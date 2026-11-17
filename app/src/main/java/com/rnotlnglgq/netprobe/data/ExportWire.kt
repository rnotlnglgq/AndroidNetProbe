package com.rnotlnglgq.netprobe.data

import com.google.gson.JsonObject
import com.rnotlnglgq.netprobe.model.*

/** JSON 1.0 boundary. Runtime state and UI-only metadata stay outside the wire representation. */
internal object ExportWire {
    private fun obj(vararg fields: Pair<String,Any?>) = JsonObject().apply {
        fields.forEach { (key,value) -> add(key,JsonExport.gson.toJsonTree(value)) }
    }
    private fun field(f: ProbeField<*>) = obj("path" to f.path,"value" to f.value,"status" to f.status.name,
        "source" to f.source,"permission" to f.permission,"note" to f.note,"errorType" to f.errorType,
        "sensitive" to f.sensitive,"observedStatus" to f.observedStatus?.name,"acquisitionPermissions" to f.acquisitionPermissions)
    private fun snapshot(s: NetworkSnapshot) = obj("schemaVersion" to s.schemaVersion,"capturedAtUtc" to s.capturedAtUtc,
        "completedAtUtc" to s.completedAtUtc,"runId" to s.runId,"event" to s.event,
        "environment" to s.environment.let { obj("androidApi" to it.androidApi,"targetSdk" to it.targetSdk,
            "manufacturer" to it.manufacturer,"model" to it.model,"appVersion" to it.appVersion) },
        "fieldResults" to s.fieldResults.map(::field),"warnings" to s.warnings)
    fun document(s: NetworkSnapshot,raw: Boolean,time: String,events: List<NetworkEvent>,comparison: PermissionComparison?) = obj(
        "schemaVersion" to "1.0","exportMode" to if (raw) "raw" else "redacted","exportedAtUtc" to time,
        "snapshot" to snapshot(s),"eventHistory" to events.map { obj("capturedAtUtc" to it.capturedAtUtc,"event" to it.event) },
        "permissionComparison" to comparison?.let { obj("experiment" to it.experiment,"before" to snapshot(it.before),
            "after" to snapshot(it.after),"changedPaths" to it.changedPaths,"note" to it.note) })
}
