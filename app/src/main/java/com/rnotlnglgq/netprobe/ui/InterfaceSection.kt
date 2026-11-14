package com.rnotlnglgq.netprobe.ui

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
    val groups = Presentation.indexedGroups(fields, "visibleInterfaces", "接口")
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = Presentation.selected(groups, selectedKey)
    LaunchedEffect(selected?.key) { selectedKey = selected?.key }
    if (selected == null) {
        val count = fields.firstOrNull { it.path == "visibleInterfaces.count" }
        Text(count?.let { Presentation.status(it.status) } ?: "等待采样")
        FieldDetails(fields, reveal, onCopied)
        return
    }
    ItemTabs(groups.map { it.title }, groups.indexOf(selected)) { selectedKey = groups[it].key }
    val addressField = selected.fields.firstOrNull { it.path.endsWith(".addresses") }
    val addresses = Presentation.addresses(addressField?.value)
    fun field(name: String) = selected.fields.firstOrNull { it.path.endsWith(".$name") }
    fun value(name: String): String = field(name)?.let { if (it.status == FieldStatus.available) Presentation.text(it.value) else Presentation.status(it.status) } ?: "无数据"
    Panel(selected.title) {
        val tags = listOfNotNull(if (selected.title == defaultInterface) "默认网络" else null,
            if (field("loopback")?.value == true) "回环" else null).joinToString(" · ")
        if (tags.isNotEmpty()) Text(tags, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f)) { SummaryRow("已启用", value("up")) }
            Column(Modifier.weight(1f)) { SummaryRow("MTU", value("mtu")) }
        }
        if (addressField?.status in listOf(FieldStatus.available, FieldStatus.empty)) {
            Text("IPv4 ${addresses.count { it.family == "IPv4" }} · IPv6 ${addresses.count { it.family == "IPv6" }}")
        } else Text("地址：${addressField?.let { Presentation.status(it.status) } ?: "无数据"}")
        FieldDetails(selected.fields.filter { it != addressField }, reveal, onCopied)
    }
    if (addresses.isEmpty()) {
        Panel("IP 地址") { Text(addressField?.let { if (it.status == FieldStatus.available) "无地址" else Presentation.status(it.status) } ?: "无数据")
            addressField?.let { FieldDetails(listOf(it), reveal, onCopied) } }
    } else {
        addresses.forEachIndexed { index, address -> Panel("IP 地址 ${index + 1}") { AddressSummary(address, reveal, onCopied) } }
        addressField?.let { FieldDetails(listOf(it), reveal, onCopied) }
    }
    fields.firstOrNull { it.path == "visibleInterfaces.crossCheck" }?.let { field ->
        var compare by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { compare = !compare }) { Text(if (compare) "收起地址对照" else "地址对照") }
        if (compare) DataFieldCard(field, reveal, onCopied)
    }
}
