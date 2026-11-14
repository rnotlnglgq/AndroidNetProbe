package com.rnotlnglgq.netprobe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.rnotlnglgq.netprobe.data.ProbeRepository
import com.rnotlnglgq.netprobe.model.FieldStatus
import com.rnotlnglgq.netprobe.ui.PermissionLabels
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** One-time API33/34/35 smoke checks; not a required per-release emulator matrix. */
class CompatibilitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun tab(text: String) = compose.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    @Test fun permissionHeadersAndHistoryVersionGate() {
        tab("Wi-Fi").performScrollTo().performClick()
        compose.onNodeWithText(PermissionLabels.WIFI).performScrollTo().assertIsDisplayed()
        tab("订阅").performScrollTo().performClick()
        compose.onAllNodesWithText(PermissionLabels.PHONE).onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("历史记录").performScrollTo().performClick()
        if (Build.VERSION.SDK_INT < 34) {
            compose.onNodeWithText("需要 Android 14 或以上").assertExists()
            compose.onNodeWithText("读取记录").assertIsNotEnabled()
        } else compose.onNodeWithText("读取记录").assertExists()

        val instrument = InstrumentationRegistry.getInstrumentation()
        val app = instrument.targetContext
        instrument.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.READ_PHONE_STATE)
        assertNotEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
        val history = ProbeRepository(app).captureHistory(true)
        assertEquals(if (Build.VERSION.SDK_INT >= 34) FieldStatus.available else FieldStatus.unsupported_api, history.fields.first().status)
        // The explanatory badge must remain after grant, not only during denial.
        compose.onAllNodesWithText(PermissionLabels.PHONE).onFirst().assertExists()
    }
}
