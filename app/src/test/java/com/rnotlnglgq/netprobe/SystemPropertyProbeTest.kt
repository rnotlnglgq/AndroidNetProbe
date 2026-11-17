package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.data.JsonExport
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class SystemPropertyProbeTest {
    private val carrier = PropertyCatalog.entries.first { it.key == "gsm.operator.numeric" }
    private fun runner(block: (String, Long) -> PropertyCommandResult) = object : PropertyCommandRunner {
        override fun query(key: String, timeoutMillis: Long) = block(key, timeoutMillis)
    }
    private fun snapshot() = NetworkSnapshot(capturedAtUtc="2026-11-12T00:00:00Z", completedAtUtc="2026-11-12T00:00:01Z",
        runId="test", event="test", environment=Environment(35,37,"test","test","1.4"),
        fieldResults=listOf(readField("defaultNetwork.vpn","test", sensitive = false) { false }))

    @Test fun initialDocumentDoesNotExecuteAndCatalogExcludesUniqueIdentifiers() {
        val initial = SystemPropertyProbe.notRequested()
        assertTrue(initial.fields.all { it.status == FieldStatus.not_requested && it.value == null })
        assertNull(initial.capturedAtUtc)
        assertEquals(PropertyCatalog.entries.size, PropertyCatalog.entries.map { it.key }.distinct().size)
        assertTrue(PropertyCatalog.entries.none { spec -> listOf("serial", "imei", "imsi", "iccid", "fingerprint").any { spec.key.contains(it, true) } })
        listOf("", ";id", "gsm.operator.numeric;id", "ro.serialno", "ro.boot.serialno").forEach { key ->
            try { GetpropCommandRunner().query(key, 1); fail("Unexpected key accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun rawStringsAndEmptyValuesAreNotGuessedOrSplitIntoSubscriptions() {
        val raw = "00101,,00102"
        assertEquals(raw, SystemPropertyProbe.convert(carrier, PropertyCommandResult(raw)).value)
        assertEquals("false", SystemPropertyProbe.convert(carrier, PropertyCommandResult("false")).value)
        assertEquals("0", SystemPropertyProbe.convert(carrier, PropertyCommandResult("0")).value)
        val empty = SystemPropertyProbe.convert(carrier, PropertyCommandResult(""))
        assertEquals(FieldStatus.empty, empty.status)
        assertTrue(empty.note!!.contains("无法"))
        assertNull(empty.value)
    }
    @Test fun errorsAndExplicitDenialsNeverPublishPartialValues() {
        assertEquals(FieldStatus.permission_denied, SystemPropertyProbe.convert(carrier, PropertyCommandResult("private", permissionDenied=true)).status)
        listOf(PropertyCommandResult("private", timedOut=true), PropertyCommandResult("private", outputTooLarge=true),
            PropertyCommandResult("private", exitCode=1), PropertyCommandResult("private", hasDiagnostics=true)).forEach {
            val field = SystemPropertyProbe.convert(carrier, it)
            assertEquals(FieldStatus.error, field.status)
            assertNull(field.value)
            assertFalse(field.toString().contains("private"))
        }
    }
    @Test fun individualFailureDoesNotStopOtherKeysAndMessagesAreNotCopied() {
        val doc = SystemPropertyProbe(runner { key, timeout ->
            assertTrue(timeout in 1..750)
            if (key == carrier.key) throw SecurityException("private detail")
            if (key == "net.dns1") throw IOException("private diagnostic")
            PropertyCommandResult("test")
        }).collect()
        assertEquals(PropertyCatalog.entries.size, doc.fields.size)
        assertEquals(FieldStatus.permission_denied, doc.fields.first { it.path == PropertyCatalog.path(carrier) }.status)
        assertEquals(FieldStatus.available, doc.fields.first().status)
        assertTrue(doc.fields.any { it.errorType == "IOException" })
        assertFalse(doc.fields.toString().contains("private"))
    }
    @Test fun deadlineStopsLaunchingCommandsAndCancellationStopsImmediately() {
        var nanos = 0L
        var calls = 0
        val probe = SystemPropertyProbe(runner { _, _ -> calls++; nanos += TimeUnit.SECONDS.toNanos(9); PropertyCommandResult("test") }, { nanos })
        val doc = probe.collect()
        assertEquals(1, calls)
        assertEquals("CollectionTimeout", doc.fields[1].errorType)
        var cancelled = false
        val command = object : PropertyCommandRunner {
            override fun query(key: String, timeoutMillis: Long): PropertyCommandResult { fail("Cancelled probe executed"); return PropertyCommandResult() }
            override fun cancel() { cancelled = true }
        }
        val stopped = SystemPropertyProbe(command)
        stopped.cancel()
        assertTrue(cancelled)
        try { stopped.collect(); fail("Cancellation swallowed") } catch (_: CancellationException) { }
    }
    @Test fun cacheAttachReplacesPreviousFieldsAndPrivacyDeletesSensitiveValues() {
        val doc = SystemPropertyProbe(runner { key, _ -> PropertyCommandResult(if (key == carrier.key) "00101,,00102" else "test") }).collect()
        val once = doc.attach(snapshot())
        val twice = doc.attach(once)
        assertEquals(once.fieldResults.size, twice.fieldResults.size)
        assertEquals(twice.fieldResults.size, twice.fieldResults.map { it.path }.distinct().size)
        assertEquals(snapshot().capturedAtUtc, twice.capturedAtUtc)
        assertTrue(twice.fieldResults.any { it.path == "systemProperties.capturedAtUtc" && it.status == FieldStatus.available })
        assertTrue(JsonExport.render(twice, true).contains("00101,,00102"))
        val redacted = JsonExport.render(twice, false)
        assertFalse(redacted.contains("00101,,00102"))
        assertTrue(redacted.contains("gsm.operator.numeric"))
    }
}
