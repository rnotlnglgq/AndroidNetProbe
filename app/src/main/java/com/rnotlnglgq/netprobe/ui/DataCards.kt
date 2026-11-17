package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rnotlnglgq.netprobe.data.InterfaceComparison
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.*

@Composable
fun Panel(title: String, permissionHint: String? = null, content: @Composable ColumnScope.() -> Unit) {

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            permissionHint?.let { PermissionCaption(it) }
            content()
        }
    }
}

@Composable
fun PermissionCaption(text: String) {

    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SummaryRow(label: String, value: String) {

    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value) }
    }
}

@Composable
fun FieldDetails(fields: List<ProbeField<*>>, reveal: Boolean, onCopied: (String) -> Unit) {
    val strings = rememberUiStrings()
    var expanded by rememberSaveable(fields.joinToString { it.path }) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) strings.get(R.string.ui_collapse_technical_details) else strings.get(R.string.ui_technical_details)) }
    if (expanded) fields.forEach { field ->
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(field.path, style = MaterialTheme.typography.labelLarge)
                Text(strings.get(R.string.ui_1_s_field_permission_2_s ,field.status, strings.legacy(field.permission)), style = MaterialTheme.typography.bodySmall)
                if (field.acquisitionPermissions.isNotEmpty()) Text(strings.get(R.string.ui_object_acquisition_1_s ,field.acquisitionPermissions.joinToString(" + ")), style = MaterialTheme.typography.bodySmall)
                Text(field.source, style = MaterialTheme.typography.bodySmall)
                field.note?.let { Text(strings.legacy(it), style = MaterialTheme.typography.bodySmall) }
                field.errorType?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        CopyableValue(field, reveal, onCopied)
        HorizontalDivider()
    }
}

@Composable
fun AddressSummary(address: AddressRecord, reveal: Boolean, onCopied: (String) -> Unit) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(address.family, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(presentation.addressType(address), style = MaterialTheme.typography.labelLarge)
            Text(if (address.prefixLength >= 0) "/${address.prefixLength}" else strings.get(R.string.ui_prefix_unknown), style = MaterialTheme.typography.labelLarge)
        }
        SelectionContainer {
            Text(if (reveal) address.address else "••••", fontFamily = FontFamily.Monospace, style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                modifier = Modifier.horizontalScroll(rememberScrollState()))
        }
        if (address.temporary == true || address.deprecated == true) {
            Text(listOfNotNull(if (address.temporary == true) strings.get(R.string.ui_temporary_address) else null,
                if (address.deprecated == true) strings.get(R.string.ui_deprecated) else null).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        if (reveal) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { copyProbeText(context, "IP", address.address, true); onCopied(strings.get(R.string.ui_address_copied)) }) { Text(strings.get(R.string.ui_copy_address)) }
            TextButton(enabled = address.prefixLength >= 0, onClick = { copyProbeText(context, "CIDR", "${address.address}/${address.prefixLength}", true); onCopied(strings.get(R.string.ui_cidr_copied)) }) { Text(strings.get(R.string.ui_copy_cidr)) }
        }
    }
}

@Composable
fun DataFieldCard(field: ProbeField<*>, reveal: Boolean, onCopied: (String) -> Unit) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    val permissionLabels = androidx.compose.runtime.remember(strings) { PermissionLabels(strings) }
    Panel(presentation.label(field.path), permissionLabels.forField(field)) {
        val addresses = presentation.addresses(field.value)
        when {
            addresses.isNotEmpty() -> addresses.forEachIndexed { i, address ->
                if (i > 0) HorizontalDivider()
                AddressSummary(address, !field.sensitive || reveal, onCopied)
            }
            field.status != FieldStatus.available -> Text(presentation.status(field.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
            field.value is EndpointResult -> EndpointSummary(field.value, !field.sensitive || reveal)
            field.sensitive && !reveal -> Text(strings.get(R.string.ui_hidden), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> if (field.value is String || field.value is Number || field.value is Boolean || field.value == null)
                SelectionContainer { Text(presentation.fieldText(field)) } else StructuredValue(field.value)
        }
        FieldDetails(listOf(field), reveal, onCopied)
    }
}

@Composable
private fun StructuredValue(value: Any?) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    when (value) {
        is EndpointResult -> EndpointSummary(value, true)
        is ProxyRecord -> {
            SummaryRow(strings.get(R.string.ui_host), value.host ?: strings.get(R.string.ui_none))
            SummaryRow(strings.get(R.string.ui_port), value.port.toString())
            if (value.exclusions.isNotEmpty()) SummaryRow(strings.get(R.string.ui_exclusions), value.exclusions.joinToString("、"))
            value.pacUrl?.takeIf { it.isNotBlank() }?.let { SummaryRow("PAC", it) }
        }
        is PrefixRecord -> SelectionContainer { Text("${value.address}/${value.prefixLength}", fontFamily = FontFamily.Monospace) }
        is InterfaceComparison -> {
            SummaryRow(strings.get(R.string.ui_default_interface_visible), presentation.text(value.defaultInterfaceVisible))
            SummaryRow(strings.get(R.string.ui_shared_address_count), presentation.text(value.sharedAddressCount))
            value.linkOnlyAddresses?.let { SummaryRow(strings.get(R.string.ui_link_view_only), it.ifEmpty { listOf(strings.get(R.string.ui_none)) }.joinToString("\n")) }
            value.javaOnlyAddresses?.let { SummaryRow(strings.get(R.string.ui_interface_view_only), it.ifEmpty { listOf(strings.get(R.string.ui_none)) }.joinToString("\n")) }
        }
        is List<*> -> value.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider()
            when (entry) {
                is RouteRecord -> {
                    SummaryRow(strings.get(R.string.ui_destination), "${entry.destination}/${entry.prefixLength}")
                    SummaryRow(strings.get(R.string.ui_gateway), entry.gateway ?: strings.get(R.string.ui_none))
                    SummaryRow(strings.get(R.string.ui_interfaces), entry.interfaceName ?: strings.get(R.string.ui_unspecified))
                    entry.type?.let { SummaryRow(strings.get(R.string.ui_type), when (it) { 1 -> strings.get(R.string.ui_unicast); 7 -> strings.get(R.string.ui_unreachable); 9 -> strings.get(R.string.ui_continue_with_later_rules); else -> it.toString() }) }
                }
                is RegistrationView -> {
                    Text("${when (entry.domain) { 1 -> strings.get(R.string.ui_voice_cs); 2 -> strings.get(R.string.ui_data_ps); 3 -> "CS / PS"; else -> strings.get(R.string.ui_domain_1_s ,entry.domain) }} · ${when(entry.transportType) { 1 -> strings.get(R.string.ui_cellular_wwan); 2 -> "Wi-Fi WLAN"; else -> strings.get(R.string.ui_transport_1_s ,entry.transportType) }}", style = MaterialTheme.typography.titleSmall)
                    SummaryRow(strings.get(R.string.ui_registration), if (entry.registered) strings.get(R.string.ui_registered) else strings.get(R.string.ui_not_registered))
                    SummaryRow("PLMN", entry.registeredPlmn ?: strings.get(R.string.ui_no_data))
                    SummaryRow(strings.get(R.string.ui_roaming), presentation.text(entry.roaming))
                    SummaryRow(strings.get(R.string.ui_network_technology), presentation.radioType(entry.accessNetworkTechnology))
                }
                is RadioSignal -> {
                    Text(entry.technology.removePrefix("CellSignalStrength"), style = MaterialTheme.typography.titleSmall)
                    SummaryRow(strings.get(R.string.ui_signal), entry.dbm?.let { "${it} dBm" } ?: strings.get(R.string.ui_no_data))
                    SummaryRow(strings.get(R.string.ui_level), entry.level.toString())
                    SummaryRow("ASU", presentation.text(entry.asuLevel))
                }
                else -> SelectionContainer { Text(presentation.text(entry)) }
            }
        }
        else -> SelectionContainer { Text(presentation.text(value)) }
    }
}

@Composable
private fun CopyableIp(label: String, address: String) {
    val strings = rememberUiStrings()
    val context = LocalContext.current
    SummaryRow(label, address)
    TextButton(onClick = { copyProbeText(context, label, address, true) }) { Text(strings.get(R.string.ui_copy_1_s ,label)) }
}

@Composable
private fun EndpointSummary(value: EndpointResult, reveal: Boolean) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    SummaryRow(strings.get(R.string.ui_result), presentation.endpointState(value.state))
    value.httpStatus?.let { SummaryRow("HTTP", it.toString()) }
    val ip = value.cloudflare?.observedIp ?: value.reply?.observedIp
    if (ip != null) { if (reveal) CopyableIp(strings.get(R.string.ui_public_ip), ip) else SummaryRow(strings.get(R.string.ui_public_ip), strings.get(R.string.ui_hidden)) }
    value.localAddress?.let { if (reveal) CopyableIp(strings.get(R.string.ui_local_ip), it) else SummaryRow(strings.get(R.string.ui_local_ip), strings.get(R.string.ui_hidden)) }
    if (reveal) value.cloudflare?.let { trace ->
        trace.colo?.let { SummaryRow(strings.get(R.string.ui_cloudflare_location), it) }
        trace.tls?.let { SummaryRow("TLS", it) }
        trace.warp?.let { SummaryRow("WARP", it) }
    }
}
