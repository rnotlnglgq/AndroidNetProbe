package com.rnotlnglgq.netprobe

import com.google.gson.JsonParser
import com.rnotlnglgq.netprobe.data.JsonExport
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.*
import org.junit.Assert.*
import org.junit.Test

class SubscriptionHistoryTest {
    private fun record(prefix: String = "subscriptionHistory[0]") = SubscriptionRecordFields.collect(prefix,"session-alias",35) { key -> when(key) {
        "displayName" -> "工作卡-私有标签"; "carrierName" -> "Test carrier"; "mcc" -> "001"; "mnc" -> "01"
        "slotIndex" -> -1; "embedded" -> true; else -> null
    } }
    private fun base() = NetworkSnapshot(capturedAtUtc="2026-11-14T00:00:00Z",completedAtUtc="2026-11-14T00:00:01Z",
        runId="test",event="test",environment=Environment(35,37,"test","test","1.6"),fieldResults=emptyList())

    @Test fun historicalReadRequiresPhoneButNotLocationAndGatesNeverInvokeReader() {
        listOf(Triple(33,true,true),Triple(35,false,true),Triple(35,true,false)).forEach { (api,enabled,granted) ->
            var called=false
            val doc=SubscriptionHistoryDocument.collect(api,enabled,true,granted) { called=true; listOf(record()) }
            assertFalse(called)
            assertTrue(doc.fields.first().status in listOf(FieldStatus.unsupported_api,FieldStatus.not_requested,FieldStatus.permission_denied))
        }
        val result=SubscriptionHistoryDocument.collect(35,true,true,true) { listOf(record()) }
        assertEquals(1,result.fields.first().value)
        assertEquals("READ_PHONE_STATE",result.fields.first().permission)
        assertTrue(result.fields.none { "LOCATION" in it.permission || it.acquisitionPermissions.any { p -> "LOCATION" in p } })
    }
    @Test fun metadataGettersKeepTheirOwnPermissionSeparateFromEnumeration() {
        val fields=record()
        assertTrue(fields.all { it.permission=="none" && it.acquisitionPermissions==listOf("READ_PHONE_STATE") })
        assertEquals("无有效卡槽",com.rnotlnglgq.netprobe.ui.Presentation.fieldText(fields.first { it.path.endsWith(".slotIndex") }))
        assertEquals("01",fields.first { it.path.endsWith(".mnc") }.value)
        assertEquals("工作卡-私有标签",fields.first { it.path.endsWith(".displayName") }.value)
        assertTrue(SubscriptionRecordFields.specs.none { it.key in listOf("iccid","number","imsi","groupUuid","cardId") })
        assertEquals("none",PhoneAccess.serviceField("voiceState"))
        assertTrue(PhoneAccess.serviceField("operatorNumeric").contains("LOCATION"))
        assertEquals("READ_PHONE_STATE",PhoneAccess.dataNetworkType(32))
        assertEquals("READ_PHONE_STATE or READ_BASIC_PHONE_STATE",PhoneAccess.dataNetworkType(33))
    }
    @Test fun oldVersionsDoNotCallNewGettersAndFieldFailureIsIsolated() {
        val fields=SubscriptionRecordFields.collect("subscriptions[0]","alias",26) { key ->
            assertFalse(key in listOf("mcc","mnc","embedded","carrierId","portIndex","usageSetting","onlyNonTerrestrial"))
            if(key=="displayName") throw SecurityException("private label")
            "test"
        }
        assertEquals(FieldStatus.permission_denied,fields.first { it.path.endsWith(".displayName") }.status)
        assertEquals(FieldStatus.available,fields.first { it.path.endsWith(".carrierName") }.status)
        assertEquals(FieldStatus.unsupported_api,fields.first { it.path.endsWith(".portIndex") }.status)
        assertFalse(fields.toString().contains("private label"))
    }
    @Test fun emptySuccessfulHistoryAndFailedReadAreDistinct() {
        val empty=SubscriptionHistoryDocument.collect(35,true,true,true) { emptyList() }
        assertEquals(FieldStatus.available,empty.fields.first().status)
        assertEquals(0,empty.fields.first().value)
        val failed=SubscriptionHistoryDocument.collect(35,true,true,true) { throw SecurityException("private") }
        assertEquals(FieldStatus.permission_denied,failed.fields.first().status)
        assertNull(failed.fields.first().value)
    }
    @Test fun historyCachesReplaceFieldsAndRedactionPreservesPermissionProvenance() {
        val doc=SubscriptionHistoryDocument.collect(35,true,true,true) { listOf(record()) }
        val snapshot=doc.attach(doc.attach(base()))
        assertEquals(snapshot.fieldResults.size,snapshot.fieldResults.map { it.path }.distinct().size)
        assertEquals(base().capturedAtUtc,snapshot.capturedAtUtc)
        assertTrue(JsonExport.render(snapshot,true).contains("工作卡-私有标签"))
        val redacted=JsonExport.render(snapshot,false)
        assertFalse(redacted.contains("工作卡-私有标签"))
        val fields=JsonParser.parseString(redacted).asJsonObject["snapshot"].asJsonObject["fieldResults"].asJsonArray
        val label=fields.first { it.asJsonObject["path"].asString.endsWith(".displayName") }.asJsonObject
        assertEquals("none",label["permission"].asString)
        assertEquals("READ_PHONE_STATE",label["acquisitionPermissions"].asJsonArray[0].asString)
    }
    @Test fun subscriptionAliasesAreSessionLocalAndSharedWithoutExportingPlatformIds() {
        val ids=SessionSubscriptionIds()
        val alias=ids.alias(12345)
        assertEquals(alias,ids.alias(12345))
        assertNotEquals(alias,SessionSubscriptionIds().alias(12345))
        assertNotEquals("12345",alias)
        assertNotNull(java.util.UUID.fromString(alias.removePrefix("sub-")))
        assertEquals(1,readField("test","test") { false }.withAcquisition("READ_PHONE_STATE").withAcquisition("READ_PHONE_STATE").acquisitionPermissions.size)
    }
}
