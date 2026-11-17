package com.rnotlnglgq.netprobe.ui

import android.net.Uri
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rnotlnglgq.netprobe.data.*
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.*
import com.rnotlnglgq.netprobe.session.CollectionSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ProbeUiState(val snapshot: NetworkSnapshot? = null, val busyTasks: Set<String> = emptySet())

class ProbeViewModel(private val services: ProbeServices) : ViewModel() {
    private val preferences = services.preferences
    private val session = CollectionSession(viewModelScope)
    private val snapshots = SnapshotStore()
    val state = combine(snapshots.snapshot,session.busy,::ProbeUiState)
        .stateIn(viewModelScope,SharingStarted.Eagerly,ProbeUiState())
    val snapshot: NetworkSnapshot? get() = snapshots.snapshot.value
    val busy get() = "capture" in state.value.busyTasks
    val externalBusy get() = "external" in state.value.busyTasks
    val propertiesBusy get() = "properties" in state.value.busyTasks
    val nativeBusy get() = "native" in state.value.busyTasks
    val historyBusy get() = "history" in state.value.busyTasks
    var endpointIpv4 by mutableStateOf(preferences.getString("ipv4", "").orEmpty()); private set
    var endpointIpv6 by mutableStateOf(preferences.getString("ipv6", "").orEmpty()); private set
    var propertyDocument by mutableStateOf(SystemPropertyProbe.notRequested()); private set
    var nativeDocument by mutableStateOf(BionicInterfaceProbe.notRequested()); private set
    var historyDocument by mutableStateOf(SubscriptionHistoryDocument.initial(services.api)); private set
    var phoneExperiment by mutableStateOf(preferences.getBoolean("phone_enabled", true)); private set
    var wifiExperiment by mutableStateOf(preferences.getBoolean("wifi_enabled", true)); private set
    var monitoring by mutableStateOf(true); private set
    var foreground by mutableStateOf(false); private set
    var registered by mutableStateOf(false); private set
    var comparison by mutableStateOf<PermissionComparison?>(null); private set
    var events by mutableStateOf<List<NetworkEvent>>(emptyList()); private set
    var reveal by mutableStateOf(false)
    var message by mutableStateOf("")
    private var externalResults = emptyList<EndpointResult>()
    private var baseline: NetworkSnapshot? = null
    private var experimentKind = ""
    private var compareAfterResult = false
    private var pendingExport: String? = null
    val baselineAvailable: Boolean get() = baseline != null
    private val observer = services.observer { if (foreground && monitoring) refresh(it.event,it) }
    private val wifi = services.wifi { if (foreground && monitoring) refresh("wifi_experiment_callback") }

    init {
        snapshots.replaceSection(FieldSection.PROPERTIES,propertyDocument.fields)
        snapshots.replaceSection(FieldSection.NATIVE,nativeDocument.fields)
        snapshots.replaceSection(FieldSection.HISTORY,historyDocument.fields)
    }
    private fun event(name: String, time: String = services.now()) {
        events = (events + NetworkEvent(time,name)).takeLast(40)
    }
    fun enterForeground() {
        foreground = true
        session.enter()
        if (!services.phoneGranted()) {
            historyDocument = SubscriptionHistoryDocument.collect(services.api,phoneExperiment,true,false) { emptyList() }
            snapshots.replaceSection(FieldSection.HISTORY,historyDocument.fields)
        }
        if (monitoring) { startObserver(); startWifi() }
        refresh("foreground")
    }
    fun leaveForeground() {
        foreground = false
        if (externalBusy) message = "已取消公网检测"
        session.leave()
        stopWifi(); stopObserver(); reveal = false
    }
    private fun startObserver() {
        try { observer.start(); registered = observer.isRegistered }
        catch (e: Exception) { registered = false; message = "监测注册失败：${e.javaClass.simpleName}" }
    }
    private fun stopObserver() {
        try { observer.stop() }
        catch (e: Exception) { message = "监测注销结果：${e.javaClass.simpleName}" }
        finally { registered = false }
    }
    private fun startWifi() {
        try { wifi.start(wifiExperiment) } catch (e: Exception) { message = "Wi-Fi 实验注册：${e.javaClass.simpleName}" }
    }
    private fun stopWifi() {
        try { wifi.stop() } catch (e: Exception) { message = "Wi-Fi 实验注销：${e.javaClass.simpleName}" }
    }
    fun toggleMonitoring() {
        monitoring = !monitoring
        if (monitoring && foreground) { startObserver(); startWifi(); refresh("monitoring_started") }
        else { stopObserver(); stopWifi(); session.cancel("capture") }
    }
    fun refresh(event: String = "manual", sample: NetworkSample? = null) {
        if (!foreground) return
        if (monitoring) startWifi()
        session.refresh {
            try {
                val enabled = phoneExperiment
                val wifiEnabled = wifiExperiment
                val external = externalResults
                val result = withContext(Dispatchers.IO) {
                    services.repository.capture(event,sample,enabled,wifi.collect(wifiEnabled),externalFields(external))
                }
                currentCoroutineContext().ensureActive()
                snapshots.replaceBase(result)
                if (compareAfterResult) {
                    baseline?.let { comparison = PermissionComparison.between(experimentKind,it,requireNotNull(snapshot)) }
                    compareAfterResult = false
                }
                event(result.event,result.capturedAtUtc)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "采样失败：${e.javaClass.simpleName}" }
        }
    }
    fun readSubscriptionHistory() {
        val enabled = phoneExperiment
        session.start("history",work = {
            val job = currentCoroutineContext()[Job]
            services.repository.captureHistory(enabled) { job?.isActive == true }
        },commit = {
            historyDocument = it
            snapshots.replaceSection(FieldSection.HISTORY,it.fields)
            event("subscription_history_read",it.completedAtUtc.orEmpty())
        },failed = { message = "订阅记录读取失败：${it.javaClass.simpleName}" })
    }
    fun readNativeInterfaces() {
        session.start("native",work = { services.native().capture() },commit = {
            nativeDocument = it
            snapshots.replaceSection(FieldSection.NATIVE,it.fields)
            event("bionic_interfaces_read",it.completedAtUtc.orEmpty())
        },failed = { message = "Native 读取失败：${it.javaClass.simpleName}" })
    }
    fun readSystemProperties() {
        val probe = services.properties()
        session.start("properties",cancelResource = probe::cancel,work = { probe.collect() },commit = {
            propertyDocument = it
            snapshots.replaceSection(FieldSection.PROPERTIES,it.fields)
            event("system_properties_read",it.completedAtUtc.orEmpty())
            message = "系统属性已读取"
        },failed = { message = "系统属性读取失败：${it.javaClass.simpleName}" })
    }
    fun prepareExport(raw: Boolean): Boolean {
        val current = snapshot ?: return false
        pendingExport = JsonExport.render(current,raw,events,comparison)
        return true
    }
    fun writeExport(uri: Uri?) {
        val document = pendingExport; pendingExport = null
        if (uri == null) { message = "已取消导出"; return }
        if (document == null) { message = "请重新选择导出"; return }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { services.write(uri,document) } }
            message = result.fold({ "导出完成" }, { "导出失败：${it.javaClass.simpleName}" })
        }
    }
    fun setExperiment(kind: String, enabled: Boolean) {
        require(kind == "phone" || kind == "wifi")
        if (kind == "phone") {
            phoneExperiment = enabled
            if (!enabled) {
                session.cancel("history")
                historyDocument = SubscriptionHistoryDocument.initial(services.api)
                snapshots.replaceSection(FieldSection.HISTORY,historyDocument.fields)
            }
        } else { wifiExperiment = enabled; stopWifi(); if (foreground && monitoring) startWifi() }
        if (!enabled) {
            session.cancel("capture")
            baseline = null; comparison = null; compareAfterResult = false
            val current = snapshot
            if (current != null) snapshots.replaceBase(current.copy(fieldResults=current.fieldResults.filterNot {
                if (kind == "phone") it.path.startsWith("subscriptions") else it.path.startsWith("wifi.")
            }))
        }
        preferences.edit().putBoolean(if (kind == "phone") "phone_enabled" else "wifi_enabled",enabled).apply()
        refresh("experiment_setting_changed")
    }
    fun beginExperiment(kind: String): Boolean {
        baseline = snapshot ?: return false
        experimentKind = kind
        setExperiment(kind,true)
        return true
    }
    fun permissionsReturned() {
        compareAfterResult = true; stopWifi()
        if (foreground && monitoring) startWifi()
        refresh("permission_result")
    }
    fun resampleComparison() { compareAfterResult = true; refresh("permission_resample") }
    fun disableExperiments() { setExperiment("phone",false); setExperiment("wifi",false) }
    fun saveEndpoints(ipv4: String, ipv6: String) {
        endpointIpv4=ipv4; endpointIpv6=ipv6
        preferences.edit().putString("ipv4",ipv4).putString("ipv6",ipv6).apply()
        message = "服务器地址已保存"
    }
    fun runCloudflare() = runExternal(true)
    fun runExternal(cloudflare: Boolean = false) {
        val configs = if (cloudflare) listOf("IPv4","IPv6").map { EndpointConfig(CloudflareTrace.ENDPOINT,it,"cloudflare_trace") }
            else listOf(EndpointConfig(endpointIpv4,"IPv4"),EndpointConfig(endpointIpv6,"IPv6"))
        if (configs.any { !EndpointPolicy.valid(it) } || !cloudflare && EndpointPolicy.parse(configs[0]).host == EndpointPolicy.parse(configs[1]).host) {
            message = "请填写两个不同的 HTTPS 域名"; return
        }
        val holder = AtomicReference<AndroidEndpointTransport?>(null)
        session.start("external",cancelResource = { holder.get()?.cancel() },work = {
            val transport = services.transport(); holder.set(transport)
            try { configs.map { currentCoroutineContext().ensureActive(); PublicEndpointProbe(transport).check(it) } }
            finally { transport.cancel(); holder.set(null) }
        },commit = {
            externalResults = it
            message = it.joinToString { result -> "${result.expectedFamily}：${Presentation.endpointState(result.state)}" }
            refresh("external_results")
        },failed = { message = "公网检测失败：${it.javaClass.simpleName}" })
    }
    override fun onCleared() { session.leave(); stopObserver(); stopWifi() }
}
