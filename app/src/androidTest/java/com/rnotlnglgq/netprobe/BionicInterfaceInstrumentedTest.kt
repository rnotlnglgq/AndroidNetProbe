package com.rnotlnglgq.netprobe

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rnotlnglgq.netprobe.model.FieldStatus
import com.rnotlnglgq.netprobe.probe.BionicInterfaceProbe
import com.rnotlnglgq.netprobe.probe.BionicBridge
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BionicInterfaceInstrumentedTest {
    @Test fun nativeLibraryLoadsAndReturnsAResultUnderAppIdentity() {
        BionicBridge.read() // Let instrumentation report the actual linker/JNI failure before the app fallback.
        val document = BionicInterfaceProbe().capture()
        val count = document.fields.first()
        // A policy refusal is a valid observation; a missing library/JNI method is a packaging failure.
        assertNotEquals(FieldStatus.unsupported_feature, count.status)
        assertNotNull(document.capturedAtUtc)
        if (count.status == FieldStatus.available) {
            assertEquals(document.interfaces.size, count.value)
            assertTrue(document.interfaces.flatMap { it.addresses }.all { it.family in listOf("IPv4","IPv6") })
        } else assertNotNull(count.errorType)
    }
}
