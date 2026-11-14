package com.rnotlnglgq.netprobe.probe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import com.rnotlnglgq.netprobe.model.*

/** Per-subscription public telephony views. Deliberately does not request CellInfo or cell IDs. */
class ServiceStateProbe(private val context: Context) {
    fun collect(prefix: String, tm: TelephonyManager): List<ProbeField<*>> {
        val fields = mutableListOf<ProbeField<*>>()
        fun <T> add(name: String, source: String, minApi: Int = 26, permission: String = "none",
            note: String? = null, block: () -> T?) {
            fields += if (Build.VERSION.SDK_INT < minApi) unavailable("$prefix.$name", source,
                FieldStatus.unsupported_api, permission, "需要 API $minApi", true)
            else readField("$prefix.$name", source, permission, true, note, block)
        }
        add("simOperatorName", "TelephonyManager.getSimOperatorName") { tm.simOperatorName }
        add("simMccMnc", "TelephonyManager.getSimOperator") { tm.simOperator }
        add("simCountryIso", "TelephonyManager.getSimCountryIso") { tm.simCountryIso }
        add("networkCountryIso", "TelephonyManager.getNetworkCountryIso") { tm.networkCountryIso }
        add("networkRoaming", "TelephonyManager.isNetworkRoaming") { tm.isNetworkRoaming }
        add("dataState", "TelephonyManager.getDataState") { tm.dataState }
        add("simCarrierId", "TelephonyManager.getSimCarrierId", 28) { if (Build.VERSION.SDK_INT >= 28) tm.simCarrierId.takeUnless { it == -1 } else null }
        add("simCarrierIdName", "TelephonyManager.getSimCarrierIdName", 28) { if (Build.VERSION.SDK_INT >= 28) tm.simCarrierIdName?.toString() else null }
        add("simSpecificCarrierId", "TelephonyManager.getSimSpecificCarrierId", 29) { if (Build.VERSION.SDK_INT >= 29) tm.simSpecificCarrierId.takeUnless { it == -1 } else null }
        add("simSpecificCarrierIdName", "TelephonyManager.getSimSpecificCarrierIdName", 29) { if (Build.VERSION.SDK_INT >= 29) tm.simSpecificCarrierIdName?.toString() else null }
        fields += RadioSignalProbe.collect(prefix, tm)
        val source = "TelephonyManager.createForSubscriptionId.getServiceState"
        val permission = PhoneAccess.SERVICE_STATE
        val hasLocation = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val state = if (!hasLocation) unavailable("$prefix.serviceState.state", source, FieldStatus.permission_denied,
            permission, "服务状态需要电话与位置授权；未获取对象，未推断无服务", true)
        else readField("$prefix.serviceState.state", source, permission, true) { tm.serviceState }
        fields += ProbeField(state.path, state.value?.let { "available" }, state.status, state.source, state.permission, state.note, state.errorType, true)
        val s = state.value
        val names = listOf("voiceState", "operatorNumeric", "operatorAlphaLong", "operatorAlphaShort", "roaming", "manualSelection", "channelNumber", "cellBandwidthsKhz", "duplexMode", "registrations")
        if (s == null) {
            names.forEach { fields += unavailable("$prefix.serviceState.$it", source, state.status, PhoneAccess.serviceField(it), state.note, true).withAcquisition(PhoneAccess.PHONE, "ACCESS_COARSE_LOCATION") }
            return fields
        }
        fun <T> service(name: String, api: String, minApi: Int = 26, note: String? = null, block: () -> T?) =
            add("serviceState.$name", api, minApi, PhoneAccess.serviceField(name), note, block)
        service("voiceState", "ServiceState.getState", note = "语音服务状态：0 IN_SERVICE / 1 OUT_OF_SERVICE / 2 EMERGENCY_ONLY / 3 POWER_OFF；不等同数据注册状态") { s.state }
        service("operatorNumeric", "ServiceState.getOperatorNumeric", note = "空值可来自未注册、未知或位置脱敏；无法仅凭 null 区分") { s.operatorNumeric }
        service("operatorAlphaLong", "ServiceState.getOperatorAlphaLong") { s.operatorAlphaLong }
        service("operatorAlphaShort", "ServiceState.getOperatorAlphaShort") { s.operatorAlphaShort }
        service("roaming", "ServiceState.getRoaming") { s.roaming }
        service("manualSelection", "ServiceState.getIsManualSelection") { s.isManualSelection }
        service("channelNumber", "ServiceState.getChannelNumber", 28) { if (Build.VERSION.SDK_INT >= 28) s.channelNumber.takeUnless { it == -1 } else null }
        service("cellBandwidthsKhz", "ServiceState.getCellBandwidths", 28) { if (Build.VERSION.SDK_INT >= 28) s.cellBandwidths.toList() else null }
        service("duplexMode", "ServiceState.getDuplexMode", 28) { if (Build.VERSION.SDK_INT >= 28) s.duplexMode else null }
        service("registrations", "ServiceState.getNetworkRegistrationInfoList / NetworkRegistrationInfo getters", 30,
            "按 CS/PS 域与 WWAN/WLAN 传输分别记录；PLMN 可能为最后尝试注册的网络，需结合 registered；没有读取 CellIdentity") {
            if (Build.VERSION.SDK_INT >= 30) s.networkRegistrationInfoList.map { r ->
                RegistrationView(r.domain, r.transportType, r.accessNetworkTechnology, r.registeredPlmn,
                    if (Build.VERSION.SDK_INT >= 34) r.isNetworkRegistered else r.isRegistered,
                    if (Build.VERSION.SDK_INT >= 34) r.isNetworkRoaming else r.isRoaming,
                    r.availableServices, if (Build.VERSION.SDK_INT >= 35) r.rejectCause else null)
            } else null
        }
        return fields.map { field ->
            if (field.path.startsWith("$prefix.serviceState.") && field.path != "$prefix.serviceState.state")
                field.withAcquisition(PhoneAccess.PHONE, "ACCESS_COARSE_LOCATION") else field
        }
    }
}

data class RadioSignal(val technology: String, val dbm: Int?, val asuLevel: Int?, val level: Int)
data class RegistrationView(val domain: Int, val transportType: Int, val accessNetworkTechnology: Int,
    val registeredPlmn: String?, val registered: Boolean, val roaming: Boolean, val availableServices: List<Int>, val rejectCause: Int?)
