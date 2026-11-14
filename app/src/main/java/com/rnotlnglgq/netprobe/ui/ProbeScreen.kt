package com.rnotlnglgq.netprobe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rnotlnglgq.netprobe.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ProbeScreen(model: ProbeViewModel, onPermissionRequest: (String) -> Unit, onSettings: () -> Unit, onExport: (Boolean) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied: (String) -> Unit = { message -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) } }
    var jsonPreview by remember { mutableStateOf<JsonPreview?>(null) }
    var endpointConfig by remember { mutableStateOf(false) }
    var permissions by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("overview") }
    val tabs = linkedMapOf("overview" to "概览", "interfaces" to "接口", "native" to "Native 接口", "addresses" to "IP 地址", "dns" to "DNS 与路由",
        "wifi" to "Wi-Fi", "telephony" to "运营商", "subscriptions" to "订阅", "external" to "公网", "properties" to "系统属性", "permissions" to "权限", "device" to "设备")
    val pageState = rememberSaveableStateHolder()
    val fields = model.snapshot?.fieldResults.orEmpty()
    val defaultInterface = fields.firstOrNull { it.path == "defaultNetwork.interfaceName" }?.value as? String
    val time = model.snapshot?.capturedAtUtc?.let { runCatching {
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(it))
    }.getOrDefault(it) } ?: "等待采样"
    LaunchedEffect(model.foreground) { if (!model.foreground) jsonPreview = null }
    LaunchedEffect(model.message) { if (model.message.isNotEmpty()) copied(model.message) }
    jsonPreview?.let { JsonViewer(it, onDismiss = { jsonPreview = null }, onCopied = {}) }
    if (endpointConfig) EndpointScreen(model, onDismiss = { endpointConfig = false })
    if (permissions) PermissionScreen(model, onDismiss = { permissions = false }, onRequest = onPermissionRequest, onSettings = onSettings)
    if (exporting) ExportScreen(onDismiss = { exporting = false }) { raw -> exporting = false; onExport(raw) }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Surface(shadowElevation = 2.dp) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text("NetProbe", style = MaterialTheme.typography.titleLarge)
                        Text("${if (model.registered) "监测中" else "已暂停"} · $time", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { model.reveal = !model.reveal }) { Text(if (model.reveal) "隐藏完整值" else "显示完整值") }
                    Box {
                        TextButton(onClick = { menu = true }) { Text("更多") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("查看 / 复制 JSON") }, enabled = model.snapshot != null,
                                onClick = { menu = false; model.snapshot?.let { jsonPreview = JsonPreview.of(it, model.events, model.comparison) } })
                            DropdownMenuItem(text = { Text("导出 JSON") }, enabled = model.snapshot != null, onClick = { menu = false; exporting = true })
                            DropdownMenuItem(text = { Text("权限与对照") }, onClick = { menu = false; permissions = true })
                            DropdownMenuItem(text = { Text("公网检测") }, onClick = { menu = false; endpointConfig = true })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = { when (page) { "properties" -> model.readSystemProperties(); "native" -> model.readNativeInterfaces(); else -> model.refresh() } },
                        enabled = when (page) { "properties" -> !model.propertiesBusy; "native" -> !model.nativeBusy; else -> !model.busy }) { Text("刷新") }
                    TextButton(onClick = model::toggleMonitoring) { Text(if (model.monitoring) "暂停监测" else "继续监测") }
                    if (model.externalBusy) Text("公网检测中…", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
                ScrollableTabRow(selectedTabIndex = tabs.keys.indexOf(page).coerceAtLeast(0), edgePadding = 8.dp) {
                    tabs.forEach { (key, label) -> Tab(selected = page == key, onClick = { page = key }, text = { Text(label) }) }
                }
                if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }) { inset ->
        pageState.SaveableStateProvider(page) {
            LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (page) {
                            "overview" -> {
                                val transport = fields.firstOrNull { it.path == "defaultNetwork.transports" }
                                Panel("当前网络") {
                                    Text(transport?.let { if (it.status == FieldStatus.available) Presentation.text(it.value) else Presentation.status(it.status) } ?: "等待采样", style = MaterialTheme.typography.headlineSmall)
                                    defaultInterface?.let { SummaryRow("接口", it) }
                                }
                                fields.filter { it.path in setOf("defaultNetwork.validated", "defaultNetwork.metered", "defaultNetwork.vpn") }
                                    .forEach { DataFieldCard(it, model.reveal, copied) }
                                fields.firstOrNull { it.path == "defaultNetwork.linkAddresses" }?.let { DataFieldCard(it, model.reveal, copied) }
                                var more by rememberSaveable { mutableStateOf(false) }
                                TextButton(onClick = { more = !more }) { Text(if (more) "收起网络属性" else "更多网络属性") }
                                if (more) fields.filter { it.path.startsWith("defaultNetwork.") && it.path !in dnsPaths && it.path !in setOf("defaultNetwork.linkAddresses", "defaultNetwork.validated", "defaultNetwork.metered", "defaultNetwork.vpn", "defaultNetwork.transports") }
                                    .forEach { DataFieldCard(it, model.reveal, copied) }
                            }
                            "interfaces" -> InterfaceSection(fields.filter { it.path.startsWith("visibleInterfaces") }, defaultInterface, model.reveal, copied)
                            "native" -> NativeInterfacesSection(model.nativeDocument, model.nativeBusy,
                                model.reveal, model::readNativeInterfaces, copied)
                            "addresses" -> {
                                var family by rememberSaveable { mutableIntStateOf(0) }
                                ItemTabs(listOf("IPv4", "IPv6"), family) { family = it }
                                fields.firstOrNull { it.path == "${if (family == 0) "ipv4" else "ipv6"}.defaultNetworkAddresses" }
                                    ?.let { DataFieldCard(it, model.reveal, copied) } ?: Text("等待采样")
                            }
                            "dns" -> {
                                var section by rememberSaveable { mutableIntStateOf(0) }
                                ItemTabs(listOf("DNS", "路由", "代理"), section) { section = it }
                                fields.filter { when (section) {
                                    1 -> it.path == "defaultNetwork.routes"
                                    2 -> it.path == "defaultNetwork.httpProxy"
                                    else -> it.path in dnsPaths && it.path !in setOf("defaultNetwork.routes", "defaultNetwork.httpProxy")
                                } }.forEach { DataFieldCard(it, model.reveal, copied) }
                            }
                            "wifi" -> {
                                Panel("Wi-Fi 读取", PermissionLabels.WIFI) {
                                    Row { Text("读取连接信息", Modifier.weight(1f)); Switch(model.wifiExperiment, { model.setExperiment("wifi", it) }) }
                                    if (fields.any { it.path.startsWith("wifi.") && it.status == FieldStatus.permission_denied }) {
                                        Text("需要精确位置权限")
                                        Button(onClick = { if (model.beginExperiment("wifi")) onPermissionRequest("wifi") }) { Text("授权") }
                                    }
                                    if (fields.any { it.path == "wifi.locationEnabled" && it.value == false }) Text("请打开系统位置开关")
                                    TextButton(onClick = onSettings) { Text("应用权限设置") }
                                }
                                fields.filter { it.path.startsWith("wifi.") }.forEach { DataFieldCard(it, model.reveal, copied) }
                            }
                            "telephony" -> fields.filter { it.path.startsWith("telephony.") }.forEach { DataFieldCard(it, model.reveal, copied) }
                            "subscriptions" -> {
                                Panel("订阅读取", PermissionLabels.PHONE) {
                                    Row { Text("读取 SIM 与服务状态", Modifier.weight(1f)); Switch(model.phoneExperiment, { model.setExperiment("phone", it) }) }
                                    TextButton(onClick = { permissions = true }) { Text("授权与对照") }
                                }
                                var section by rememberSaveable { mutableIntStateOf(0) }
                                ItemTabs(listOf("当前订阅", "历史记录"), section) { section = it }
                                if (section == 0) CurrentSubscriptionsSection(fields, model.reveal, copied)
                                else SubscriptionHistorySection(model.historyDocument, model.historyBusy, model.phoneExperiment,
                                    model.reveal, model::readSubscriptionHistory, { permissions = true }, copied)

                            }
                            "external" -> {
                                Button(onClick = { endpointConfig = true }, enabled = !model.externalBusy) { Text("公网 / Cloudflare") }
                                fields.filter { it.path.startsWith("externalChecks.") && !it.path.endsWith(".state") }.forEach { DataFieldCard(it, model.reveal, copied) }
                                if (fields.none { it.path.startsWith("externalChecks.") && it.path.endsWith(".result") }) Text("尚未检测")
                            }
                            "properties" -> SystemPropertiesSection(model.propertyDocument, model.propertiesBusy,
                                model.reveal, model::readSystemProperties, copied)
                            "permissions" -> {
                                Button(onClick = { permissions = true }) { Text("权限与对照") }
                                fields.filter { it.path.startsWith("permissions.") }.forEach { DataFieldCard(it, model.reveal, copied) }
                            }
                            "device" -> {
                                model.snapshot?.let { snapshot ->
                                    Panel("设备") {
                                        SummaryRow("型号", "${snapshot.environment.manufacturer} ${snapshot.environment.model}")
                                        SummaryRow("Android API", snapshot.environment.androidApi.toString())
                                        SummaryRow("应用版本", snapshot.environment.appVersion)
                                    }
                                    var details by rememberSaveable { mutableStateOf(false) }
                                    TextButton(onClick = { details = !details }) { Text(if (details) "收起采样记录" else "采样记录") }
                                    if (details) Panel("采样记录") {
                                        SummaryRow("采样时间", snapshot.capturedAtUtc)
                                        SummaryRow("完成时间", snapshot.completedAtUtc)
                                        SummaryRow("target SDK", snapshot.environment.targetSdk.toString())
                                        snapshot.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        model.events.takeLast(10).forEach { Text("${it.capturedAtUtc} · ${it.event}", style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                                fields.filter { it.path.startsWith("features.") }.forEach { DataFieldCard(it, model.reveal, copied) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val dnsPaths = setOf("defaultNetwork.dnsServers", "defaultNetwork.routes", "defaultNetwork.privateDns.active",
    "defaultNetwork.privateDns.serverName", "defaultNetwork.nat64Prefix", "defaultNetwork.dnsSearchDomains", "defaultNetwork.httpProxy")
