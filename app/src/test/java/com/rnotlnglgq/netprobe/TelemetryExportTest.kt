package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.data.JsonExport
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.*
import org.junit.Assert.*
import org.junit.Test

class TelemetryExportTest {
    @Test fun registrationAndProxyDetailsPreserveStructureAndAreRemovedByRedaction() {
        val fields = listOf(
            readField("subscriptions[0].serviceState.registrations", "NetworkRegistrationInfo", sensitive = true) {
                listOf(RegistrationView(2, 1, 13, "00101", true, false, listOf(2), null))
            }, readField("defaultNetwork.httpProxy", "ProxyInfo", sensitive = true) {
                ProxyRecord("proxy.example", 8080, listOf("local.example"), "https://pac.example/config")
            })
        val snapshot = NetworkSnapshot(capturedAtUtc="2026-11-10T00:00:00Z", completedAtUtc="2026-11-10T00:00:01Z",
            runId="test", event="test", environment=Environment(35,37,"example","example","1.2"), fieldResults=fields)
        val raw = JsonExport.render(snapshot, true)
        assertTrue(raw.contains("00101"))
        assertTrue(raw.contains("registeredPlmn"))
        val redacted = JsonExport.render(snapshot, false)
        assertFalse(redacted.contains("00101"))
        assertFalse(redacted.contains("pac.example"))
        assertTrue(redacted.contains("NetworkRegistrationInfo"))
    }
    @Test fun socketLocalAddressSurvivesRawAndNotPrivacyExport() {
        val result = EndpointResult("https://example.test", "IPv4", "success", "start", "end", localAddress="192.0.2.99")
        val fields = externalFields(listOf(result))
        assertTrue(JsonExport.gson.toJson(fields).contains("192.0.2.99"))
        assertTrue(fields.first { it.path.endsWith(".result") }.sensitive)
    }
}
