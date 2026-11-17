package com.rnotlnglgq.netprobe.probe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.*
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.rnotlnglgq.netprobe.model.*
import java.time.Instant

private data class WifiObservation(val ssid: String?, val bssid: String?, val capturedAtUtc: String,
    val rssiDbm: Int, val linkSpeedMbps: Int, val frequencyMhz: Int,
    val wifiStandard: Int, val txMbps: Int, val rxMbps: Int, val securityType: Int, val hiddenSsid: Boolean)

/** Optional Wi-Fi experiment; passive connected-network callback only, no scans or coordinates. */
class WifiProbe(private val context: Context, private val onChange: () -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var latest: WifiObservation? = null
    @Volatile private var observerError: ProbeField<*>? = null
    val isRegistered: Boolean get() = callback != null
    private val permission = "ACCESS_FINE_LOCATION + ACCESS_WIFI_STATE + ACCESS_NETWORK_STATE"
    private val source = "NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) / NetworkCapabilities.transportInfo / WifiInfo"

    fun locationEnabled(): Boolean {
        val lm = context.getSystemService(LocationManager::class.java)
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    private fun gate(enabled: Boolean): ProbeField<*>? = when {
        !enabled -> unavailable("wifi.state", source, FieldStatus.not_requested, permission, "尚未主动开启 Wi-Fi 权限实验", sensitive = false)
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI) -> unavailable("wifi.state", source,
            FieldStatus.unsupported_feature, permission, "设备无 Wi-Fi feature", sensitive = false)
        Build.VERSION.SDK_INT < 31 -> unavailable("wifi.state", source, FieldStatus.unsupported_api, permission,
            "此可选实验仅实现 API 31+ 的位置敏感回调；P0 正常可用", sensitive = false)
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ->
            unavailable("wifi.state", source, FieldStatus.permission_denied, permission, "未授予精确位置或权限已撤回；仅模糊位置不足", sensitive = false)
        !locationEnabled() -> unavailable("wifi.state", source, FieldStatus.not_requested, permission, "location_disabled：位置总开关关闭", sensitive = false)
        else -> null
    }

    fun start(enabled: Boolean) {
        if (callback != null || gate(enabled) != null || Build.VERSION.SDK_INT < 31) return
        observerError = null
        val next = object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
            var lastNetwork: Network? = null
            override fun onCapabilitiesChanged(network: Network, nc: NetworkCapabilities) {
                if (callback !== this) return
                val info = nc.transportInfo as? WifiInfo ?: return
                lastNetwork = network
                latest = WifiObservation(info.ssid, info.bssid, Instant.now().toString(),
                    info.rssi, info.linkSpeed, info.frequency, info.wifiStandard, info.txLinkSpeedMbps,
                    info.rxLinkSpeedMbps, info.currentSecurityType, info.hiddenSSID)
                onChange()
            }
            override fun onLost(network: Network) {
                if (callback !== this || lastNetwork != network) return
                latest = null
                onChange()
            }
        }
        try {
            manager.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                next, Handler(Looper.getMainLooper()))
            callback = next
        } catch (e: Exception) {
            observerError = readField<String>("wifi.observer", source, permission, sensitive = false) { throw e }
        }
    }
    fun stop() {
        val old = callback
        callback = null
        latest = null
        if (old != null) manager.unregisterNetworkCallback(old)
    }
    fun collect(enabled: Boolean): List<ProbeField<*>> {
        val fields = mutableListOf<ProbeField<*>>()
        if (enabled) fields += readField("wifi.locationEnabled", "LocationManager.isLocationEnabled / isProviderEnabled", sensitive = false) { locationEnabled() }
        val blocked = gate(enabled) ?: observerError
        if (blocked != null) return fields + listOf(blocked,
            unavailable("wifi.ssid", source, blocked.status, permission, blocked.note, true),
            unavailable("wifi.bssid", source, blocked.status, permission, blocked.note, true))
        val observation = latest
        fields += readField("wifi.observedAtUtc", source, permission,
            note = "最近一次前台 Wi-Fi 回调，不保证与默认网络快照同一时刻或属于同一网络；VPN 下可能是底层 Wi-Fi", sensitive = false) { observation?.capturedAtUtc }
        fun value(name: String, text: String?, sentinel: Boolean): ProbeField<*> =
            if (sentinel) unavailable("wifi.$name", source, FieldStatus.redacted, permission,
                "system_redacted：系统未暴露真实值", true)
            else readField("wifi.$name", source, permission, true) { text }
        fields += readField("wifi.rssiDbm", source, permission, sensitive = false) { observation?.rssiDbm }
        fields += readField("wifi.linkSpeedMbps", source, permission, note = "连接速率由系统报告，非实际吞吐量；-1 表示未知", sensitive = false) { observation?.linkSpeedMbps }
        fields += readField("wifi.frequencyMhz", source, permission, sensitive = false) { observation?.frequencyMhz }
        fields += readField("wifi.standard", "WifiInfo.getWifiStandard", permission, sensitive = false) { observation?.wifiStandard }
        fields += readField("wifi.txLinkSpeedMbps", "WifiInfo.getTxLinkSpeedMbps", permission, sensitive = false) { observation?.txMbps?.takeUnless { it == -1 } }
        fields += readField("wifi.rxLinkSpeedMbps", "WifiInfo.getRxLinkSpeedMbps", permission, sensitive = false) { observation?.rxMbps?.takeUnless { it == -1 } }
        fields += readField("wifi.securityType", "WifiInfo.getCurrentSecurityType", permission, sensitive = false) { observation?.securityType?.takeUnless { it == -1 } }
        fields += readField("wifi.hiddenSsid", "WifiInfo.getHiddenSSID", permission, true) { observation?.hiddenSsid }
        fields += value("ssid", observation?.ssid, observation?.ssid == "<unknown ssid>")
        fields += value("bssid", observation?.bssid, observation?.bssid == "02:00:00:00:00:00")
        return fields
    }
}
