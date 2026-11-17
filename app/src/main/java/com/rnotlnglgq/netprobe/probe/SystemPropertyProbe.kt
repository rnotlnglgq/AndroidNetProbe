package com.rnotlnglgq.netprobe.probe

import com.rnotlnglgq.netprobe.model.*
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Explicit queries only: never enumerate the full property area or invoke a shell. */
data class PropertySpec(val key: String, val title: String, val group: String, val sensitive: Boolean)

object PropertyCatalog {
    val entries = listOf(
        PropertySpec("ro.product.manufacturer", "厂商", "system", false),
        PropertySpec("ro.product.brand", "品牌", "system", false),
        PropertySpec("ro.product.model", "型号", "system", false),
        PropertySpec("ro.product.device", "设备代号", "system", false),
        PropertySpec("ro.product.cpu.abilist", "CPU ABI", "system", false),
        PropertySpec("ro.build.version.release", "Android 版本", "system", false),
        PropertySpec("ro.build.version.sdk", "Android API", "system", false),
        PropertySpec("ro.build.version.security_patch", "安全补丁", "system", false),
        PropertySpec("ro.build.version.incremental", "系统构建版本", "system", false),
        PropertySpec("ro.build.type", "构建类型", "system", false),
        PropertySpec("ro.build.tags", "构建标签", "system", false),
        PropertySpec("gsm.operator.alpha", "注册运营商名称", "carrier", true),
        PropertySpec("gsm.operator.numeric", "注册 MCC/MNC", "carrier", true),
        PropertySpec("gsm.operator.iso-country", "网络国家", "carrier", true),
        PropertySpec("gsm.operator.isroaming", "漫游属性", "carrier", true),
        PropertySpec("gsm.sim.operator.alpha", "SIM 运营商名称", "carrier", true),
        PropertySpec("gsm.sim.operator.numeric", "SIM MCC/MNC", "carrier", true),
        PropertySpec("gsm.sim.operator.iso-country", "SIM 国家", "carrier", true),
        PropertySpec("gsm.sim.state", "SIM 状态属性", "carrier", true),
        PropertySpec("gsm.network.type", "网络制式属性", "carrier", true),
        PropertySpec("gsm.version.baseband", "基带版本", "carrier", true),
        PropertySpec("persist.radio.multisim.config", "多 SIM 配置", "carrier", true),
        PropertySpec("ro.telephony.default_network", "默认网络制式配置", "carrier", true),
        PropertySpec("wifi.interface", "Wi-Fi 接口属性", "network", true),
        PropertySpec("net.dns1", "DNS 属性 1", "network", true),
        PropertySpec("net.dns2", "DNS 属性 2", "network", true),
        PropertySpec("net.dns3", "DNS 属性 3", "network", true),
        PropertySpec("net.dns4", "DNS 属性 4", "network", true),
        PropertySpec("net.tcp.default_init_rwnd", "TCP 初始接收窗口配置", "network", true)
    )
    fun path(spec: PropertySpec) = "systemProperties.values.${spec.key}"
    fun requireKnown(key: String) { require(entries.any { it.key == key }) { "Unknown property key" } }
}

data class PropertyCommandResult(
    val value: String? = null, val exitCode: Int = 0, val timedOut: Boolean = false,
    val outputTooLarge: Boolean = false, val permissionDenied: Boolean = false, val hasDiagnostics: Boolean = false
)
interface PropertyCommandRunner {
    fun query(key: String, timeoutMillis: Long): PropertyCommandResult
    fun cancel() {}
}

class GetpropCommandRunner : PropertyCommandRunner {
    private val cancelled = AtomicBoolean(false)
    private val active = AtomicReference<Process?>()
    override fun cancel() { cancelled.set(true); active.get()?.destroyForcibly() }
    private fun ensureActive() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException("Property read cancelled")
    }
    override fun query(key: String, timeoutMillis: Long): PropertyCommandResult {
        PropertyCatalog.requireKnown(key)
        ensureActive()
        val process = ProcessBuilder("/system/bin/getprop", key).start()
        active.set(process)
        try {
            ensureActive()
            // The fixed getprop command never forks or accepts commands from the property value.
            // Waiting is bounded even if an unexpected output fills the pipe buffer.
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) return PropertyCommandResult(timedOut = true)
            ensureActive()
            val stdout = process.inputStream.readNBytesCompat(LIMIT + 1)
            val stderr = process.errorStream.readNBytesCompat(LIMIT + 1)
            if (stdout.size > LIMIT || stderr.size > LIMIT) return PropertyCommandResult(outputTooLarge = true)
            val diagnostics = String(stderr, Charsets.UTF_8).lowercase(Locale.ROOT)
            return PropertyCommandResult(value = String(stdout, Charsets.UTF_8).removeSuffix("\n").removeSuffix("\r"),
                exitCode = process.exitValue(), permissionDenied = "permission denied" in diagnostics || "access denied" in diagnostics,
                hasDiagnostics = stderr.isNotEmpty())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("Property read interrupted")
        } finally {
            active.compareAndSet(process, null)
            process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var offset = 0
        while (offset < limit) {
            val count = read(buffer, offset, limit - offset)
            if (count < 0) break
            if (count == 0) continue
            offset += count
        }
        return buffer.copyOf(offset)
    }
    private companion object { const val LIMIT = 4096 }
}

data class PropertyDocument(val capturedAtUtc: String?, val completedAtUtc: String?, val fields: List<ProbeField<*>>) {
    fun attach(snapshot: NetworkSnapshot): NetworkSnapshot = snapshot.copy(fieldResults =
        snapshot.fieldResults.filterNot { it.path.startsWith("systemProperties.") } + fields +
            listOf(timestamp("systemProperties.capturedAtUtc", capturedAtUtc), timestamp("systemProperties.completedAtUtc", completedAtUtc)))
    private fun timestamp(path: String, value: String?): ProbeField<*> = if (value == null)
        unavailable(path, "SystemPropertyProbe", FieldStatus.not_requested, note = "尚未读取系统属性", sensitive = false)
        else readField(path, "SystemPropertyProbe", sensitive = false) { value }
}

class SystemPropertyProbe(
    private val runner: PropertyCommandRunner = GetpropCommandRunner(),
    private val nanoTime: () -> Long = System::nanoTime
) {
    private val cancelled = AtomicBoolean(false)
    fun cancel() { cancelled.set(true); runner.cancel() }
    fun collect(): PropertyDocument {
        val start = Instant.now().toString()
        val deadline = nanoTime() + TimeUnit.SECONDS.toNanos(8)
        val fields = PropertyCatalog.entries.map { spec ->
            if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException("Property read cancelled")
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - nanoTime())
            if (remaining <= 0) field(spec, status = FieldStatus.error, note = "本次读取已超时", errorType = "CollectionTimeout")
            else try { convert(spec, runner.query(spec.key, minOf(750L, remaining))) }
            catch (e: CancellationException) { throw e }
            catch (e: SecurityException) { field(spec, status = FieldStatus.permission_denied, note = "系统拒绝执行 getprop", errorType = e.javaClass.simpleName) }
            catch (e: Exception) { field(spec, status = FieldStatus.error, note = "getprop 启动或读取失败", errorType = e.javaClass.simpleName) }
        }
        if (cancelled.get()) throw CancellationException("Property read cancelled")
        return PropertyDocument(start, Instant.now().toString(), fields)
    }
    companion object {
        fun notRequested() = PropertyDocument(null, null, PropertyCatalog.entries.map {
            field(it, status = FieldStatus.not_requested, note = "点击系统属性页的读取按钮")
        })
        private fun field(spec: PropertySpec, value: String? = null, status: FieldStatus,
            note: String? = null, errorType: String? = null) = ProbeField(PropertyCatalog.path(spec), value, status,
            "/system/bin/getprop ${spec.key} (ProcessBuilder)", "none", note, errorType, spec.sensitive)
        fun convert(spec: PropertySpec, result: PropertyCommandResult): ProbeField<String> = when {
            result.permissionDenied -> field(spec, status = FieldStatus.permission_denied, note = "系统明确拒绝读取", errorType = "PropertyAccessDenied")
            result.timedOut -> field(spec, status = FieldStatus.error, note = "读取超时", errorType = "PropertyTimeout")
            result.outputTooLarge -> field(spec, status = FieldStatus.error, note = "输出超过大小限制", errorType = "PropertyOutputLimit")
            result.exitCode != 0 || result.hasDiagnostics -> field(spec, status = FieldStatus.error,
                note = "getprop 返回异常（退出码 ${result.exitCode}）", errorType = "GetpropCommandError")
            result.value.isNullOrEmpty() -> field(spec, status = FieldStatus.empty,
                note = "空值、未设置和被系统隐藏无法仅凭此结果区分；不代表属性不存在")
            else -> field(spec, result.value, FieldStatus.available,
                note = "应用进程权限下读取；属性并非稳定 SDK 契约，可能过时或含逗号列表，不自动对应订阅/当前网络")
        }
    }
}
