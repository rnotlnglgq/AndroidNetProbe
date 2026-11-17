package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import com.rnotlnglgq.netprobe.model.*
import java.net.InetAddress
import com.rnotlnglgq.netprobe.probe.EndpointPolicy

/** UI projections only. Collection and export keep their original fields and values. */
data class FieldGroup(val key: String, val title: String, val fields: List<ProbeField<*>>)

class Presentation(private val strings: UiStrings) {
    fun indexedGroups(fields: List<ProbeField<*>>, root: String, defaultName: String): List<FieldGroup> {
        val pattern = Regex("^${Regex.escape(root)}\\[(\\d+)]\\.")
        return fields.mapNotNull { field -> pattern.find(field.path)?.groupValues?.get(1)?.let { it to field } }
            .groupBy({ it.first }, { it.second }).toSortedMap(compareBy { it.toInt() }).map { (index, group) ->
                val name = group.firstOrNull { it.path.endsWith(".name") }?.value as? String
                val session = group.firstOrNull { it.path.endsWith(".sessionSubscriptionId") }?.value as? String
                FieldGroup(name ?: session ?: "${root}[${index}]", name ?: "${defaultName} ${index.toInt() + 1}", group)
            }
    }
    fun selected(groups: List<FieldGroup>, key: String?): FieldGroup? = groups.firstOrNull { it.key == key } ?: groups.firstOrNull()
    fun addresses(value: Any?): List<AddressRecord> = (value as? List<*>)?.filterIsInstance<AddressRecord>().orEmpty()
    fun status(status: FieldStatus): String = when (status) {
        FieldStatus.available -> strings.get(R.string.ui_available)
        FieldStatus.empty -> strings.get(R.string.ui_no_data)
        FieldStatus.permission_denied -> strings.get(R.string.ui_permission_denied)
        FieldStatus.redacted -> strings.get(R.string.ui_redacted)
        FieldStatus.unsupported_api -> strings.get(R.string.ui_unsupported_android_version)
        FieldStatus.unsupported_feature -> strings.get(R.string.ui_unsupported_device)
        FieldStatus.network_unavailable -> strings.get(R.string.ui_no_network)
        FieldStatus.error -> strings.get(R.string.ui_read_failed)
        FieldStatus.not_requested -> strings.get(R.string.ui_disabled)
    }
    fun text(value: Any?): String = when (value) {
        null -> strings.get(R.string.ui_no_data)
        true -> strings.get(R.string.ui_yes)
        false -> strings.get(R.string.ui_no)
        is String -> when (value) {
            "granted" -> strings.get(R.string.ui_granted); "denied" -> strings.get(R.string.ui_permission_denied); "not_declared" -> strings.get(R.string.ui_not_declared)
            "available" -> strings.get(R.string.ui_available); "WIFI" -> "Wi-Fi"; "CELLULAR" -> strings.get(R.string.ui_cellular); "ETHERNET" -> strings.get(R.string.ui_ethernet)
            else -> value
        }
        is Number -> value.toString()
        is List<*> -> value.joinToString(strings.get(R.string.ui_list_separator)) { text(it) }
        else -> strings.get(R.string.ui_structured_data)
    }
    fun fieldText(field: ProbeField<*>): String {
        val n = (field.value as? Number)?.toInt()
        val key = field.path.substringAfterLast('.')
        if (field.path == "defaultNetwork.mtu" && n == 0) return strings.get(R.string.ui_system_default)
        if (n != null) {
            val label = when (key) {
                "dataNetworkType", "accessNetworkTechnology" -> radioType(n)
                "simState" -> mapOf(0 to strings.get(R.string.ui_unknown), 1 to strings.get(R.string.ui_sim_not_detected), 2 to strings.get(R.string.ui_pin_required), 3 to strings.get(R.string.ui_puk_required), 4 to strings.get(R.string.ui_network_locked), 5 to strings.get(R.string.ui_ready), 6 to strings.get(R.string.ui_not_ready), 7 to strings.get(R.string.ui_permanently_disabled), 8 to strings.get(R.string.ui_sim_read_error), 9 to strings.get(R.string.ui_restricted), 10 to strings.get(R.string.ui_loaded))[n]
                "slotIndex" -> if (n >= 0) strings.get(R.string.ui_slot_1_s ,n + 1) else strings.get(R.string.ui_no_valid_slot)
                "dataRoaming" -> mapOf(0 to strings.get(R.string.ui_close), 1 to strings.get(R.string.ui_on))[n]
                "voiceState" -> mapOf(0 to strings.get(R.string.ui_in_service), 1 to strings.get(R.string.ui_out_of_service), 2 to strings.get(R.string.ui_emergency_calls_only), 3 to strings.get(R.string.ui_radio_off))[n]
                "dataState" -> mapOf(0 to strings.get(R.string.ui_disconnected), 1 to strings.get(R.string.ui_connecting), 2 to strings.get(R.string.ui_connected), 3 to strings.get(R.string.ui_paused), 4 to strings.get(R.string.ui_disconnecting))[n]
                "standard" -> mapOf(0 to strings.get(R.string.ui_unknown), 1 to strings.get(R.string.ui_legacy_wi_fi), 4 to "802.11n", 5 to "802.11ac", 6 to "802.11ax", 7 to "802.11ad", 8 to "802.11be")[n]
                else -> null
            }
            if (label != null) return label
        }
        return text(field.value)
    }
    fun radioType(value: Int): String = mapOf(0 to strings.get(R.string.ui_unknown), 1 to "GPRS", 2 to "EDGE", 3 to "UMTS", 4 to "CDMA",
        5 to "EVDO 0", 6 to "EVDO A", 7 to "1xRTT", 8 to "HSDPA", 9 to "HSUPA", 10 to "HSPA", 11 to "iDEN",
        12 to "EVDO B", 13 to "LTE", 14 to "eHRPD", 15 to "HSPA+", 16 to "GSM", 17 to "TD-SCDMA", 18 to "IWLAN", 19 to "LTE CA", 20 to "NR")[value] ?: value.toString()
    fun endpointState(state: String): String = when (state) {
        "success" -> strings.get(R.string.ui_connected_635875); "dns_error" -> strings.get(R.string.ui_dns_resolution_failed); "timeout" -> strings.get(R.string.ui_timed_out)
        "tls_error" -> strings.get(R.string.ui_tls_verification_failed); "connection_error" -> strings.get(R.string.ui_connection_failed); "network_unavailable" -> strings.get(R.string.ui_no_network)
        "address_family_unavailable" -> strings.get(R.string.ui_address_family_unavailable); "configuration_error" -> strings.get(R.string.ui_configuration_or_address_family_mismatch)
        "http_error" -> strings.get(R.string.ui_http_error); "invalid_response" -> strings.get(R.string.ui_invalid_response_format); else -> state
    }
    fun addressType(address: AddressRecord): String {
        // Numeric input only: never resolve names to format a UI label.
        val raw = address.address.substringBefore('%')
        val bytes = if (EndpointPolicy.numericFamily(raw) != null)
            runCatching { InetAddress.getByName(raw).address }.getOrNull() else null
        if (bytes != null && bytes.size == 16) {
            if (bytes.all { it == 0.toByte() }) return strings.get(R.string.ui_unspecified)
            if (bytes.dropLast(1).all { it == 0.toByte() } && bytes.last() == 1.toByte()) return strings.get(R.string.ui_loopback)
        }
        if (bytes?.size == 16 && bytes.take(4).map { it.toInt() and 255 } == listOf(0x20, 1, 0x0d, 0xb8)) return strings.get(R.string.ui_documentation_range)
        if (bytes?.size == 4) {
            val b = bytes.map { it.toInt() and 255 }
            if (b.take(3) in listOf(listOf(192, 0, 2), listOf(198, 51, 100), listOf(203, 0, 113))) return strings.get(R.string.ui_documentation_range)
        }
        return when (address.classification) {
            "link-local" -> strings.get(R.string.ui_link_local)
            "unique-local" -> strings.get(R.string.ui_unique_local_private)
            "global-unicast" -> strings.get(R.string.ui_global_unicast)
            "private" -> strings.get(R.string.ui_private)
            "shared-cgnat" -> strings.get(R.string.ui_shared_cgnat)
            "loopback" -> strings.get(R.string.ui_loopback)
            "unspecified" -> strings.get(R.string.ui_unspecified)
            "multicast" -> strings.get(R.string.ui_multicast)
            "other-ipv4" -> strings.get(R.string.ui_other_ipv4)
            else -> strings.get(R.string.ui_other_address)
        }
    }
    fun label(path: String): String {
        if (path == "externalChecks.ipv4.result") return strings.get(R.string.ui_ipv4_connection)
        if (path == "externalChecks.ipv6.result") return strings.get(R.string.ui_ipv6_connection)
        if (path.endsWith("serviceState.state")) return strings.get(R.string.ui_service_state)
        if (path.endsWith("signalStrength.state")) return strings.get(R.string.ui_signal_state)
        val key = path.substringAfterLast('.')
        return labels[key] ?: key
    }
    private val labels by lazy { mapOf(
        "state" to strings.get(R.string.ui_state), "sessionNetworkId" to strings.get(R.string.ui_session_network_id), "transports" to strings.get(R.string.ui_transport), "validated" to strings.get(R.string.ui_internet_validation),
        "internetCapability" to strings.get(R.string.ui_internet_capability), "metered" to strings.get(R.string.ui_metered), "vpn" to "VPN", "interfaceName" to strings.get(R.string.ui_interfaces),
        "name" to strings.get(R.string.ui_name), "up" to strings.get(R.string.ui_enabled), "loopback" to strings.get(R.string.ui_loopback_interface), "mtu" to "MTU", "count" to strings.get(R.string.ui_interface_count),
        "downstreamKbps" to strings.get(R.string.ui_estimated_downstream_bandwidth_kbps), "upstreamKbps" to strings.get(R.string.ui_estimated_upstream_bandwidth_kbps), "signalStrength" to strings.get(R.string.ui_signal_strength),
        "captivePortal" to strings.get(R.string.ui_captive_portal), "notRestricted" to strings.get(R.string.ui_unrestricted_network), "notSuspended" to strings.get(R.string.ui_not_suspended),
        "linkAddresses" to strings.get(R.string.ui_link_addresses), "defaultNetworkAddresses" to strings.get(R.string.ui_address), "addresses" to strings.get(R.string.ui_interface_addresses),
        "dnsServers" to strings.get(R.string.ui_dns_servers), "routes" to strings.get(R.string.ui_routes), "dnsSearchDomains" to strings.get(R.string.ui_dns_search_domains), "httpProxy" to strings.get(R.string.ui_http_proxy),
        "active" to "Private DNS", "serverName" to strings.get(R.string.ui_private_dns_hostname), "nat64Prefix" to strings.get(R.string.ui_nat64_prefix),
        "crossCheck" to strings.get(R.string.ui_interface_address_comparison), "simOperatorName" to strings.get(R.string.ui_sim_carrier), "simOperatorMccMnc" to "SIM MCC/MNC",
        "registeredOperatorName" to strings.get(R.string.ui_registered_carrier), "registeredOperatorMccMnc" to strings.get(R.string.ui_registered_mcc_mnc), "registeredMccMnc" to strings.get(R.string.ui_registered_mcc_mnc),
        "simMccMnc" to "SIM MCC/MNC", "simCountryIso" to strings.get(R.string.ui_sim_country), "networkCountryIso" to strings.get(R.string.ui_network_country),
        "simState" to strings.get(R.string.ui_sim_state), "networkRoaming" to strings.get(R.string.ui_network_roaming), "simCarrierId" to strings.get(R.string.ui_carrier_id), "simCarrierIdName" to strings.get(R.string.ui_carrier_brand),
        "simSpecificCarrierId" to strings.get(R.string.ui_specific_carrier_id), "simSpecificCarrierIdName" to strings.get(R.string.ui_specific_carrier_name),
        "carrierIdFromSimMccMnc" to strings.get(R.string.ui_carrier_id_from_mcc_mnc), "activeModemCount" to strings.get(R.string.ui_active_modem_count),
        "activeCount" to strings.get(R.string.ui_active_subscription_count), "sessionSubscriptionId" to strings.get(R.string.ui_session_subscription_id), "slotIndex" to strings.get(R.string.ui_slot_index),
        "displayName" to strings.get(R.string.ui_sim_name), "countryIso" to strings.get(R.string.ui_subscription_country), "carrierId" to strings.get(R.string.ui_subscription_carrier_id),
        "dataRoaming" to strings.get(R.string.ui_data_roaming_setting), "opportunistic" to strings.get(R.string.ui_opportunistic_subscription), "subscriptionType" to strings.get(R.string.ui_subscription_type),
        "portIndex" to strings.get(R.string.ui_port_index), "usageSetting" to strings.get(R.string.ui_usage_setting), "onlyNonTerrestrial" to strings.get(R.string.ui_non_terrestrial_networks_only),
        "carrierName" to strings.get(R.string.ui_carrier), "embedded" to "eSIM", "mcc" to "MCC", "mnc" to "MNC", "dataNetworkType" to strings.get(R.string.ui_data_network_technology),
        "dataState" to strings.get(R.string.ui_data_connection_state), "voiceState" to strings.get(R.string.ui_voice_service_state), "operatorNumeric" to strings.get(R.string.ui_registered_plmn),
        "operatorAlphaLong" to strings.get(R.string.ui_long_operator_name), "operatorAlphaShort" to strings.get(R.string.ui_short_operator_name), "roaming" to strings.get(R.string.ui_roaming),
        "manualSelection" to strings.get(R.string.ui_manual_network_selection), "channelNumber" to strings.get(R.string.ui_channel), "cellBandwidthsKhz" to strings.get(R.string.ui_cell_bandwidth_khz),
        "duplexMode" to strings.get(R.string.ui_duplex_mode), "registrations" to strings.get(R.string.ui_network_registrations), "level" to strings.get(R.string.ui_signal_level), "radios" to strings.get(R.string.ui_signals_by_technology),
        "locationEnabled" to strings.get(R.string.ui_system_location), "observedAtUtc" to strings.get(R.string.ui_sample_time), "ssid" to strings.get(R.string.ui_wi_fi_name), "bssid" to "BSSID",
        "rssiDbm" to strings.get(R.string.ui_signal_dbm), "linkSpeedMbps" to strings.get(R.string.ui_link_speed_mbps), "frequencyMhz" to strings.get(R.string.ui_frequency_mhz),
        "standard" to strings.get(R.string.ui_wi_fi_standard), "txLinkSpeedMbps" to strings.get(R.string.ui_transmit_link_speed_mbps), "rxLinkSpeedMbps" to strings.get(R.string.ui_receive_link_speed_mbps),
        "securityType" to strings.get(R.string.ui_security_type), "hiddenSsid" to strings.get(R.string.ui_hidden_ssid), "manifestDeclared" to strings.get(R.string.ui_declared_app_permissions)
    ) }
}
