package com.rnotlnglgq.netprobe.data

import com.google.gson.GsonBuilder
import com.rnotlnglgq.netprobe.model.*
import java.time.Instant
import java.time.Clock

object JsonExport {
    val gson = GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create()

    fun redact(snapshot: NetworkSnapshot): NetworkSnapshot = snapshot.copy(
        fieldResults = snapshot.fieldResults.map { f ->
            if (!f.sensitive) f else ProbeField<Any>(path = f.path, value = null,
                status = if (f.value != null && f.status == FieldStatus.available) FieldStatus.redacted else f.status,
                source = f.source, permission = f.permission, note = f.note,
                errorType = f.errorType, sensitive = true, observedStatus = f.status, acquisitionPermissions = f.acquisitionPermissions)
        }
    )

    fun render(snapshot: NetworkSnapshot, raw: Boolean, events: List<NetworkEvent> = emptyList(), comparison: PermissionComparison? = null, clock: Clock = Clock.systemUTC()): String =
        gson.toJson(ExportWire.document(if (raw) snapshot else redact(snapshot), raw,
            Instant.now(clock).toString(), events, if (raw) comparison else comparison?.copy(
                before = redact(comparison.before), after = redact(comparison.after))))
}
