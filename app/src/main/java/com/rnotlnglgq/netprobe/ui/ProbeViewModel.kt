package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

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
    private var messageId = 0L
    var message by mutableStateOf<UiMessage?>(null); private set
    private fun notify(text: UiText) { message = UiMessage(++messageId,text) }
    fun consumeMessage(id: Long) { if (message?.id == id) message = null }
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
        snapshots.invalidatePermissions(services.runtimePermissions())
        if (historyDocument.capturedAtUtc != null && !services.phoneGranted()) {
            historyDocument = SubscriptionHistoryDocument.collect(services.api,phoneExperiment,true,false) { emptyList() }
            snapshots.replaceSection(FieldSection.HISTORY,historyDocument.fields)
        }
        if (monitoring) { startObserver(); startWifi() }
        refresh("foreground")
    }
    fun leaveForeground() {
        foreground = false
        if (externalBusy) notify(UiText.resource(R.string.ui_public_checks_cancelled))
        session.leave()
        stopWifi(); stopObserver(); reveal = false
    }
    private fun startObserver() {
        try { observer.start(); registered = observer.isRegistered }
        catch (e: Exception) { registered = false; notify(UiText.resource(R.string.ui_monitor_registration_failed_1_s ,e.javaClass.simpleName)) }
    }
    private fun stopObserver() {
        try { observer.stop() }
        catch (e: Exception) { notify(UiText.resource(R.string.ui_monitor_stopped_1_s ,e.javaClass.simpleName)) }
        finally { registered = false }
    }
    private fun startWifi() {
        try { wifi.start(wifiExperiment) } catch (e: Exception) { notify(UiText.resource(R.string.ui_wi_fi_observer_registration_1_s ,e.javaClass.simpleName)) }
    }
    private fun stopWifi() {
        try { wifi.stop() } catch (e: Exception) { notify(UiText.resource(R.string.ui_wi_fi_observer_stopped_1_s ,e.javaClass.simpleName)) }
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
                    val job = currentCoroutineContext()[Job]
                    services.repository.capture(event,sample,enabled,wifi.collect(wifiEnabled),externalFields(external)) { job?.isActive == true }
                }
                currentCoroutineContext().ensureActive()
                snapshots.replaceBase(result)
                if (compareAfterResult) {
                    baseline?.let { comparison = PermissionComparison.between(experimentKind,it,requireNotNull(snapshot)) }
                    compareAfterResult = false
                }
                event(result.event,result.capturedAtUtc)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { notify(UiText.resource(R.string.ui_sampling_failed_1_s ,e.javaClass.simpleName)) }
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
        },failed = { notify(UiText.resource(R.string.ui_subscription_history_read_failed_1_s ,it.javaClass.simpleName)) })
    }
    fun readNativeInterfaces() {
        session.start("native",work = { services.native().capture() },commit = {
            nativeDocument = it
            snapshots.replaceSection(FieldSection.NATIVE,it.fields)
            event("bionic_interfaces_read",it.completedAtUtc.orEmpty())
        },failed = { notify(UiText.resource(R.string.ui_native_read_failed_1_s ,it.javaClass.simpleName)) })
    }
    fun readSystemProperties() {
        val probe = services.properties()
        session.start("properties",cancelResource = probe::cancel,work = { probe.collect() },commit = {
            propertyDocument = it
            snapshots.replaceSection(FieldSection.PROPERTIES,it.fields)
            event("system_properties_read",it.completedAtUtc.orEmpty())
            notify(UiText.resource(R.string.ui_system_properties_read))
        },failed = { notify(UiText.resource(R.string.ui_system_property_read_failed_1_s ,it.javaClass.simpleName)) })
    }
    fun prepareExport(raw: Boolean): Boolean {
        val current = snapshot ?: return false
        pendingExport = JsonExport.render(current,raw,events,comparison)
        return true
    }
    fun writeExport(uri: Uri?) {
        val document = pendingExport; pendingExport = null
        if (uri == null) { notify(UiText.resource(R.string.ui_export_cancelled)); return }
        if (document == null) { notify(UiText.resource(R.string.ui_please_select_export_again)); return }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { services.write(uri,document) } }
            notify(result.fold({ UiText.resource(R.string.ui_export_complete) }, { UiText.resource(R.string.ui_export_failed_1_s ,it.javaClass.simpleName) }))
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
        snapshots.invalidatePermissions(services.runtimePermissions())
        compareAfterResult = true; stopWifi()
        if (foreground && monitoring) startWifi()
        refresh("permission_result")
    }
    fun resampleComparison() { compareAfterResult = true; refresh("permission_resample") }
    fun disableExperiments() { setExperiment("phone",false); setExperiment("wifi",false) }
    fun saveEndpoints(ipv4: String, ipv6: String) {
        endpointIpv4=ipv4; endpointIpv6=ipv6
        preferences.edit().putString("ipv4",ipv4).putString("ipv6",ipv6).apply()
        notify(UiText.resource(R.string.ui_server_addresses_saved))
    }
    fun runCloudflare() = runExternal(true)
    fun runExternal(cloudflare: Boolean = false) {
        val configs = if (cloudflare) listOf("IPv4","IPv6").map { EndpointConfig(CloudflareTrace.ENDPOINT,it,"cloudflare_trace") }
            else listOf(EndpointConfig(endpointIpv4,"IPv4"),EndpointConfig(endpointIpv6,"IPv6"))
        if (configs.any { !EndpointPolicy.valid(it) } || !cloudflare && EndpointPolicy.parse(configs[0]).host == EndpointPolicy.parse(configs[1]).host) {
            notify(UiText.resource(R.string.ui_enter_two_different_https_hostnames)); return
        }
        val holder = AtomicReference<AndroidEndpointTransport?>(null)
        session.start("external",cancelResource = { holder.get()?.cancel() },work = {
            val transport = services.transport(); holder.set(transport)
            try { configs.map { currentCoroutineContext().ensureActive(); PublicEndpointProbe(transport).check(it) } }
            finally { transport.cancel(); holder.set(null) }
        },commit = {
            externalResults = it
            notify(UiText.resource(R.string.ui_public_checks_complete))
            refresh("external_results")
        },failed = { notify(UiText.resource(R.string.ui_public_check_failed_1_s ,it.javaClass.simpleName)) })
    }
    override fun onCleared() { session.leave(); stopObserver(); stopWifi() }
}
