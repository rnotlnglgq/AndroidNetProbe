package com.rnotlnglgq.netprobe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.ui.InterfaceSection
import org.junit.Rule
import org.junit.Test

class InterfaceUiTest {
    @get:Rule val compose = createComposeRule()
    private fun face(index: Int, name: String, ip: String, category: String) = listOf(
        readField("visibleInterfaces[$index].name", "test", sensitive = false) { name },
        readField("visibleInterfaces[$index].up", "test", sensitive = false) { true },
        readField("visibleInterfaces[$index].mtu", "test", sensitive = false) { 1500 },
        readField("visibleInterfaces[$index].addresses", "test", sensitive = true) {
            listOf(AddressRecord(ip,"IPv6",64,category,flagsSource="test",flagsStatus=FieldStatus.unsupported_api))
        })

    @Test fun tabsShowOnlySelectedInterfaceAndPrivacyHidesAddresses() {
        var reveal by mutableStateOf(false)
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                InterfaceSection(face(0,"wlan0","fe80::1%wlan0","link-local") + face(1,"tun0","fd12::1","unique-local"), "wlan0", reveal) {}
            } }
        }
        compose.onNodeWithText("链路本地").assertExists()
        compose.onNodeWithText("fe80::1%wlan0").assertDoesNotExist()
        compose.onNodeWithText("fd12::1").assertDoesNotExist()
        compose.onNodeWithText("tun0").performClick()
        compose.onNodeWithText("唯一本地（内网）").assertExists()
        compose.onNodeWithText("链路本地").assertDoesNotExist()
        compose.runOnIdle { reveal = true }
        compose.onNodeWithText("fd12::1").assertExists()
        compose.onNodeWithText("复制地址").assertExists()
        compose.onNodeWithText("visibleInterfaces[1].addresses").assertDoesNotExist()
        compose.runOnIdle { reveal = false }
        compose.onNodeWithText("fd12::1").assertDoesNotExist()
    }
}
