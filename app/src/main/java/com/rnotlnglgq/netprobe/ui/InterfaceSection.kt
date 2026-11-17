package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rnotlnglgq.netprobe.model.*

@Composable
fun ItemTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {

    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        titles.forEachIndexed { index, title ->
            FilterChip(selected = index == selected, onClick = { onSelect(index) }, label = { Text(title) })
        }
    }
}

@Composable
fun InterfaceSection(fields: List<ProbeField<*>>, defaultInterface: String?, reveal: Boolean, onCopied: (String) -> Unit) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    val groups = presentation.indexedGroups(fields, "visibleInterfaces", strings.get(R.string.ui_interfaces))
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = presentation.selected(groups, selectedKey)
    LaunchedEffect(selected?.key) { selectedKey = selected?.key }
    if (selected == null) {
        val count = fields.firstOrNull { it.path == "visibleInterfaces.count" }
        Text(count?.let { presentation.status(it.status) } ?: strings.get(R.string.ui_waiting_for_a_sample))
        FieldDetails(fields, reveal, onCopied)
        return
    }
    ItemTabs(groups.map { it.title }, groups.indexOf(selected)) { selectedKey = groups[it].key }
    val addressField = selected.fields.firstOrNull { it.path.endsWith(".addresses") }
    val addresses = presentation.addresses(addressField?.value)
    fun field(name: String) = selected.fields.firstOrNull { it.path.endsWith(".${name}") }
    fun value(name: String): String = field(name)?.let { if (it.status == FieldStatus.available) presentation.text(it.value) else presentation.status(it.status) } ?: strings.get(R.string.ui_no_data)
    Panel(selected.title) {
        val tags = listOfNotNull(if (selected.title == defaultInterface) strings.get(R.string.ui_default_network) else null,
            if (field("loopback")?.value == true) strings.get(R.string.ui_loopback) else null).joinToString(" · ")
        if (tags.isNotEmpty()) Text(tags, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) { SummaryRow(strings.get(R.string.ui_enabled), value("up")) }
            Column(Modifier.weight(1f)) { SummaryRow("MTU", value("mtu")) }
        }
        if (addressField?.status in listOf(FieldStatus.available, FieldStatus.empty)) {
            Text("IPv4 ${addresses.count { it.family == "IPv4" }} · IPv6 ${addresses.count { it.family == "IPv6" }}")
        } else Text(strings.get(R.string.ui_address_1_s ,addressField?.let { presentation.status(it.status) } ?: strings.get(R.string.ui_no_data)))
        FieldDetails(selected.fields.filter { it != addressField }, reveal, onCopied)
    }
    if (addresses.isEmpty()) {
        Panel(strings.get(R.string.ui_ip_addresses)) { Text(addressField?.let { if (it.status == FieldStatus.available) strings.get(R.string.ui_no_address) else presentation.status(it.status) } ?: strings.get(R.string.ui_no_data))
            addressField?.let { FieldDetails(listOf(it), reveal, onCopied) } }
    } else {
        addresses.forEachIndexed { index, address -> Panel(strings.get(R.string.ui_ip_address_1_s ,index + 1)) { AddressSummary(address, reveal, onCopied) } }
        addressField?.let { FieldDetails(listOf(it), reveal, onCopied) }
    }
    fields.firstOrNull { it.path == "visibleInterfaces.crossCheck" }?.let { field ->
        var compare by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { compare = !compare }) { Text(if (compare) strings.get(R.string.ui_collapse_address_comparison) else strings.get(R.string.ui_address_comparison)) }
        if (compare) DataFieldCard(field, reveal, onCopied)
    }
}
