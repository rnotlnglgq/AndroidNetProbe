package com.rnotlnglgq.netprobe

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rnotlnglgq.netprobe.model.FieldStatus
import com.rnotlnglgq.netprobe.probe.PhonePermissionProbe
import com.rnotlnglgq.netprobe.probe.WifiProbe
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionProbeInstrumentedTest {
    @Test fun wifiDeniedHasSeparatePermissionAndLocationState() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val fields = WifiProbe(app) {}.collect(true)
        assertTrue(fields.any { it.path == "wifi.locationEnabled" })
        if (app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            assertEquals(FieldStatus.permission_denied, fields.first { it.path == "wifi.ssid" }.status)
        assertEquals(FieldStatus.not_requested, WifiProbe(app) {}.collect(false).first().status)
    }

    @Test fun phoneGrantDoesNotEnableCollectionUntilExplicitExperiment() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val probe = PhonePermissionProbe(app)
        assertEquals(FieldStatus.not_requested, probe.collect(false).first().status)
        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) return
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.READ_PHONE_STATE)
        assertEquals(FieldStatus.not_requested, probe.collect(false).first().status)
        val result = probe.collect(true)
        assertTrue(result.first().status in listOf(FieldStatus.available, FieldStatus.empty))
        assertTrue(result.none { it.path.contains("iccid", true) || it.path.contains("phoneNumber", true) })
    }
}
