package com.rnotlnglgq.netprobe.model

/** Permission expressions are data; legacy prose is confined to the JSON boundary. */
sealed interface PermissionRequirement {
    data object None : PermissionRequirement
    data class Permission(val name: String, val runtime: Boolean) : PermissionRequirement
    data class AllOf(val requirements: List<PermissionRequirement>) : PermissionRequirement
    data class AnyOf(val requirements: List<PermissionRequirement>) : PermissionRequirement

    fun runtimeAlternatives(): List<Set<String>> = when (this) {
        None -> listOf(emptySet())
        is Permission -> listOf(if (runtime) setOf(name) else emptySet())
        is AnyOf -> requirements.flatMap { it.runtimeAlternatives() }
        is AllOf -> requirements.fold(listOf(emptySet())) { result, next ->
            result.flatMap { a -> next.runtimeAlternatives().map { b -> a + b } }
        }
    }
}

object PermissionCatalog {
    private fun atom(name: String) = PermissionRequirement.Permission(name,
        name in setOf("READ_PHONE_STATE", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"))
    private fun all(vararg names: String) = PermissionRequirement.AllOf(names.map(::atom))
    private fun any(vararg names: String) = PermissionRequirement.AnyOf(names.map(::atom))
    private val definitions = mapOf(
        "none" to PermissionRequirement.None,
        "READ_PHONE_STATE + ACCESS_COARSE_LOCATION" to all("READ_PHONE_STATE","ACCESS_COARSE_LOCATION"),
        "ACCESS_FINE_LOCATION + ACCESS_WIFI_STATE + ACCESS_NETWORK_STATE" to all("ACCESS_FINE_LOCATION","ACCESS_WIFI_STATE","ACCESS_NETWORK_STATE"),
        "ACCESS_FINE_LOCATION + ACCESS_WIFI_STATE" to all("ACCESS_FINE_LOCATION","ACCESS_WIFI_STATE"),
        "ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION (字段可见性)" to any("ACCESS_COARSE_LOCATION","ACCESS_FINE_LOCATION"),
        "ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION" to any("ACCESS_COARSE_LOCATION","ACCESS_FINE_LOCATION"),
        "READ_PHONE_STATE or READ_BASIC_PHONE_STATE" to any("READ_PHONE_STATE","READ_BASIC_PHONE_STATE")
    )
    fun requirement(legacy: String): PermissionRequirement = definitions[legacy] ?: atom(legacy)
    fun forField(field: ProbeField<*>) = PermissionRequirement.AllOf(
        listOf(requirement(field.permission)) + field.acquisitionPermissions.map(::requirement))
}

/** Stable keys shared by collection, grouping and comparison. */
object FieldKeys {
    const val DEFAULT_INTERFACE = "defaultNetwork.interfaceName"
    const val DEFAULT_ADDRESSES = "defaultNetwork.linkAddresses"
    const val DEFAULT_VALIDATED = "defaultNetwork.validated"
    const val DEFAULT_METERED = "defaultNetwork.metered"
    const val DEFAULT_VPN = "defaultNetwork.vpn"
    const val DEFAULT_TRANSPORTS = "defaultNetwork.transports"
}

enum class Sensitivity { PUBLIC, SENSITIVE }
data class FieldDefinition(val path: String, val source: String, val sensitivity: Sensitivity,
    val permissions: PermissionRequirement) {
    val section: String get() = path.substringBefore('.').substringBefore('[')
}
val ProbeField<*>.definition: FieldDefinition get() = FieldDefinition(path, source,
    if (sensitive) Sensitivity.SENSITIVE else Sensitivity.PUBLIC, PermissionCatalog.forField(this))
