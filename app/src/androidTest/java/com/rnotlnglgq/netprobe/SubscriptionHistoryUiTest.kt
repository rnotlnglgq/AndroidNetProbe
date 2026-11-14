package com.rnotlnglgq.netprobe

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.rnotlnglgq.netprobe.probe.*
import com.rnotlnglgq.netprobe.ui.SubscriptionHistorySection
import org.junit.Rule
import org.junit.Test

class SubscriptionHistoryUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun recordsAreSummarizedPaginatedAndSensitiveLabelsStayHidden() {
        var reveal by mutableStateOf(false)
        val doc = SubscriptionHistoryDocument.collect(35,true,true,true) {
            (0..20).map { index -> SubscriptionRecordFields.collect("subscriptionHistory[$index]","alias-$index",35) { key ->
                when(key) { "displayName" -> "私有卡名$index"; "carrierName" -> "测试运营商"; "slotIndex" -> -1; else -> null }
            } }
        }
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            SubscriptionHistorySection(doc,false,true,reveal,{}, {}) {}
        } } }
        compose.onNodeWithText("记录 1").assertExists()
        compose.onNodeWithText("记录 21").assertDoesNotExist()
        compose.onNodeWithText("私有卡名0").assertDoesNotExist()
        compose.runOnIdle { reveal = true }
        compose.onNodeWithText("私有卡名0").assertExists()
        compose.onNodeWithText("SubscriptionInfo.getDisplayName").assertDoesNotExist()
        compose.onNodeWithText("下一页").performScrollTo().performClick()
        compose.onNodeWithText("记录 21").assertExists()
        compose.onNodeWithText("记录 1").assertDoesNotExist()
        compose.runOnIdle { reveal = false }
        compose.onNodeWithText("私有卡名20").assertDoesNotExist()
    }
}
