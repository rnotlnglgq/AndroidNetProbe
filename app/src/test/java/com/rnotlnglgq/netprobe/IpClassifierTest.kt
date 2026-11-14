package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.probe.IpClassifier
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class IpClassifierTest {
    @Test fun ipv4PrivateSharedAndLocalRanges() {
        val cases = mapOf("10.0.0.1" to "private", "172.16.0.1" to "private", "192.168.1.1" to "private",
            "100.64.0.1" to "shared-cgnat", "100.127.255.254" to "shared-cgnat", "100.128.0.1" to "other-ipv4",
            "169.254.1.1" to "link-local", "127.0.0.1" to "loopback", "0.0.0.0" to "unspecified", "224.0.0.1" to "multicast",
            "192.0.2.1" to "other-ipv4")
        cases.forEach { (ip, expected) -> assertEquals(ip, expected, IpClassifier.classify(InetAddress.getByName(ip))) }
    }
    @Test fun ipv6BoundaryPrefixes() {
        val cases = mapOf("fe80::1" to "link-local", "febf::1" to "link-local",
            "fec0::1" to "other", "fc00::1" to "unique-local", "fdff::1" to "unique-local",
            "fbff::1" to "other", "2001:db8::1" to "global-unicast", "3fff::1" to "global-unicast",
            "4000::1" to "other", "::1" to "other", "::" to "other", "ff02::1" to "other")
        cases.forEach { (ip, expected) -> assertEquals(ip, expected, IpClassifier.classify(InetAddress.getByName(ip))) }
    }
}
