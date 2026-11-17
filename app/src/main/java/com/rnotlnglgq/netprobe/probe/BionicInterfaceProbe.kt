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
        catch (e: LinkageError) { failed(start, FieldStatus.unsupported_feature, e.javaClass.simpleName, DiagnosticCatalog.legacy("native_library_unavailable")) }
        catch (e: SecurityException) { failed(start, FieldStatus.permission_denied, e.javaClass.simpleName, DiagnosticCatalog.legacy("system_denied_reading")) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { failed(start, FieldStatus.error, e.javaClass.simpleName, DiagnosticCatalog.legacy("native_read_failed")) }
    }
    companion object {
        private const val SOURCE = "Bionic getifaddrs / JNI"
        fun notRequested() = BionicDocument(null, null, emptyList(), listOf(unavailable("nativeInterfaces.count", SOURCE, FieldStatus.not_requested, sensitive = false)))
        private fun failed(start: String, status: FieldStatus, error: String, note: String): BionicDocument {
            val done = Instant.now().toString()
            return BionicDocument(start, done, emptyList(), listOf(ProbeField<Int>("nativeInterfaces.count", status=status,
                source=SOURCE, note=note, errorType=error, sensitive = false), readField("nativeInterfaces.capturedAtUtc",SOURCE, sensitive = false) { start },
                readField("nativeInterfaces.completedAtUtc",SOURCE, sensitive = false) { done }))
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
                "errno_${result.errno}", DiagnosticCatalog.legacy("getifaddrs_returned_errno_1_s" ,result.errno))
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
            fields += readField("nativeInterfaces.count", SOURCE, sensitive = false) { interfaces.size }
            fields += readField("nativeInterfaces.capturedAtUtc", SOURCE, sensitive = false) { start }
            fields += readField("nativeInterfaces.completedAtUtc", SOURCE, sensitive = false) { done }
            interfaces.forEachIndexed { i, face ->
                val path = "nativeInterfaces[$i]"
                fields += readField("$path.name", "getifaddrs.ifa_name", sensitive = false) { face.name }
                fields += readField("$path.flags", "getifaddrs.ifa_flags", note=DiagnosticCatalog.legacy("interface_iff_flags_address_ifa_f_flags_are_separate"), sensitive = false) { face.flags }
                fields += readField("$path.up", "getifaddrs.ifa_flags & IFF_UP", sensitive = false) { face.up }
                fields += readField("$path.loopback", "getifaddrs.ifa_flags & IFF_LOOPBACK", sensitive = false) { face.loopback }
                fields += readField("$path.addresses", "getifaddrs.ifa_addr / ifa_netmask", sensitive=true,
                    note=DiagnosticCatalog.legacy("scopeid_is_the_ipv6_zone_index_missing_or_non_contiguous_masks_yi")) { face.addresses }
            }
            return BionicDocument(start, done, interfaces, fields)
        }
    }
}
