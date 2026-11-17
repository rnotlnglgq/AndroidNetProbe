package com.rnotlnglgq.netprobe.ui

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rnotlnglgq.netprobe.data.*
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.*
import kotlinx.coroutines.*

class ProbeViewModel(app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("owned_endpoints", 0)
    var endpointIpv4 by mutableStateOf(preferences.getString("ipv4", "").orEmpty())
        private set
    var endpointIpv6 by mutableStateOf(preferences.getString("ipv6", "").orEmpty())
        private set
    var externalBusy by mutableStateOf(false)
        private set
    private var externalResults = emptyList<EndpointResult>()
    private var externalJob: Job? = null
    private var externalRunHolder = AtomicReference<AndroidEndpointTransport?>(null)
    private var externalGeneration = 0
    private var captureGeneration = 0
    var propertyDocument by mutableStateOf(SystemPropertyProbe.notRequested())
        private set
    var propertiesBusy by mutableStateOf(false)
        private set
    private var propertyJob: Job? = null
    private var propertyProbe: SystemPropertyProbe? = null
    private var propertyGeneration = 0
    var nativeDocument by mutableStateOf(BionicInterfaceProbe.notRequested())
        private set
    var nativeBusy by mutableStateOf(false)
        private set
    private var nativeJob: Job? = null
    private var nativeGeneration = 0
    var historyDocument by mutableStateOf(SubscriptionHistoryDocument.initial(Build.VERSION.SDK_INT))
        private set
    var historyBusy by mutableStateOf(false)
        private set
    private var historyJob: Job? = null
    private var historyGeneration = 0
    private val repository = ProbeRepository(app)
    private val observer = NetworkChangeObserver(app) { sample ->
        if (foreground && monitoring) refresh(sample.event, sample)
    }
    private val wifi = WifiProbe(app) { if (foreground && monitoring) refresh("wifi_experiment_callback") }
    var phoneExperiment by mutableStateOf(preferences.getBoolean("phone_enabled", true))
        private set
    var wifiExperiment by mutableStateOf(preferences.getBoolean("wifi_enabled", true))
        private set
    private var baseline: NetworkSnapshot? = null
    private var experimentKind = ""
    private var compareAfterResult = false
    var comparison by mutableStateOf<PermissionComparison?>(null)
        private set
    val baselineAvailable: Boolean get() = baseline != null
    private var captureJob: Job? = null
    private var pending: Pair<String, NetworkSample?>? = null
    private var pendingExport: String? = null
    private val snapshots = SnapshotStore()
    var snapshot by mutableStateOf<NetworkSnapshot?>(null)
        private set

    init {
        snapshots.replaceSection(FieldSection.PROPERTIES, propertyDocument.fields)
        snapshots.replaceSection(FieldSection.NATIVE, nativeDocument.fields)
        snapshots.replaceSection(FieldSection.HISTORY, historyDocument.fields)
        viewModelScope.launch { snapshots.snapshot.collect { snapshot = it } }
    }
    var busy by mutableStateOf(false)
        private set
    var monitoring by mutableStateOf(true)
        private set
    var foreground by mutableStateOf(false)
        private set
    var registered by mutableStateOf(false)
        private set
    var events by mutableStateOf<List<NetworkEvent>>(emptyList())
        private set
    var reveal by mutableStateOf(false)
    var message by mutableStateOf("")

    fun enterForeground() {
        foreground = true
        if (historyDocument.capturedAtUtc != null && getApplication<Application>().checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            historyDocument = SubscriptionHistoryDocument.collect(Build.VERSION.SDK_INT, phoneExperiment, true, false) { emptyList() }
            snapshots.replaceSection(FieldSection.HISTORY, historyDocument.fields)
        }
        if (monitoring) { startObserver(); startWifi() }
        refresh("foreground")
    }
    fun leaveForeground() {
        foreground = false
        historyGeneration++
        historyJob?.cancel()
        historyBusy = false
        nativeGeneration++
        nativeJob?.cancel()
        nativeBusy = false
        propertyGeneration++
        propertyJob?.cancel()
        propertyProbe?.cancel()
        propertyProbe = null
        propertiesBusy = false
        externalGeneration++
        externalJob?.cancel()
        externalRunHolder.get()?.cancel()
        if (externalBusy) message = "已取消公网检测"
        externalBusy = false
        stopWifi()
        stopObserver()
        pending = null
        captureGeneration++
        captureJob?.cancel()
        captureJob = null
        busy = false
        reveal = false
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
    fun toggleMonitoring() {
        monitoring = !monitoring
        if (monitoring && foreground) { startObserver(); startWifi(); refresh("monitoring_started") }
        else { stopObserver(); stopWifi(); pending = null; captureGeneration++; captureJob?.cancel(); captureJob = null; busy = false }
    }
    fun refresh(event: String = "manual", sample: NetworkSample? = null) {
        if (!foreground) return
        if (monitoring) startWifi()
        pending = event to sample
        if (captureJob?.isActive == true) return
        val generation = ++captureGeneration
        captureJob = viewModelScope.launch {
            busy = true
            try {
                while (pending != null && foreground) {
                    // Coalesce bursts but retain the latest request even if another sample is in flight.
                    delay(150)
                    val request = pending ?: break
                    pending = null
                    val result = withContext(Dispatchers.IO) { repository.capture(request.first, request.second, phoneExperiment,
                        wifi.collect(wifiExperiment), externalFields(externalResults)) }
                    ensureActive()
                    snapshots.replaceBase(result)
                    val assembled = requireNotNull(snapshots.snapshot.value)
                    if (compareAfterResult) {
                        baseline?.let { comparison = PermissionComparison.between(experimentKind, it, assembled) }
                        compareAfterResult = false
                    }
                    events = (events + NetworkEvent(result.capturedAtUtc, result.event)).takeLast(40)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "采样失败：${e.javaClass.simpleName}" }
            finally { if (generation == captureGeneration) busy = false }
        }
    }
    fun readSubscriptionHistory() {
        if (!foreground || historyBusy || historyJob?.isCompleted == false) return
        val generation = ++historyGeneration
        val enabled = phoneExperiment
        historyBusy = true
        historyJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val job = currentCoroutineContext()[Job]
                    repository.captureHistory(enabled) { job?.isActive == true }
                }
                ensureActive()
                historyDocument = result
                snapshots.replaceSection(FieldSection.HISTORY, result.fields)
                events = (events + NetworkEvent(result.completedAtUtc.orEmpty(), "subscription_history_read")).takeLast(40)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "订阅记录读取失败：${e.javaClass.simpleName}" }
            finally { if (generation == historyGeneration) historyBusy = false }
        }
    }
    fun readNativeInterfaces() {
        if (!foreground || nativeBusy) return
        // getifaddrs cannot be forcibly interrupted; do not queue another call while it returns.
        if (nativeJob?.isCompleted == false) { message = "上次读取尚未结束"; return }
        val generation = ++nativeGeneration
        nativeBusy = true
        nativeJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { BionicInterfaceProbe().capture() }
                ensureActive()
                nativeDocument = result
                snapshots.replaceSection(FieldSection.NATIVE, result.fields)
                events = (events + NetworkEvent(result.completedAtUtc.orEmpty(), "bionic_interfaces_read")).takeLast(40)
            } finally { if (generation == nativeGeneration) nativeBusy = false }
        }
    }
    fun readSystemProperties() {
        if (!foreground || propertiesBusy) return
        val probe = SystemPropertyProbe()
        val generation = ++propertyGeneration
        propertyProbe = probe
        propertiesBusy = true
        propertyJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { probe.collect() }
                ensureActive()
                propertyDocument = result
                snapshots.replaceSection(FieldSection.PROPERTIES, result.fields)
                events = (events + NetworkEvent(result.completedAtUtc.orEmpty(), "system_properties_read")).takeLast(40)
                message = "系统属性已读取"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "系统属性读取失败：${e.javaClass.simpleName}" }
            finally {
                probe.cancel()
                if (generation == propertyGeneration) { propertiesBusy = false; propertyProbe = null }
            }
        }
    }
    fun prepareExport(raw: Boolean): Boolean {
        val current = snapshot ?: return false
        pendingExport = JsonExport.render(current, raw, events, comparison)
        return true
    }
    fun writeExport(uri: Uri?) {
        val document = pendingExport
        pendingExport = null
        if (uri == null) { message = "已取消导出"; return }
        if (document == null) { message = "请重新选择导出"; return }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: error("No output stream")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(document) }
                }
            }
            message = result.fold({ "导出完成" }, { "导出失败：${it.javaClass.simpleName}" })
        }
    }
    fun setExperiment(kind: String, enabled: Boolean) {
        if (kind == "phone") {
            phoneExperiment = enabled
            if (!enabled) {
                historyGeneration++
                historyJob?.cancel()
                historyBusy = false
                historyDocument = SubscriptionHistoryDocument.initial(Build.VERSION.SDK_INT)
                snapshots.replaceSection(FieldSection.HISTORY, historyDocument.fields)
            }
        } else wifiExperiment = enabled
        preferences.edit().putBoolean(if (kind == "phone") "phone_enabled" else "wifi_enabled", enabled).apply()
        if (kind == "wifi") {
            stopWifi()
            if (foreground && monitoring) startWifi()
        }
        refresh("experiment_setting_changed")
    }
    fun beginExperiment(kind: String): Boolean {
        val current = snapshot ?: return false
        baseline = current
        experimentKind = kind
        setExperiment(kind, true)
        return true
    }
    fun permissionsReturned() {
        compareAfterResult = true
        stopWifi()
        if (foreground && monitoring) startWifi()
        refresh("permission_result")
    }
    fun resampleComparison() { compareAfterResult = true; if (foreground && monitoring) { stopWifi(); startWifi() }; refresh("permission_resample") }
    fun disableExperiments() {
        setExperiment("phone", false)
        setExperiment("wifi", false)
        stopWifi()
        refresh("experiments_disabled")
    }
    private fun startWifi() {
        try { wifi.start(wifiExperiment) } catch (e: Exception) { message = "Wi-Fi 实验注册：${e.javaClass.simpleName}" }
    }
    private fun stopWifi() {
        try { wifi.stop() } catch (e: Exception) { message = "Wi-Fi 实验注销：${e.javaClass.simpleName}" }
    }
    fun saveEndpoints(ipv4: String, ipv6: String) {
        endpointIpv4 = ipv4
        endpointIpv6 = ipv6
        preferences.edit().putString("ipv4", ipv4).putString("ipv6", ipv6).apply()
        message = "服务器地址已保存"
    }
    fun runCloudflare() = runExternal(true)
    fun runExternal(cloudflare: Boolean = false) {
        if (!foreground || externalBusy) return
        val configs = if (cloudflare) listOf("IPv4", "IPv6").map {
            EndpointConfig(CloudflareTrace.ENDPOINT, it, "cloudflare_trace")
        } else listOf(EndpointConfig(endpointIpv4, "IPv4"), EndpointConfig(endpointIpv6, "IPv6"))
        if (configs.any { !EndpointPolicy.valid(it) } ||
            !cloudflare && EndpointPolicy.parse(configs[0]).host == EndpointPolicy.parse(configs[1]).host) {
            message = "请填写两个不同的 HTTPS 域名"
            return
        }
        externalBusy = true
        val generation = ++externalGeneration
        val holder = AtomicReference<AndroidEndpointTransport?>(null)
        externalRunHolder = holder
        externalJob = viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val transport = AndroidEndpointTransport(getApplication())
                    holder.set(transport)
                    try { configs.map { currentCoroutineContext().ensureActive(); PublicEndpointProbe(transport).check(it) } }
                    finally { transport.cancel(); holder.set(null) }
                }
                ensureActive()
                externalResults = results
                message = results.joinToString { "${it.expectedFamily}：${Presentation.endpointState(it.state)}" }
                refresh("external_results")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "公网检测失败：${e.javaClass.simpleName}" }
            finally { if (generation == externalGeneration) externalBusy = false }
        }
    }
    override fun onCleared() { propertyProbe?.cancel(); stopObserver(); stopWifi(); externalRunHolder.get()?.cancel() }
}
