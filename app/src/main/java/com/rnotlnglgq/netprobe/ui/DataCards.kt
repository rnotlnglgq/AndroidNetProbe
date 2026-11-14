package com.rnotlnglgq.netprobe.ui

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
    var expanded by rememberSaveable(fields.joinToString { it.path }) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起技术详情" else "技术详情") }
    if (expanded) fields.forEach { field ->
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(field.path, style = MaterialTheme.typography.labelLarge)
                Text("${field.status} · 字段权限：${field.permission}", style = MaterialTheme.typography.bodySmall)
                if (field.acquisitionPermissions.isNotEmpty()) Text("取得前置对象：${field.acquisitionPermissions.joinToString(" + ")}", style = MaterialTheme.typography.bodySmall)
                Text(field.source, style = MaterialTheme.typography.bodySmall)
                field.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                field.errorType?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        CopyableValue(field, reveal, onCopied)
        HorizontalDivider()
    }
}

@Composable
fun AddressSummary(address: AddressRecord, reveal: Boolean, onCopied: (String) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(address.family, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(Presentation.addressType(address), style = MaterialTheme.typography.labelLarge)
            Text(if (address.prefixLength >= 0) "/${address.prefixLength}" else "前缀未知", style = MaterialTheme.typography.labelLarge)
        }
        SelectionContainer {
            Text(if (reveal) address.address else "••••", fontFamily = FontFamily.Monospace,
                modifier = Modifier.horizontalScroll(rememberScrollState()))
        }
        if (address.temporary == true || address.deprecated == true) {
            Text(listOfNotNull(if (address.temporary == true) "临时地址" else null,
                if (address.deprecated == true) "已弃用" else null).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        if (reveal) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { copyProbeText(context, "IP", address.address, true); onCopied("已复制地址") }) { Text("复制地址") }
            TextButton(enabled = address.prefixLength >= 0, onClick = { copyProbeText(context, "CIDR", "${address.address}/${address.prefixLength}", true); onCopied("已复制 CIDR") }) { Text("复制 CIDR") }
        }
    }
}

@Composable
fun DataFieldCard(field: ProbeField<*>, reveal: Boolean, onCopied: (String) -> Unit) {
    Panel(Presentation.label(field.path), PermissionLabels.forField(field)) {
        val addresses = Presentation.addresses(field.value)
        when {
            addresses.isNotEmpty() -> addresses.forEachIndexed { i, address ->
                if (i > 0) HorizontalDivider()
                AddressSummary(address, !field.sensitive || reveal, onCopied)
            }
            field.status != FieldStatus.available -> Text(Presentation.status(field.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
            field.value is EndpointResult -> EndpointSummary(field.value, !field.sensitive || reveal)
            field.sensitive && !reveal -> Text("已隐藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> if (field.value is String || field.value is Number || field.value is Boolean || field.value == null)
                SelectionContainer { Text(Presentation.fieldText(field)) } else StructuredValue(field.value)
        }
        FieldDetails(listOf(field), reveal, onCopied)
    }
}

@Composable
private fun StructuredValue(value: Any?) {
    when (value) {
        is EndpointResult -> EndpointSummary(value, true)
        is ProxyRecord -> {
            SummaryRow("主机", value.host ?: "无")
            SummaryRow("端口", value.port.toString())
            if (value.exclusions.isNotEmpty()) SummaryRow("例外", value.exclusions.joinToString("、"))
            value.pacUrl?.takeIf { it.isNotBlank() }?.let { SummaryRow("PAC", it) }
        }
        is PrefixRecord -> SelectionContainer { Text("${value.address}/${value.prefixLength}", fontFamily = FontFamily.Monospace) }
        is InterfaceComparison -> {
            SummaryRow("默认接口可见", Presentation.text(value.defaultInterfaceVisible))
            SummaryRow("共有地址数", Presentation.text(value.sharedAddressCount))
            value.linkOnlyAddresses?.let { SummaryRow("仅链路视图", it.ifEmpty { listOf("无") }.joinToString("\n")) }
            value.javaOnlyAddresses?.let { SummaryRow("仅接口视图", it.ifEmpty { listOf("无") }.joinToString("\n")) }
        }
        is List<*> -> value.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider()
            when (entry) {
                is RouteRecord -> {
                    SummaryRow("目标", "${entry.destination}/${entry.prefixLength}")
                    SummaryRow("网关", entry.gateway ?: "无")
                    SummaryRow("接口", entry.interfaceName ?: "未指定")
                    entry.type?.let { SummaryRow("类型", when (it) { 1 -> "单播"; 7 -> "不可达"; 9 -> "交由后续规则"; else -> it.toString() }) }
                }
                is RegistrationView -> {
                    Text("${when (entry.domain) { 1 -> "语音 CS"; 2 -> "数据 PS"; 3 -> "CS / PS"; else -> "域 ${entry.domain}" }} · ${when(entry.transportType) { 1 -> "蜂窝 WWAN"; 2 -> "Wi-Fi WLAN"; else -> "传输 ${entry.transportType}" }}", style = MaterialTheme.typography.titleSmall)
                    SummaryRow("注册", if (entry.registered) "已注册" else "未注册")
                    SummaryRow("PLMN", entry.registeredPlmn ?: "无数据")
                    SummaryRow("漫游", Presentation.text(entry.roaming))
                    SummaryRow("网络制式", Presentation.radioType(entry.accessNetworkTechnology))
                }
                is RadioSignal -> {
                    Text(entry.technology.removePrefix("CellSignalStrength"), style = MaterialTheme.typography.titleSmall)
                    SummaryRow("信号", entry.dbm?.let { "$it dBm" } ?: "无数据")
                    SummaryRow("等级", entry.level.toString())
                    SummaryRow("ASU", Presentation.text(entry.asuLevel))
                }
                else -> SelectionContainer { Text(Presentation.text(entry)) }
            }
        }
        else -> SelectionContainer { Text(Presentation.text(value)) }
    }
}

@Composable
private fun CopyableIp(label: String, address: String) {
    val context = LocalContext.current
    SummaryRow(label, address)
    TextButton(onClick = { copyProbeText(context, label, address, true) }) { Text("复制$label") }
}

@Composable
private fun EndpointSummary(value: EndpointResult, reveal: Boolean) {
    SummaryRow("结果", Presentation.endpointState(value.state))
    value.httpStatus?.let { SummaryRow("HTTP", it.toString()) }
    val ip = value.cloudflare?.observedIp ?: value.reply?.observedIp
    if (ip != null) { if (reveal) CopyableIp("出口 IP", ip) else SummaryRow("出口 IP", "已隐藏") }
    value.localAddress?.let { if (reveal) CopyableIp("本地 IP", it) else SummaryRow("本地 IP", "已隐藏") }
    if (reveal) value.cloudflare?.let { trace ->
        trace.colo?.let { SummaryRow("Cloudflare 节点", it) }
        trace.tls?.let { SummaryRow("TLS", it) }
        trace.warp?.let { SummaryRow("WARP", it) }
    }
}
