package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.probe.*
import org.junit.Assert.*
import org.junit.Test

class CloudflareTraceTest {
    private val body = "ip=192.0.2.42\nts=1794312000.125\ncolo=NRT\nloc=JP\ntls=TLSv1.3\nhttp=http/1.1\nwarp=off\nuag=private-device-agent\n"
    private fun probe(exchange: HttpExchange) = PublicEndpointProbe(object : EndpointTransport {
        override fun exchange(config: EndpointConfig) = exchange
    })
    private val config = EndpointConfig(CloudflareTrace.ENDPOINT, "IPv4", "cloudflare_trace")

    @Test fun traceHasTypedObservationWithoutArbitraryResponseFields() {
        val parsed = CloudflareTrace.parse(body)
        assertEquals("192.0.2.42", parsed.observedIp)
        assertEquals("IPv4", parsed.ipFamily)
        assertEquals("NRT", parsed.colo)
        assertEquals("TLSv1.3", parsed.tls)
        assertFalse(parsed.toString().contains("private-device-agent"))
    }
    @Test fun dualStackDnsIsValidForCloudflareButPeerAndObservedFamilyMustMatch() {
        val response = HttpExchange(200, body, "192.0.2.1", "IPv4", listOf("IPv4", "IPv6"))
        val result = probe(response).check(config)
        assertEquals("success", result.state)
        assertEquals("cloudflare", result.provider)
        assertNotNull(result.cloudflare)
        assertNull(result.reply)
        assertEquals("configuration_error", probe(response.copy(peerFamily = "IPv6")).check(config).state)
        assertEquals("configuration_error", probe(response.copy(body = body.replace("192.0.2.42", "2001:db8::42"))).check(config).state)
        assertEquals("success", probe(response.copy(body = body.replace("192.0.2.42", "2001:db8::42"), peerFamily = "IPv6"))
            .check(config.copy(family = "IPv6")).state)
    }
    @Test fun cloudflareModeCannotBeRedirectedToOtherHostsOrPaths() {
        listOf("https://other.example/cdn-cgi/trace", "https://www.cloudflare.com/", "http://www.cloudflare.com/cdn-cgi/trace").forEach {
            assertFalse(EndpointPolicy.valid(config.copy(url = it)))
        }
    }
    @Test fun invalidTraceIsRejectedAndUnavailableFamilyIsExplicit() {
        listOf("<html>error</html>", "colo=NRT", "ip=attacker.example", "ip=192.0.2.1\nip=192.0.2.2", body + "colo=XXX").forEach {
            assertEquals("invalid_response", probe(HttpExchange(200, it, "192.0.2.1", "IPv4", listOf("IPv4"))).check(config).state)
        }
        val unavailable = PublicEndpointProbe(object : EndpointTransport {
            override fun exchange(config: EndpointConfig): HttpExchange = throw AddressFamilyUnavailableException()
        })
        assertEquals("address_family_unavailable", unavailable.check(config).state)
    }
}
