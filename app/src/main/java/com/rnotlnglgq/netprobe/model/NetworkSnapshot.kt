package com.rnotlnglgq.netprobe.model

data class Environment(
    val androidApi: Int, val targetSdk: Int, val manufacturer: String,
    val model: String, val appVersion: String
)

data class NetworkSnapshot(
    val schemaVersion: String = "1.0",
    val capturedAtUtc: String,
    val completedAtUtc: String,
    val runId: String,
    val event: String,
    val environment: Environment,
    val fieldResults: List<ProbeField<*>>,
    val warnings: List<String> = emptyList()
)

data class AddressRecord(
    val address: String, val family: String, val prefixLength: Int, val classification: String,
    val scope: Int? = null, val flags: Int? = null,
    val temporary: Boolean? = null, val deprecated: Boolean? = null,
    val flagsSource: String, val flagsStatus: FieldStatus,
    val note: String = "地址分类不代表公网可达性；标志位不保证完整"
)

data class RouteRecord(val destination: String, val prefixLength: Int, val gateway: String?,
    val interfaceName: String?, val type: Int?)
