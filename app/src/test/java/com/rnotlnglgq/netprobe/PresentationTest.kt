package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.ui.Presentation
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    private fun face(index: Int, name: String, ip: String) = listOf(
        readField("visibleInterfaces[$index].name", "test") { name },
        readField("visibleInterfaces[$index].addresses", "test", sensitive = true) { listOf(address(ip, "private")) })
    private fun address(ip: String, classification: String) = AddressRecord(ip, if (':' in ip) "IPv6" else "IPv4",
        if (':' in ip) 64 else 24, classification, flagsSource = "test", flagsStatus = FieldStatus.unsupported_api)

    @Test fun interfaceSelectionFollowsNameWhenEnumerationChanges() {
        val first = Presentation.indexedGroups(face(0, "wlan0", "10.0.0.1") + face(1, "tun0", "10.8.0.1"), "visibleInterfaces", "接口")
        val key = first.first { it.title == "tun0" }.key
        val changed = Presentation.indexedGroups(face(0, "tun0", "10.8.0.2") + face(1, "wlan0", "10.0.0.2"), "visibleInterfaces", "接口")
        val selected = Presentation.selected(changed, key)!!
        assertEquals("tun0", selected.title)
        assertEquals("10.8.0.2", Presentation.addresses(selected.fields.last().value).single().address)
        assertEquals("wlan0", Presentation.selected(Presentation.indexedGroups(face(0,"wlan0","10.0.0.2"),"visibleInterfaces","接口"),key)!!.title)
        assertNull(Presentation.selected(emptyList(), key))
    }
    @Test fun groupsSeparateIndexesAndDoNotIncludeCrossCheckOrCount() {
        val fields = face(10,"eth0","10.1.0.1") + face(2,"wlan0","10.2.0.1") +
            readField("visibleInterfaces.count", "test") { 2 } + readField("visibleInterfaces.crossCheck","test") { false }
        val groups = Presentation.indexedGroups(fields,"visibleInterfaces","接口")
        assertEquals(listOf("wlan0","eth0"), groups.map { it.title })
        assertTrue(groups.all { it.fields.size == 2 })
    }
    @Test fun addressLabelsKeepScopeAndNeverCallDocumentationAddressesGlobal() {
        val cases = listOf(
            address("fe80::1%wlan0","link-local") to "链路本地",
            address("fd12::1","unique-local") to "唯一本地（内网）",
            address("2001:4860::1","global-unicast") to "全局单播",
            address("2001:db8::1","global-unicast") to "文档保留",
            address("192.0.2.1","other-ipv4") to "文档保留",
            address("198.51.100.3","other-ipv4") to "文档保留",
            address("203.0.113.7","other-ipv4") to "文档保留",
            address("192.168.0.1","private") to "私有内网",
            address("100.64.0.1","shared-cgnat") to "共享地址（CGNAT）")
        cases.forEach { (record, label) -> assertEquals(label, Presentation.addressType(record)) }
        assertEquals("fe80::1%wlan0", cases.first().first.address)
        assertEquals("其他地址", Presentation.addressType(address("hostname.example","other")))
    }
    @Test fun displayConvertsKnownConstantsWithoutChangingRawValues() {
        val mtu = readField("defaultNetwork.mtu", "LinkProperties.getMtu") { 0 }
        assertEquals("系统默认", Presentation.fieldText(mtu))
        assertEquals(0, mtu.value)
        assertEquals("LTE", Presentation.fieldText(readField("subscriptions[0].dataNetworkType","test") { 13 }))
        assertEquals("无服务", Presentation.fieldText(readField("subscriptions[0].serviceState.voiceState","test") { 1 }))
        assertEquals("回环", Presentation.addressType(address("::1","other")))
    }
    @Test fun unavailableAndEmptyAreDifferentUserStatesAndOriginalFieldsAreNotChanged() {
        assertNotEquals(Presentation.status(FieldStatus.empty), Presentation.status(FieldStatus.permission_denied))
        assertNotEquals(Presentation.status(FieldStatus.unsupported_api), Presentation.status(FieldStatus.network_unavailable))
        val fields = face(0,"wlan0","192.168.1.1")
        val snapshotBefore = fields.toString()
        Presentation.indexedGroups(fields,"visibleInterfaces","接口")
        Presentation.addresses(fields.last().value).forEach { Presentation.addressType(it) }
        assertEquals(snapshotBefore, fields.toString())
    }
}
