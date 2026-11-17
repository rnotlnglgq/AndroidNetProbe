package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rnotlnglgq.netprobe.model.FieldStatus
import com.rnotlnglgq.netprobe.probe.BionicDocument

@Composable
fun NativeInterfacesSection(document: BionicDocument, busy: Boolean, reveal: Boolean,
    onRead: () -> Unit, onCopied: (String) -> Unit) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    Text(strings.get(R.string.ui_experimental_read_interfaces_through_bionic), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onRead, enabled = !busy) { Text(if (document.capturedAtUtc == null) strings.get(R.string.ui_read_interfaces) else strings.get(R.string.ui_read_again)) }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
    }
    document.completedAtUtc?.let { time ->
        val display = runCatching { strings.time(time) }.getOrDefault(time)
        val status = document.fields.firstOrNull { it.path == "nativeInterfaces.count" }?.status
        Text(if (status == FieldStatus.available || status == FieldStatus.empty) "${strings.quantity(R.plurals.interface_count,document.interfaces.size)} · $display" else strings.get(R.string.ui_read_at_1_s ,display))
    }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = document.interfaces.firstOrNull { it.name == selectedName } ?: document.interfaces.firstOrNull()
    LaunchedEffect(selected?.name) { selectedName = selected?.name }
    if (selected == null) {
        val count = document.fields.firstOrNull { it.path == "nativeInterfaces.count" }
        Text(when (count?.status) {
            FieldStatus.not_requested -> strings.get(R.string.ui_not_read_yet)
            FieldStatus.empty, FieldStatus.available -> strings.get(R.string.ui_no_visible_interfaces)
            null -> strings.get(R.string.ui_no_data)
            else -> presentation.status(count.status)
        })
        FieldDetails(document.fields, reveal, onCopied)
        return
    }
    ItemTabs(document.interfaces.map { it.name }, document.interfaces.indexOf(selected)) { selectedName = document.interfaces[it].name }
    val prefix = "nativeInterfaces[${document.interfaces.indexOf(selected)}]."
    Panel(selected.name) {
        SummaryRow(strings.get(R.string.ui_enabled), presentation.text(selected.up))
        SummaryRow(strings.get(R.string.ui_loopback), presentation.text(selected.loopback))
        Text("IPv4 ${selected.addresses.count { it.family == "IPv4" }} · IPv6 ${selected.addresses.count { it.family == "IPv6" }}")
    }
    if (selected.addresses.isEmpty()) Text(strings.get(R.string.ui_no_ip_address))
    selected.addresses.forEachIndexed { i, address ->
        Panel(strings.get(R.string.ui_ip_address_1_s ,i + 1)) { AddressSummary(address.display(), reveal, onCopied) }
    }
    FieldDetails(document.fields.filter { it.path.startsWith(prefix) }, reveal, onCopied)
}
