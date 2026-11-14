package com.rnotlnglgq.netprobe.ui

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
    Panel(title) {
        val summary = listOf("displayName", "carrierName", "mcc", "mnc", "countryIso", "embedded", "slotIndex")
        summary.forEach { key -> fields.firstOrNull { it.path.endsWith(".$key") }?.let { field ->
            SummaryRow(Presentation.label(field.path), when {
                field.status != FieldStatus.available -> Presentation.status(field.status)
                field.sensitive && !reveal -> "已隐藏"
                else -> Presentation.fieldText(field)
            })
        } }
        FieldDetails(fields, reveal, onCopied)
    }
}

@Composable
fun CurrentSubscriptionsSection(fields: List<ProbeField<*>>, reveal: Boolean, onCopied: (String) -> Unit) {
    val groups = Presentation.indexedGroups(fields, "subscriptions", "订阅")
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = Presentation.selected(groups, selectedKey)
    LaunchedEffect(selected?.key) { selectedKey = selected?.key }
    if (selected == null) {
        fields.filter { it.path.startsWith("subscriptions.") }.forEach { DataFieldCard(it, reveal, onCopied) }
        return
    }
    ItemTabs(groups.map { it.title }, groups.indexOf(selected)) { selectedKey = groups[it].key }
    SubscriptionSummary(selected.title, selected.fields.filterNot { ".serviceState." in it.path || ".signalStrength." in it.path }, reveal, onCopied)
    var network by rememberSaveable(selected.key) { mutableStateOf(false) }
    TextButton(onClick = { network = !network }) { Text(if (network) "收起网络与服务状态" else "网络与服务状态") }
    if (network) selected.fields.filter {
        ".serviceState." in it.path || ".signalStrength." in it.path || it.path.endsWith(".dataNetworkType") || it.path.endsWith(".dataState") ||
            it.path.endsWith(".registeredOperatorName") || it.path.endsWith(".registeredMccMnc")
    }.forEach { DataFieldCard(it, reveal, onCopied) }
}

@Composable
fun SubscriptionHistorySection(document: SubscriptionHistoryDocument, busy: Boolean, enabled: Boolean, reveal: Boolean,
    onRead: () -> Unit, onPermission: () -> Unit, onCopied: (String) -> Unit) {
    PermissionCaption(PermissionLabels.PHONE)
    Text("系统保留的当前及曾插入的 SIM 记录。", style = MaterialTheme.typography.bodySmall)
    val count = document.fields.firstOrNull { it.path == "subscriptionHistory.count" }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRead, enabled = enabled && !busy && count?.status != FieldStatus.unsupported_api) { Text(if (document.capturedAtUtc == null) "读取记录" else "重新读取") }
        if (!enabled || count?.status == FieldStatus.permission_denied) TextButton(onClick = onPermission) { Text("授权与设置") }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
    }
    if (count?.status != FieldStatus.available) {
        Text(if (count?.status == FieldStatus.not_requested) "尚未读取" else if (count?.status == FieldStatus.unsupported_api) "需要 Android 14 或以上" else count?.let { Presentation.status(it.status) } ?: "无数据")
        FieldDetails(document.fields, reveal, onCopied)
        return
    }
    Text("${count.value} 条记录")
    document.completedAtUtc?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    val groups = Presentation.indexedGroups(document.fields, "subscriptionHistory", "记录")
    var page by rememberSaveable(document.capturedAtUtc) { mutableIntStateOf(0) }
    val pageCount = ((groups.size + 19) / 20).coerceAtLeast(1)
    val currentPage = page.coerceIn(0, pageCount - 1)
    groups.drop(currentPage * 20).take(20).forEach { SubscriptionSummary(it.title, it.fields, reveal, onCopied) }
    if (pageCount > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { page = currentPage - 1 }, enabled = currentPage > 0) { Text("上一页") }
        Text("${currentPage + 1} / $pageCount", modifier = Modifier.padding(top = 12.dp))
        OutlinedButton(onClick = { page = currentPage + 1 }, enabled = currentPage + 1 < pageCount) { Text("下一页") }
    }
}
