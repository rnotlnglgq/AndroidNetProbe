package com.rnotlnglgq.netprobe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rnotlnglgq.netprobe.model.FieldStatus
import com.rnotlnglgq.netprobe.probe.BionicDocument
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NativeInterfacesSection(document: BionicDocument, busy: Boolean, reveal: Boolean,
    onRead: () -> Unit, onCopied: (String) -> Unit) {
    Text("测试功能：通过 Bionic 读取接口信息。", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onRead, enabled = !busy) { Text(if (document.capturedAtUtc == null) "读取接口" else "重新读取") }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
    }
    document.completedAtUtc?.let { time ->
        val display = runCatching { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(time)) }.getOrDefault(time)
        val status = document.fields.firstOrNull { it.path == "nativeInterfaces.count" }?.status
        Text(if (status == FieldStatus.available || status == FieldStatus.empty) "${document.interfaces.size} 个接口 · $display" else "读取时间 $display")
    }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = document.interfaces.firstOrNull { it.name == selectedName } ?: document.interfaces.firstOrNull()
    LaunchedEffect(selected?.name) { selectedName = selected?.name }
    if (selected == null) {
        val count = document.fields.firstOrNull { it.path == "nativeInterfaces.count" }
        Text(when (count?.status) {
            FieldStatus.not_requested -> "尚未读取"
            FieldStatus.empty, FieldStatus.available -> "无可见接口"
            null -> "无数据"
            else -> Presentation.status(count.status)
        })
        FieldDetails(document.fields, reveal, onCopied)
        return
    }
    ItemTabs(document.interfaces.map { it.name }, document.interfaces.indexOf(selected)) { selectedName = document.interfaces[it].name }
    val prefix = "nativeInterfaces[${document.interfaces.indexOf(selected)}]."
    Panel(selected.name) {
        SummaryRow("已启用", Presentation.text(selected.up))
        SummaryRow("回环", Presentation.text(selected.loopback))
        Text("IPv4 ${selected.addresses.count { it.family == "IPv4" }} · IPv6 ${selected.addresses.count { it.family == "IPv6" }}")
    }
    if (selected.addresses.isEmpty()) Text("无 IP 地址")
    selected.addresses.forEachIndexed { i, address ->
        Panel("IP 地址 ${i + 1}") { AddressSummary(address.display(), reveal, onCopied) }
    }
    FieldDetails(document.fields.filter { it.path.startsWith(prefix) }, reveal, onCopied)
}
