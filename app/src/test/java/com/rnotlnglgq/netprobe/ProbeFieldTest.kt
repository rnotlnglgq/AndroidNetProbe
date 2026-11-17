package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.model.*
import org.junit.Assert.*
import org.junit.Test

class ProbeFieldTest {
    @Test fun distinguishesEmptyDeniedErrorAndValidFalse() {
        assertEquals(FieldStatus.empty, readField("x", "api", sensitive = false) { "" }.status)
        assertEquals(FieldStatus.empty, readField("x", "api", sensitive = false) { emptyList<String>() }.status)
        assertEquals(FieldStatus.available, readField("x", "api", sensitive = false) { false }.status)
        assertEquals(FieldStatus.permission_denied,
            readField<String>("x", "api", sensitive = false) { throw SecurityException("private") }.status)
        val error = readField<String>("x", "api", sensitive = false) { throw IllegalStateException("private") }
        assertEquals(FieldStatus.error, error.status)
        assertEquals("IllegalStateException", error.errorType)
        assertFalse(error.toString().contains("private"))
    }
}
