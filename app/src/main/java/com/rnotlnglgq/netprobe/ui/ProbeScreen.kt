package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rnotlnglgq.netprobe.model.*
import kotlinx.coroutines.launch

@Composable
fun ProbeScreen(model: ProbeViewModel, onPermissionRequest: (String) -> Unit, onSettings: () -> Unit, onExport: (Boolean) -> Unit) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    val permissionLabels = androidx.compose.runtime.remember(strings) { PermissionLabels(strings) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied: (String) -> Unit = { message -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) } }
    var jsonPreview by remember { mutableStateOf<JsonPreview?>(null) }
    var endpointConfig by remember { mutableStateOf(false) }
    var permissions by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var languageSettings by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("overview") }
    val tabs = linkedMapOf("overview" to strings.get(R.string.ui_overview), "interfaces" to strings.get(R.string.ui_interfaces), "native" to strings.get(R.string.ui_native_interfaces), "addresses" to strings.get(R.string.ui_ip_addresses), "dns" to strings.get(R.string.ui_dns_and_routes),
        "wifi" to "Wi-Fi", "telephony" to strings.get(R.string.ui_carrier), "subscriptions" to strings.get(R.string.ui_subscriptions), "external" to strings.get(R.string.ui_public_network), "properties" to strings.get(R.string.ui_system_properties), "permissions" to strings.get(R.string.ui_permissions), "device" to strings.get(R.string.ui_device))
    val pageState = rememberSaveableStateHolder()
    val uiState by model.state.collectAsState()
    val fields = uiState.snapshot?.fieldResults.orEmpty()
    val defaultInterface = fields.firstOrNull { it.path == FieldKeys.DEFAULT_INTERFACE }?.value as? String
    val time = model.snapshot?.capturedAtUtc?.let { runCatching {
        strings.time(it)
    }.getOrDefault(it) } ?: strings.get(R.string.ui_waiting_for_a_sample)
    LaunchedEffect(model.foreground) { if (!model.foreground) jsonPreview = null }
    LaunchedEffect(model.message?.id) { model.message?.let { copied(strings.resolve(it.text)); model.consumeMessage(it.id) } }
    jsonPreview?.let { JsonViewer(it, onDismiss = { jsonPreview = null }, onCopied = {}) }
    if (endpointConfig) EndpointScreen(model, onDismiss = { endpointConfig = false })
    if (permissions) PermissionScreen(model, onDismiss = { permissions = false }, onRequest = onPermissionRequest, onSettings = onSettings)
    if (exporting) ExportScreen(onDismiss = { exporting = false }) { raw -> exporting = false; onExport(raw) }
    if (languageSettings) LanguageSettings(onDismiss = { languageSettings = false })
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Surface(shadowElevation = 2.dp) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text("NetProbe", style = MaterialTheme.typography.titleLarge)
                        Text("${if (model.registered) strings.get(R.string.ui_monitoring) else strings.get(R.string.ui_paused)} · ${time}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { model.reveal = !model.reveal }) { Text(if (model.reveal) strings.get(R.string.ui_hide_full_values) else strings.get(R.string.ui_show_full_values)) }
                    Box {
                        TextButton(modifier = Modifier.testTag("menu-more"), onClick = { menu = true }) { Text(strings.get(R.string.ui_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(strings.get(R.string.ui_view_copy_json)) }, enabled = model.snapshot != null,
                                onClick = { menu = false; model.snapshot?.let { jsonPreview = JsonPreview.of(it, model.events, model.comparison) } })
                            DropdownMenuItem(text = { Text(strings.get(R.string.ui_export_json)) }, enabled = model.snapshot != null, onClick = { menu = false; exporting = true })
                            DropdownMenuItem(text = { Text(strings.get(R.string.ui_permissions_and_comparison)) }, onClick = { menu = false; permissions = true })
                            DropdownMenuItem(text = { Text(strings.get(R.string.ui_public_checks)) }, onClick = { menu = false; endpointConfig = true })
                            DropdownMenuItem(modifier = Modifier.testTag("settings-language"), text = { Text(strings.get(R.string.language_settings)) },
                                onClick = { menu = false; languageSettings = true })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    TextButton(onClick = { when (page) { "properties" -> model.readSystemProperties(); "native" -> model.readNativeInterfaces(); else -> model.refresh() } },
                        enabled = when (page) { "properties" -> !model.propertiesBusy; "native" -> !model.nativeBusy; else -> !model.busy }) { Text(strings.get(R.string.ui_refresh)) }
                    TextButton(onClick = model::toggleMonitoring) { Text(if (model.monitoring) strings.get(R.string.ui_pause_monitoring) else strings.get(R.string.ui_resume_monitoring)) }
                    if (model.externalBusy) Text(strings.get(R.string.ui_checking_public_network), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
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
                                val transport = fields.firstOrNull { it.path == FieldKeys.DEFAULT_TRANSPORTS }
                                Panel(strings.get(R.string.ui_current_network)) {
                                    Text(transport?.let { if (it.status == FieldStatus.available) presentation.text(it.value) else presentation.status(it.status) } ?: strings.get(R.string.ui_waiting_for_a_sample), style = MaterialTheme.typography.headlineSmall)
                                    defaultInterface?.let { SummaryRow(strings.get(R.string.ui_interfaces), it) }
                                }
                                fields.filter { it.path in setOf(FieldKeys.DEFAULT_VALIDATED, FieldKeys.DEFAULT_METERED, FieldKeys.DEFAULT_VPN) }
                                    .forEach { DataFieldCard(it, model.reveal, copied) }
                                fields.firstOrNull { it.path == FieldKeys.DEFAULT_ADDRESSES }?.let { DataFieldCard(it, model.reveal, copied) }
                                var more by rememberSaveable { mutableStateOf(false) }
                                TextButton(onClick = { more = !more }) { Text(if (more) strings.get(R.string.ui_collapse_network_properties) else strings.get(R.string.ui_more_network_properties)) }
                                if (more) fields.filter { it.path.startsWith("defaultNetwork.") && it.path !in dnsPaths && it.path !in setOf(FieldKeys.DEFAULT_ADDRESSES, FieldKeys.DEFAULT_VALIDATED, FieldKeys.DEFAULT_METERED, FieldKeys.DEFAULT_VPN, FieldKeys.DEFAULT_TRANSPORTS) }
                                    .forEach { DataFieldCard(it, model.reveal, copied) }
                            }
                            "interfaces" -> InterfaceSection(fields.filter { it.path.startsWith("visibleInterfaces") }, defaultInterface, model.reveal, copied)
                            "native" -> NativeInterfacesSection(model.nativeDocument, model.nativeBusy,
                                model.reveal, model::readNativeInterfaces, copied)
                            "addresses" -> {
                                var family by rememberSaveable { mutableIntStateOf(0) }
                                ItemTabs(listOf("IPv4", "IPv6"), family) { family = it }
                                fields.firstOrNull { it.path == "${if (family == 0) "ipv4" else "ipv6"}.defaultNetworkAddresses" }
                                    ?.let { DataFieldCard(it, model.reveal, copied) } ?: Text(strings.get(R.string.ui_waiting_for_a_sample))
                            }
                            "dns" -> {
                                var section by rememberSaveable { mutableIntStateOf(0) }
                                ItemTabs(listOf("DNS", strings.get(R.string.ui_routes), strings.get(R.string.ui_proxy)), section) { section = it }
                                fields.filter { when (section) {
                                    1 -> it.path == "defaultNetwork.routes"
                                    2 -> it.path == "defaultNetwork.httpProxy"
                                    else -> it.path in dnsPaths && it.path !in setOf("defaultNetwork.routes", "defaultNetwork.httpProxy")
                                } }.forEach { DataFieldCard(it, model.reveal, copied) }
                            }
                            "wifi" -> {
                                Panel(strings.get(R.string.ui_wi_fi_reading), permissionLabels.WIFI) {
                                    Row { Text(strings.get(R.string.ui_read_connection_information), Modifier.weight(1f)); Switch(model.wifiExperiment, { model.setExperiment("wifi", it) }) }
                                    if (fields.any { it.path.startsWith("wifi.") && it.status == FieldStatus.permission_denied }) {
                                        Text(strings.get(R.string.ui_precise_location_permission_required))
                                        Button(onClick = { if (model.beginExperiment("wifi")) onPermissionRequest("wifi") }) { Text(strings.get(R.string.ui_grant_permission)) }
                                    }
                                    if (fields.any { it.path == "wifi.locationEnabled" && it.value == false }) Text(strings.get(R.string.ui_turn_on_system_location))
                                    TextButton(onClick = onSettings) { Text(strings.get(R.string.ui_app_permission_settings)) }
                                }
                                fields.filter { it.path.startsWith("wifi.") }.forEach { DataFieldCard(it, model.reveal, copied) }
                            }
                            "telephony" -> fields.filter { it.path.startsWith("telephony.") }.forEach { DataFieldCard(it, model.reveal, copied) }
                            "subscriptions" -> {
                                Panel(strings.get(R.string.ui_subscription_reading), permissionLabels.PHONE) {
                                    Row { Text(strings.get(R.string.ui_read_sim_and_service_state), Modifier.weight(1f)); Switch(model.phoneExperiment, { model.setExperiment("phone", it) }) }
                                    TextButton(onClick = { permissions = true }) { Text(strings.get(R.string.ui_permissions_and_comparison_a13e9a)) }
                                }
                                var section by rememberSaveable { mutableIntStateOf(0) }
                                ItemTabs(listOf(strings.get(R.string.ui_current_subscriptions), strings.get(R.string.ui_history)), section) { section = it }
                                if (section == 0) CurrentSubscriptionsSection(fields, model.reveal, copied)
                                else SubscriptionHistorySection(model.historyDocument, model.historyBusy, model.phoneExperiment,
                                    model.reveal, model::readSubscriptionHistory, { permissions = true }, copied)

                            }
                            "external" -> {
                                Button(onClick = { endpointConfig = true }, enabled = !model.externalBusy) { Text(strings.get(R.string.ui_public_network_cloudflare)) }
                                fields.filter { it.path.startsWith("externalChecks.") && !it.path.endsWith(".state") }.forEach { DataFieldCard(it, model.reveal, copied) }
                                if (fields.none { it.path.startsWith("externalChecks.") && it.path.endsWith(".result") }) Text(strings.get(R.string.ui_not_checked_yet))
                            }
                            "properties" -> SystemPropertiesSection(model.propertyDocument, model.propertiesBusy,
                                model.reveal, model::readSystemProperties, copied)
                            "permissions" -> {
                                Button(onClick = { permissions = true }) { Text(strings.get(R.string.ui_permissions_and_comparison)) }
                                fields.filter { it.path.startsWith("permissions.") }.forEach { DataFieldCard(it, model.reveal, copied) }
                            }
                            "device" -> {
                                model.snapshot?.let { snapshot ->
                                    Panel(strings.get(R.string.ui_device)) {
                                        SummaryRow(strings.get(R.string.ui_model), "${snapshot.environment.manufacturer} ${snapshot.environment.model}")
                                        SummaryRow("Android API", snapshot.environment.androidApi.toString())
                                        SummaryRow(strings.get(R.string.ui_app_version), snapshot.environment.appVersion)
                                    }
                                    var details by rememberSaveable { mutableStateOf(false) }
                                    TextButton(onClick = { details = !details }) { Text(if (details) strings.get(R.string.ui_collapse_sample_history) else strings.get(R.string.ui_sample_history)) }
                                    if (details) Panel(strings.get(R.string.ui_sample_history)) {
                                        SummaryRow(strings.get(R.string.ui_sample_time), snapshot.capturedAtUtc)
                                        SummaryRow(strings.get(R.string.ui_completion_time), snapshot.completedAtUtc)
                                        SummaryRow("target SDK", snapshot.environment.targetSdk.toString())
                                        snapshot.warnings.forEach { Text(strings.legacy(it), style = MaterialTheme.typography.bodySmall) }
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
