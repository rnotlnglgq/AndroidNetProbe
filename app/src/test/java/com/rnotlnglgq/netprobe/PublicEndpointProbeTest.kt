package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.probe.*
import org.junit.Assert.*
import org.junit.Test
import java.net.*
import javax.net.ssl.SSLHandshakeException

class PublicEndpointProbeTest {
    private val config = EndpointConfig("https://v4.example.test/probe", "IPv4")
    private val reply = """{"observedIp":"192.0.2.12","ipFamily":"IPv4","serverTimeUtc":"2026-11-10T00:00:00Z","requestId":"test-1"}"""
    private fun probe(block: () -> HttpExchange) = PublicEndpointProbe(object : EndpointTransport {
        override fun exchange(config: EndpointConfig) = block()
    })
    private fun exchange(body: String = reply, peerFamily: String = "IPv4", status: Int = 200) =
        HttpExchange(status, body, "192.0.2.1", peerFamily, listOf(peerFamily))

    @Test fun invalidConfigurationNeverReachesTransport() {
        val never = probe { fail("Must not send request"); exchange() }
        listOf("http://v4.example.test", "https://192.0.2.1/probe", "https://user:secret@v4.example.test/",
            "https://v4.example.test/?token=private", "https://v4.example.test/#fragment", "").forEach {
            assertEquals(it, "configuration_error", never.check(config.copy(url = it)).state)
        }
    }
    @Test fun successRequiresPeerAndServerFamilyAgreement() {
        assertEquals("success", probe { exchange() }.check(config).state)
        assertEquals("configuration_error", probe { exchange(peerFamily = "IPv6") }.check(config).state)
        assertEquals("configuration_error", probe { exchange(reply.replace("192.0.2.12", "2001:db8::1")) }.check(config).state)
        val v6 = config.copy(url = "https://v6.example.test/probe", family = "IPv6")
        assertEquals("success", probe { exchange(reply.replace("192.0.2.12", "2001:db8::12").replace("IPv4", "IPv6"), "IPv6") }.check(v6).state)
    }
    @Test fun errorsAreClassifiedAndMessagesAreNotExported() {
        val cases = listOf(UnknownHostException("private") to "dns_error", ConnectException("private") to "connection_error",
            SSLHandshakeException("private") to "tls_error", SocketTimeoutException("private") to "timeout",
            ConfigurationException() to "configuration_error", NetworkUnavailableException() to "network_unavailable")
        cases.forEach { (failure, expected) ->
            val result = probe { throw failure }.check(config)
            assertEquals(expected, result.state)
            assertFalse(result.toString().contains("private"))
        }
        assertEquals("http_error", probe { exchange(status = 503) }.check(config).state)
        assertEquals("invalid_response", probe { exchange("{}") }.check(config).state)
        assertEquals("invalid_response", probe { exchange("not json") }.check(config).state)
    }
    @Test fun serverAddressIsParsedAsNumericDataNeverResolvedAsHostname() {
        assertNull(EndpointPolicy.numericFamily("attacker.example.test"))
        assertNull(EndpointPolicy.numericFamily("999.2.3.4"))
        assertNull(EndpointPolicy.numericFamily("01.2.3.4"))
        assertEquals("IPv6", EndpointPolicy.numericFamily("2001:db8::1"))
    }
}
