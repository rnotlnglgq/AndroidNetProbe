package com.rnotlnglgq.netprobe

import com.google.gson.JsonParser
import com.rnotlnglgq.netprobe.data.*
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class ExportContractTest {
    @Test fun actualExportsGenerateReproducibleSyntheticFixturesAndKeepProtocolStableAcrossLocales() {
        val time="2026-11-10T00:00:00Z"
        val clock=Clock.fixed(Instant.parse(time),ZoneOffset.UTC)
        val fields=listOf(
            readField(FieldKeys.DEFAULT_INTERFACE,"LinkProperties.getInterfaceName",sensitive=false) { "wlan0" },
            readField(FieldKeys.DEFAULT_ADDRESSES,"LinkProperties.getLinkAddresses",sensitive=true) {
                listOf(AddressRecord("2001:db8::42","IPv6",64,"global-unicast",flagsSource="link_address_flags",flagsStatus=FieldStatus.available),
                    AddressRecord("192.0.2.42","IPv4",24,"other-ipv4",flagsSource="link_address_flags",flagsStatus=FieldStatus.available)) },
            readField("subscriptions[0].displayName","SubscriptionInfo.getDisplayName",sensitive=true) { "Synthetic SIM" }.withAcquisition("READ_PHONE_STATE"),
            readField("subscriptions[0].serviceState.registrations","NetworkRegistrationInfo",sensitive=true) {
                listOf(RegistrationView(2,1,13,"00101",true,false,listOf(2),null)) }.withAcquisition("READ_PHONE_STATE","ACCESS_COARSE_LOCATION"),
            unavailable("wifi.ssid","WifiInfo.getSSID",FieldStatus.permission_denied,"ACCESS_FINE_LOCATION + ACCESS_WIFI_STATE",sensitive=true))
        val store=SnapshotStore()
        store.replaceBase(NetworkSnapshot(capturedAtUtc=time,completedAtUtc=time,runId="synthetic-example",event="documentation_example",
            environment=Environment(35,37,"example","example","0.1.0"),fieldResults=fields))
        store.replaceSection(FieldSection.PROPERTIES,listOf(readField("systemProperties.values.gsm.operator.numeric","getprop gsm.operator.numeric",sensitive=true) { "00101" }))
        store.replaceSection(FieldSection.NATIVE,BionicInterfaceProbe.fromResult(NativeIfResult(0,arrayOf(
            NativeIfEntry("wlan0".toByteArray(),1,4,byteArrayOf(192.toByte(),0,2,42),byteArrayOf(-1,-1,-1,0),0))),time).fields)
        store.replaceSection(FieldSection.HISTORY,SubscriptionHistoryDocument.collect(35,true,true,true) {
            listOf(SubscriptionRecordFields.collect("subscriptionHistory[0]","sub-example",35) { if (it=="displayName") "Synthetic history SIM" else null })
        }.fields)
        // Freeze module timestamps as well as the outer document.
        val snapshot=store.snapshot.value!!.let { s -> s.copy(fieldResults=s.fieldResults.map {
            if (it.path.endsWith(".capturedAtUtc") || it.path.endsWith(".completedAtUtc")) ProbeField(it.path,time,it.status,it.source,it.permission,it.note,it.errorType,it.sensitive,it.observedStatus,it.acquisitionPermissions) else it
        }) }
        val comparison=PermissionComparison.between("phone",snapshot,snapshot)
        val events=listOf(NetworkEvent(time,"documentation_example"))
        val raw=JsonExport.render(snapshot,true,events,comparison,clock)
        val hidden=JsonExport.render(snapshot,false,events,comparison,clock)
        assertTrue(raw.contains("2001:db8::42"));assertFalse(hidden.contains("2001:db8::42"))
        assertFalse(hidden.contains("192.0.2.42"));assertFalse(hidden.contains("Synthetic history SIM"))
        val default=Locale.getDefault()
        try { Locale.setDefault(Locale.SIMPLIFIED_CHINESE);assertEquals(raw,JsonExport.render(snapshot,true,events,comparison,clock)) }
        finally { Locale.setDefault(default) }
        assertEquals(setOf("schemaVersion","exportMode","exportedAtUtc","snapshot","eventHistory","permissionComparison"),JsonParser.parseString(raw).asJsonObject.keySet())
        val output=File("build/contract-exports").apply { mkdirs() }
        File(output,"example-snapshot.json").writeText(raw+"\n")
        File(output,"example-redacted.json").writeText(hidden+"\n")
    }
}
