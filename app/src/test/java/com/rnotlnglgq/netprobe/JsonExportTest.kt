package com.rnotlnglgq.netprobe

import com.google.gson.JsonParser
import com.rnotlnglgq.netprobe.data.JsonExport
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.*
import org.junit.Assert.*
import org.junit.Test

class JsonExportTest {
    private val snapshot = NetworkSnapshot(capturedAtUtc = "2026-11-10T00:00:00Z",
        completedAtUtc = "2026-11-10T00:00:01Z", runId = "session", event = "manual",
        environment = Environment(35,37,"example","example","1.0"),
        fieldResults = listOf(readField("defaultNetwork.linkAddresses", "LinkProperties", sensitive = true) {
            listOf(AddressRecord("2001:db8::abc", "IPv6", 64, "global-unicast", flags = 1,
                flagsSource = "link_address_flags", flagsStatus = FieldStatus.available))
        }, readField("defaultNetwork.validated", "NetworkCapabilities", sensitive = false) { false },
            unavailable("telephony.sim", "TelephonyManager", FieldStatus.permission_denied, sensitive = true)))

    @Test fun cloudflareAndIpv4AddressesAreRemovedFromRedactedExport() {
        val result = EndpointResult(CloudflareTrace.ENDPOINT, "IPv4", "success", "2026-11-10T00:00:00Z", "2026-11-10T00:00:01Z",
            provider = "cloudflare", cloudflare = CloudflareObservation("192.0.2.42", "IPv4", null, "NRT", "JP", null, null, "off"))
        val current = snapshot.copy(fieldResults = snapshot.fieldResults + externalFields(listOf(result)) +
            readField("ipv4.defaultNetworkAddresses", "LinkProperties", sensitive = true) { listOf("192.0.2.99") })
        assertTrue(JsonExport.render(current, true).contains("192.0.2.42"))
        val redacted = JsonExport.render(current, false)
        assertFalse(redacted.contains("192.0.2.42"))
        assertFalse(redacted.contains("192.0.2.99"))
        assertTrue(redacted.contains("externalChecks.ipv4.state"))
    }

    @Test fun rawAndRedactedAreParseableAndNoNestedSensitiveValueSurvives() {
        val raw = JsonExport.render(snapshot, true)
        val comparison = PermissionComparison.between("phone", snapshot, snapshot)
        val redacted = JsonExport.render(snapshot, false, comparison = comparison)
        assertTrue(raw.contains("2001:db8::abc"))
        assertFalse(redacted.contains("2001:db8"))
        assertEquals("1.0", JsonParser.parseString(raw).asJsonObject["schemaVersion"].asString)
        val fields = JsonParser.parseString(redacted).asJsonObject["snapshot"].asJsonObject["fieldResults"].asJsonArray
        assertEquals("redacted", fields[0].asJsonObject["status"].asString)
        assertEquals("available", fields[0].asJsonObject["observedStatus"].asString)
        assertFalse(fields[1].asJsonObject["value"].asBoolean)
        assertEquals("permission_denied", fields[2].asJsonObject["status"].asString)
        assertEquals("2001:db8::abc", (snapshot.fieldResults[0].value as List<*>).let { (it[0] as AddressRecord).address })
    }
}
