package com.rnotlnglgq.netprobe.probe

import androidx.annotation.Keep
import com.rnotlnglgq.netprobe.model.*
import java.net.InetAddress
import java.net.Inet6Address
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CancellationException

@Keep
data class NativeIfEntry(val nameBytes: ByteArray, val interfaceFlags: Long, val family: Int,
    val addressBytes: ByteArray, val netmaskBytes: ByteArray?, val scopeId: Long)
@Keep
data class NativeIfResult(val errno: Int, val entries: Array<NativeIfEntry>?)

@Keep
object BionicBridge {
    private var loaded = false
    @Synchronized fun read(): NativeIfResult {
        if (!loaded) { System.loadLibrary("netprobe_bionic"); loaded = true }
        return captureNative()
    }
    private external fun captureNative(): NativeIfResult
}

data class BionicAddress(val address: String, val family: String, val prefixLength: Int?,
    val netmask: String?, val scopeId: Long, val classification: String) {
    fun display() = AddressRecord(address, family, prefixLength ?: -1, classification,
        flagsSource = "getifaddrs: no address flags", flagsStatus = FieldStatus.unsupported_api)
}
data class BionicInterface(val name: String, val flags: Long, val addresses: List<BionicAddress>) {
    val up: Boolean get() = flags and 0x1L != 0L
    val loopback: Boolean get() = flags and 0x8L != 0L
}
data class BionicDocument(val capturedAtUtc: String?, val completedAtUtc: String?,
    val interfaces: List<BionicInterface>, val fields: List<ProbeField<*>>) {
    fun attach(snapshot: NetworkSnapshot) = snapshot.copy(fieldResults =
        snapshot.fieldResults.filterNot { it.path.startsWith("nativeInterfaces.") || it.path.startsWith("nativeInterfaces[") } + fields)
}

class BionicInterfaceProbe(private val read: () -> NativeIfResult = BionicBridge::read) {
    fun capture(): BionicDocument {
        val start = Instant.now().toString()
        return try { fromResult(read(), start) }
        catch (e: LinkageError) { failed(start, FieldStatus.unsupported_feature, e.javaClass.simpleName, "Native 库不可用") }
        catch (e: SecurityException) { failed(start, FieldStatus.permission_denied, e.javaClass.simpleName, "系统拒绝读取") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { failed(start, FieldStatus.error, e.javaClass.simpleName, "Native 读取失败") }
    }
    companion object {
        private const val SOURCE = "Bionic getifaddrs / JNI"
        fun notRequested() = BionicDocument(null, null, emptyList(), listOf(unavailable("nativeInterfaces.count", SOURCE, FieldStatus.not_requested)))
        private fun failed(start: String, status: FieldStatus, error: String, note: String): BionicDocument {
            val done = Instant.now().toString()
            return BionicDocument(start, done, emptyList(), listOf(ProbeField<Int>("nativeInterfaces.count", status=status,
                source=SOURCE, note=note, errorType=error), readField("nativeInterfaces.capturedAtUtc",SOURCE) { start },
                readField("nativeInterfaces.completedAtUtc",SOURCE) { done }))
        }
        fun prefixLength(mask: ByteArray?): Int? {
            if (mask == null || mask.size !in listOf(4,16)) return null
            var prefix = 0
            var sawZero = false
            for (byte in mask) for (bit in 7 downTo 0) {
                if ((byte.toInt() and (1 shl bit)) == 0) sawZero = true
                else { if (sawZero) return null; prefix++ }
            }
            return prefix
        }
        fun fromResult(result: NativeIfResult, start: String): BionicDocument {
            if (result.errno != 0) return failed(start, if (result.errno == 1 || result.errno == 13) FieldStatus.permission_denied else FieldStatus.error,
                "errno_${result.errno}", "getifaddrs 返回 errno ${result.errno}")
            val entries = requireNotNull(result.entries) { "Missing native array" }
            require(entries.size <= 512)
            val grouped = entries.groupBy { entry ->
                require(entry.nameBytes.isNotEmpty() && entry.nameBytes.size <= 16)
                Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(entry.nameBytes)).toString()
            }
            val interfaces = grouped.toSortedMap().map { (name, rows) ->
                val addresses = rows.mapNotNull { row ->
                    if (row.family == 0) return@mapNotNull null
                    require(row.family == 4 || row.family == 6)
                    require(row.addressBytes.size == if (row.family == 4) 4 else 16)
                    require(row.netmaskBytes == null || row.netmaskBytes.size == row.addressBytes.size)
                    require(row.scopeId in 0..0xffffffffL)
                    val ip = if (row.family == 6) Inet6Address.getByAddress(null, row.addressBytes, -1) else InetAddress.getByAddress(row.addressBytes)
                    val address = ip.hostAddress.orEmpty() + if (row.family == 6 && row.scopeId != 0L) "%${row.scopeId}" else ""
                    BionicAddress(address, "IPv${row.family}", prefixLength(row.netmaskBytes),
                        row.netmaskBytes?.let { InetAddress.getByAddress(it).hostAddress }, row.scopeId, IpClassifier.classify(ip))
                }.distinct()
                BionicInterface(name, rows.first().interfaceFlags, addresses)
            }
            val done = Instant.now().toString()
            val fields = mutableListOf<ProbeField<*>>()
            fields += readField("nativeInterfaces.count", SOURCE) { interfaces.size }
            fields += readField("nativeInterfaces.capturedAtUtc", SOURCE) { start }
            fields += readField("nativeInterfaces.completedAtUtc", SOURCE) { done }
            interfaces.forEachIndexed { i, face ->
                val path = "nativeInterfaces[$i]"
                fields += readField("$path.name", "getifaddrs.ifa_name") { face.name }
                fields += readField("$path.flags", "getifaddrs.ifa_flags", note="接口 IFF_* 标志，不是地址 IFA_F_* 标志") { face.flags }
                fields += readField("$path.up", "getifaddrs.ifa_flags & IFF_UP") { face.up }
                fields += readField("$path.loopback", "getifaddrs.ifa_flags & IFF_LOOPBACK") { face.loopback }
                fields += readField("$path.addresses", "getifaddrs.ifa_addr / ifa_netmask", sensitive=true,
                    note="scopeId 是 IPv6 zone 索引；缺失或非连续掩码的 prefixLength=null。未读取 MAC、MTU 或地址 flags/lifetime。") { face.addresses }
            }
            return BionicDocument(start, done, interfaces, fields)
        }
    }
}
