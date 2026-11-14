package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.data.JsonExport
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.*
import org.junit.Assert.*
import org.junit.Test

class BionicInterfaceProbeTest {
    private fun row(name: String, ip: ByteArray, mask: ByteArray?, family: Int = 4, scope: Long = 0) =
        NativeIfEntry(name.toByteArray(),1,family,ip,mask,scope)
    private val mask4 = byteArrayOf(-1,-1,-1,0)
    private fun valid() = NativeIfResult(0,arrayOf(
        row("wlan0", byteArrayOf(-64,0,2,9),mask4),
        row("wlan0", byteArrayOf(0xfe.toByte(),0x80.toByte()) + ByteArray(13) + byteArrayOf(1),ByteArray(8){-1}+ByteArray(8),6,7),
        NativeIfEntry("tun0".toByteArray(),9,0,byteArrayOf(),null,0)))

    @Test fun convertsFamiliesScopePrefixAndInterfaceFlagsWithoutInventingAddressFlags() {
        val doc=BionicInterfaceProbe { valid() }.capture()
        assertEquals(listOf("tun0","wlan0"),doc.interfaces.map { it.name })
        assertTrue(doc.interfaces.first().loopback)
        val face=doc.interfaces.last()
        assertTrue(face.up)
        assertEquals(2,face.addresses.size)
        assertEquals(24,face.addresses[0].prefixLength)
        assertEquals(64,face.addresses[1].prefixLength)
        assertTrue(face.addresses[1].address.endsWith("%7"))
        assertEquals("link-local",face.addresses[1].classification)
        assertNull(face.addresses[1].display().temporary)
        assertNull(face.addresses[1].display().flags)
        assertEquals(FieldStatus.unsupported_api,face.addresses[1].display().flagsStatus)
    }
    @Test fun prefixValidationKeepsUnknownDistinctFromZeroAndFullLength() {
        assertNull(BionicInterfaceProbe.prefixLength(null))
        assertNull(BionicInterfaceProbe.prefixLength(byteArrayOf(-1,0,-1,0)))
        assertEquals(0,BionicInterfaceProbe.prefixLength(ByteArray(4)))
        assertEquals(32,BionicInterfaceProbe.prefixLength(ByteArray(4){-1}))
        assertEquals(128,BionicInterfaceProbe.prefixLength(ByteArray(16){-1}))
        val doc=BionicInterfaceProbe { NativeIfResult(0,arrayOf(row("wlan0",byteArrayOf(-64,0,2,9),null))) }.capture()
        assertNull(doc.interfaces.single().addresses.single().prefixLength)
        assertEquals(-1,doc.interfaces.single().addresses.single().display().prefixLength)
    }
    @Test fun errnoLibraryAndMalformedPayloadFailuresDoNotCrashOrReturnFakeEmptySuccess() {
        assertEquals(FieldStatus.not_requested,BionicInterfaceProbe.notRequested().fields.single().status)
        assertEquals(FieldStatus.permission_denied,BionicInterfaceProbe { NativeIfResult(13,null) }.capture().fields.first().status)
        assertEquals(FieldStatus.error,BionicInterfaceProbe { NativeIfResult(105,null) }.capture().fields.first().status)
        assertEquals(FieldStatus.unsupported_feature,BionicInterfaceProbe { throw UnsatisfiedLinkError("private") }.capture().fields.first().status)
        assertEquals(FieldStatus.unsupported_feature,BionicInterfaceProbe { throw NoSuchMethodError("private") }.capture().fields.first().status)
        val failed=BionicInterfaceProbe { NativeIfResult(0,arrayOf(row("wlan0",byteArrayOf(1),mask4))) }.capture()
        assertEquals(FieldStatus.error,failed.fields.first().status)
        assertFalse(failed.fields.toString().contains("private"))
        assertEquals(0,BionicInterfaceProbe { NativeIfResult(0,emptyArray()) }.capture().fields.first().value)
    }
    @Test fun cachedNativeResultsAreExportedOnceAndAddressesAreRedacted() {
        val base=NetworkSnapshot(capturedAtUtc="2026-11-12T00:00:00Z",completedAtUtc="2026-11-12T00:00:01Z",runId="test",event="test",
            environment=Environment(35,37,"test","test","1.5"),fieldResults=emptyList())
        val doc=BionicInterfaceProbe { valid() }.capture()
        val result=doc.attach(doc.attach(base))
        assertEquals(result.fieldResults.size,result.fieldResults.map { it.path }.distinct().size)
        assertTrue(JsonExport.render(result,true).contains("192.0.2.9"))
        assertFalse(JsonExport.render(result,false).contains("192.0.2.9"))
        assertTrue(JsonExport.render(result,false).contains("getifaddrs"))
        assertEquals(base.capturedAtUtc,result.capturedAtUtc)
    }
}
