package com.rnotlnglgq.netprobe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.ui.theme.NetProbeTheme

// Synthetic local preview fixtures, never used by the running app or exported.
private val previewInterfaces = listOf(
    readField("visibleInterfaces[0].name", "NetworkInterface.getName") { "wlan0" },
    readField("visibleInterfaces[0].up", "NetworkInterface.isUp") { true },
    readField("visibleInterfaces[0].mtu", "NetworkInterface.getMTU") { 1500 },
    readField("visibleInterfaces[0].addresses", "NetworkInterface.getInterfaceAddresses", sensitive = true) {
        listOf(AddressRecord("192.168.1.10", "IPv4", 24, "private", flagsSource="NetworkInterface", flagsStatus=FieldStatus.unsupported_api),
            AddressRecord("fe80::1234%wlan0", "IPv6", 64, "link-local", flagsSource="NetworkInterface", flagsStatus=FieldStatus.unsupported_api))
    }, readField("visibleInterfaces[1].name", "NetworkInterface.getName") { "tun0" })

@Preview(name = "接口 · 完整地址", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun InterfaceFullPreview() = InterfacePreview(true)

@Preview(name = "接口 · 隐藏地址", widthDp = 360, heightDp = 760, showBackground = true)
@Composable
private fun InterfaceHiddenPreview() = InterfacePreview(false)

@Composable
private fun InterfacePreview(reveal: Boolean) {
    NetProbeTheme(dynamicColor = false) {
        Surface { Column(Modifier.verticalScroll(rememberScrollState())) { InterfaceSection(previewInterfaces, "wlan0", reveal) {} } }
    }
}
