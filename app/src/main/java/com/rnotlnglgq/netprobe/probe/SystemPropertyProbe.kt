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
        PropertySpec("ro.product.manufacturer", DiagnosticCatalog.legacy("manufacturer"), "system", false),
        PropertySpec("ro.product.brand", DiagnosticCatalog.legacy("brand"), "system", false),
        PropertySpec("ro.product.model", DiagnosticCatalog.legacy("model"), "system", false),
        PropertySpec("ro.product.device", DiagnosticCatalog.legacy("device_codename"), "system", false),
        PropertySpec("ro.product.cpu.abilist", "CPU ABI", "system", false),
        PropertySpec("ro.build.version.release", DiagnosticCatalog.legacy("android_version"), "system", false),
        PropertySpec("ro.build.version.sdk", "Android API", "system", false),
        PropertySpec("ro.build.version.security_patch", DiagnosticCatalog.legacy("security_patch"), "system", false),
        PropertySpec("ro.build.version.incremental", DiagnosticCatalog.legacy("system_build_version"), "system", false),
        PropertySpec("ro.build.type", DiagnosticCatalog.legacy("build_type"), "system", false),
        PropertySpec("ro.build.tags", DiagnosticCatalog.legacy("build_tags"), "system", false),
        PropertySpec("gsm.operator.alpha", DiagnosticCatalog.legacy("registered_carrier_name"), "carrier", true),
        PropertySpec("gsm.operator.numeric", DiagnosticCatalog.legacy("registered_mcc_mnc"), "carrier", true),
        PropertySpec("gsm.operator.iso-country", DiagnosticCatalog.legacy("network_country"), "carrier", true),
        PropertySpec("gsm.operator.isroaming", DiagnosticCatalog.legacy("roaming_property"), "carrier", true),
        PropertySpec("gsm.sim.operator.alpha", DiagnosticCatalog.legacy("sim_carrier_name"), "carrier", true),
        PropertySpec("gsm.sim.operator.numeric", "SIM MCC/MNC", "carrier", true),
        PropertySpec("gsm.sim.operator.iso-country", DiagnosticCatalog.legacy("sim_country"), "carrier", true),
        PropertySpec("gsm.sim.state", DiagnosticCatalog.legacy("sim_state_property"), "carrier", true),
        PropertySpec("gsm.network.type", DiagnosticCatalog.legacy("network_technology_property"), "carrier", true),
        PropertySpec("gsm.version.baseband", DiagnosticCatalog.legacy("baseband_version"), "carrier", true),
        PropertySpec("persist.radio.multisim.config", DiagnosticCatalog.legacy("multi_sim_configuration"), "carrier", true),
        PropertySpec("ro.telephony.default_network", DiagnosticCatalog.legacy("default_network_technology_configuration"), "carrier", true),
        PropertySpec("wifi.interface", DiagnosticCatalog.legacy("wi_fi_interface_property"), "network", true),
        PropertySpec("net.dns1", DiagnosticCatalog.legacy("dns_property_1"), "network", true),
        PropertySpec("net.dns2", DiagnosticCatalog.legacy("dns_property_2"), "network", true),
        PropertySpec("net.dns3", DiagnosticCatalog.legacy("dns_property_3"), "network", true),
        PropertySpec("net.dns4", DiagnosticCatalog.legacy("dns_property_4"), "network", true),
        PropertySpec("net.tcp.default_init_rwnd", DiagnosticCatalog.legacy("tcp_initial_receive_window_configuration"), "network", true)
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
        unavailable(path, "SystemPropertyProbe", FieldStatus.not_requested, note = DiagnosticCatalog.legacy("system_properties_await_a_read"), sensitive = false)
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
            if (remaining <= 0) field(spec, status = FieldStatus.error, note = DiagnosticCatalog.legacy("this_read_timed_out"), errorType = "CollectionTimeout")
            else try { convert(spec, runner.query(spec.key, minOf(750L, remaining))) }
            catch (e: CancellationException) { throw e }
            catch (e: SecurityException) { field(spec, status = FieldStatus.permission_denied, note = DiagnosticCatalog.legacy("system_denied_getprop_execution"), errorType = e.javaClass.simpleName) }
            catch (e: Exception) { field(spec, status = FieldStatus.error, note = DiagnosticCatalog.legacy("getprop_start_or_read_failed"), errorType = e.javaClass.simpleName) }
        }
        if (cancelled.get()) throw CancellationException("Property read cancelled")
        return PropertyDocument(start, Instant.now().toString(), fields)
    }
    companion object {
        fun notRequested() = PropertyDocument(null, null, PropertyCatalog.entries.map {
            field(it, status = FieldStatus.not_requested, note = DiagnosticCatalog.legacy("use_the_read_button_in_system_properties"))
        })
        private fun field(spec: PropertySpec, value: String? = null, status: FieldStatus,
            note: String? = null, errorType: String? = null) = ProbeField(PropertyCatalog.path(spec), value, status,
            "/system/bin/getprop ${spec.key} (ProcessBuilder)", "none", note, errorType, spec.sensitive)
        fun convert(spec: PropertySpec, result: PropertyCommandResult): ProbeField<String> = when {
            result.permissionDenied -> field(spec, status = FieldStatus.permission_denied, note = DiagnosticCatalog.legacy("system_explicitly_denied_reading"), errorType = "PropertyAccessDenied")
            result.timedOut -> field(spec, status = FieldStatus.error, note = DiagnosticCatalog.legacy("read_timed_out"), errorType = "PropertyTimeout")
            result.outputTooLarge -> field(spec, status = FieldStatus.error, note = DiagnosticCatalog.legacy("output_exceeded_the_size_limit"), errorType = "PropertyOutputLimit")
            result.exitCode != 0 || result.hasDiagnostics -> field(spec, status = FieldStatus.error,
                note = DiagnosticCatalog.legacy("getprop_failed_exit_code_1_s" ,result.exitCode), errorType = "GetpropCommandError")
            result.value.isNullOrEmpty() -> field(spec, status = FieldStatus.empty,
                note = DiagnosticCatalog.legacy("empty_unset_and_system_hidden_values_share_this_result"))
            else -> field(spec, result.value, FieldStatus.available,
                note = DiagnosticCatalog.legacy("read_under_app_process_permissions_properties_may_be_stale_or_con"))
        }
    }
}
