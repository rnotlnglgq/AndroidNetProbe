package com.rnotlnglgq.netprobe.model

data class NetworkEvent(val capturedAtUtc: String, val event: String)

data class ExportDocument(
    val schemaVersion: String = "1.0",
    val exportMode: String,
    val exportedAtUtc: String,
    val snapshot: NetworkSnapshot,
    val eventHistory: List<NetworkEvent> = emptyList(),
    val permissionComparison: PermissionComparison? = null
)
