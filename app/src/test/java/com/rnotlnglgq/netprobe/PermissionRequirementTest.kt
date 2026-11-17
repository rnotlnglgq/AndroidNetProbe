package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class PermissionRequirementTest {
    @Test fun ordinaryAlternativeRemovesRuntimeRequirementButAcquisitionRestoresIt() {
        val field=readField("test","test","READ_PHONE_STATE or READ_BASIC_PHONE_STATE",sensitive=false) { true }
        assertTrue(PermissionCatalog.forField(field).runtimeAlternatives().any { it.isEmpty() })
        assertTrue(PermissionCatalog.forField(field.withAcquisition("READ_PHONE_STATE")).runtimeAlternatives().all { "READ_PHONE_STATE" in it })
    }
    @Test fun fieldReaderPropagatesCancellation() {
        try { readField<Unit>("test","test",sensitive=false) { throw CancellationException() };fail() }
        catch (_: CancellationException) { }
    }
}
