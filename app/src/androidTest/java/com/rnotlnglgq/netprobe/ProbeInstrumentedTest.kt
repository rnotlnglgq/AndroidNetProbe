package com.rnotlnglgq.netprobe

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import androidx.test.platform.app.InstrumentationRegistry
import com.rnotlnglgq.netprobe.data.ProbeRepository
import com.rnotlnglgq.netprobe.model.FieldStatus
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.rnotlnglgq.netprobe.ui.ProbeViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProbeInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realSnapshotHasCompleteMetadataWithoutRuntimeGrants() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = ProbeRepository(app).capture("instrumented")
        assertEquals("1.0", snapshot.schemaVersion)
        assertEquals(snapshot.fieldResults.size, snapshot.fieldResults.map { it.path }.distinct().size)
        assertTrue(snapshot.fieldResults.all { it.source.isNotBlank() && it.permission.isNotBlank() })
        assertTrue(snapshot.fieldResults.any { it.path == "visibleInterfaces.count" })
        assertTrue(snapshot.fieldResults.any { it.path == "defaultNetwork.routes" })
        assertTrue(snapshot.fieldResults.filter { it.path.startsWith("telephony.") }.isNotEmpty())
        assertEquals(FieldStatus.not_requested, snapshot.fieldResults.first { it.path == "externalChecks.state" }.status)
    }

    @Test fun callbacksStopWhenPausedOrActivityLeavesForeground() {
        compose.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[ProbeViewModel::class.java]
            assertTrue(model.foreground)
            assertTrue(model.registered)
            model.toggleMonitoring()
            assertFalse(model.registered)
            model.toggleMonitoring()
            assertTrue(model.registered)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[ProbeViewModel::class.java]
            assertFalse(model.foreground)
            assertFalse(model.registered)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.activityRule.scenario.onActivity { activity ->
            assertTrue(ViewModelProvider(activity)[ProbeViewModel::class.java].registered)
        }
    }

    @Test fun unconfiguredEndpointCannotSendRequests() {
        compose.onNodeWithText("公网").performScrollTo().performClick()
        compose.onNodeWithText("公网 / Cloudflare").performScrollTo().performClick()
        compose.onNodeWithText("配置自有服务器").performClick()
        compose.onNodeWithText("检测自有服务器…").assertIsNotEnabled()
    }

    @Test fun homeStartsWithoutDangerousPermission() {
        compose.onNodeWithText("NetProbe").assertIsDisplayed()
        compose.onNodeWithText("刷新").assertIsDisplayed()
    }
}
