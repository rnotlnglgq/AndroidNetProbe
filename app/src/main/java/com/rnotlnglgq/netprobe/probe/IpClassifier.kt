package com.rnotlnglgq.netprobe.probe

import java.net.InetAddress

object IpClassifier {
    fun classify(address: InetAddress): String {
        val b = address.address
        if (b.size == 4) return when {
            address.isLoopbackAddress -> "loopback"
            address.isLinkLocalAddress -> "link-local"
            address.isSiteLocalAddress -> "private"
            b[0].toInt() and 255 == 100 && b[1].toInt() and 0xc0 == 0x40 -> "shared-cgnat"
            address.isAnyLocalAddress -> "unspecified"
            address.isMulticastAddress -> "multicast"
            else -> "other-ipv4"
        }
        val first = b[0].toInt() and 255
        val second = b[1].toInt() and 255
        return when {
            first == 0xfe && second and 0xc0 == 0x80 -> "link-local"
            first and 0xfe == 0xfc -> "unique-local"
            first and 0xe0 == 0x20 -> "global-unicast"
            else -> "other"
        }
    }
}
