package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.SubscriptionHistoryDocument

@Composable
fun SubscriptionSummary(title: String, fields: List<ProbeField<*>>, reveal: Boolean, onCopied: (String) -> Unit) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    Panel(title) {
        val summary = listOf("displayName", "carrierName", "mcc", "mnc", "countryIso", "embedded", "slotIndex")
        summary.forEach { key -> fields.firstOrNull { it.path.endsWith(".${key}") }?.let { field ->
            SummaryRow(presentation.label(field.path), when {
                field.status != FieldStatus.available -> presentation.status(field.status)
                field.sensitive && !reveal -> strings.get(R.string.ui_hidden)
                else -> presentation.fieldText(field)
            })
        } }
        FieldDetails(fields, reveal, onCopied)
    }
}

@Composable
fun CurrentSubscriptionsSection(fields: List<ProbeField<*>>, reveal: Boolean, onCopied: (String) -> Unit) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    val groups = presentation.indexedGroups(fields, "subscriptions", strings.get(R.string.ui_subscriptions))
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = presentation.selected(groups, selectedKey)
    LaunchedEffect(selected?.key) { selectedKey = selected?.key }
    if (selected == null) {
        fields.filter { it.path.startsWith("subscriptions.") }.forEach { DataFieldCard(it, reveal, onCopied) }
        return
    }
    ItemTabs(groups.map { it.title }, groups.indexOf(selected)) { selectedKey = groups[it].key }
    SubscriptionSummary(selected.title, selected.fields.filterNot { ".serviceState." in it.path || ".signalStrength." in it.path }, reveal, onCopied)
    var network by rememberSaveable(selected.key) { mutableStateOf(false) }
    TextButton(onClick = { network = !network }) { Text(if (network) strings.get(R.string.ui_collapse_network_and_service_state) else strings.get(R.string.ui_network_and_service_state)) }
    if (network) selected.fields.filter {
        ".serviceState." in it.path || ".signalStrength." in it.path || it.path.endsWith(".dataNetworkType") || it.path.endsWith(".dataState") ||
            it.path.endsWith(".registeredOperatorName") || it.path.endsWith(".registeredMccMnc")
    }.forEach { DataFieldCard(it, reveal, onCopied) }
}

@Composable
fun SubscriptionHistorySection(document: SubscriptionHistoryDocument, busy: Boolean, enabled: Boolean, reveal: Boolean,
    onRead: () -> Unit, onPermission: () -> Unit, onCopied: (String) -> Unit) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    val permissionLabels = androidx.compose.runtime.remember(strings) { PermissionLabels(strings) }
    PermissionCaption(permissionLabels.PHONE)
    Text(strings.get(R.string.ui_current_and_previously_inserted_sim_records_retained_by_the_syste), style = MaterialTheme.typography.bodySmall)
    val count = document.fields.firstOrNull { it.path == "subscriptionHistory.count" }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRead, enabled = enabled && !busy && count?.status != FieldStatus.unsupported_api) { Text(if (document.capturedAtUtc == null) strings.get(R.string.ui_read_records) else strings.get(R.string.ui_read_again)) }
        if (!enabled || count?.status == FieldStatus.permission_denied) TextButton(onClick = onPermission) { Text(strings.get(R.string.ui_permissions_and_settings)) }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
    }
    if (count?.status != FieldStatus.available) {
        Text(if (count?.status == FieldStatus.not_requested) strings.get(R.string.ui_not_read_yet) else if (count?.status == FieldStatus.unsupported_api) strings.get(R.string.ui_requires_android_14_or_later) else count?.let { presentation.status(it.status) } ?: strings.get(R.string.ui_no_data))
        FieldDetails(document.fields, reveal, onCopied)
        return
    }
    Text(strings.quantity(R.plurals.record_count,(count.value as? Number)?.toInt() ?: 0))
    document.completedAtUtc?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    val groups = presentation.indexedGroups(document.fields, "subscriptionHistory", strings.get(R.string.ui_record))
    var page by rememberSaveable(document.capturedAtUtc) { mutableIntStateOf(0) }
    val pageCount = ((groups.size + 19) / 20).coerceAtLeast(1)
    val currentPage = page.coerceIn(0, pageCount - 1)
    groups.drop(currentPage * 20).take(20).forEach { SubscriptionSummary(it.title, it.fields, reveal, onCopied) }
    if (pageCount > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { page = currentPage - 1 }, enabled = currentPage > 0) { Text(strings.get(R.string.ui_previous_page)) }
        Text("${currentPage + 1} / ${pageCount}", modifier = Modifier.padding(top = 12.dp))
        OutlinedButton(onClick = { page = currentPage + 1 }, enabled = currentPage + 1 < pageCount) { Text(strings.get(R.string.ui_next_page)) }
    }
}
