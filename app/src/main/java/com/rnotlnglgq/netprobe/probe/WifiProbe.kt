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
        !enabled -> unavailable("wifi.state", source, FieldStatus.not_requested, permission, DiagnosticCatalog.legacy("wi_fi_reader_disabled"), sensitive = false)
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI) -> unavailable("wifi.state", source,
            FieldStatus.unsupported_feature, permission, DiagnosticCatalog.legacy("wi_fi_feature_missing"), sensitive = false)
        Build.VERSION.SDK_INT < 31 -> unavailable("wifi.state", source, FieldStatus.unsupported_api, permission,
            DiagnosticCatalog.legacy("location_sensitive_wi_fi_callbacks_require_api_31_basic_network_r"), sensitive = false)
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ->
            unavailable("wifi.state", source, FieldStatus.permission_denied, permission, DiagnosticCatalog.legacy("precise_location_permission_absent_or_revoked"), sensitive = false)
        !locationEnabled() -> unavailable("wifi.state", source, FieldStatus.not_requested, permission, DiagnosticCatalog.legacy("location_disabled_system_location_is_off"), sensitive = false)
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
            note = DiagnosticCatalog.legacy("latest_foreground_wi_fi_callback_it_can_differ_in_time_or_network"), sensitive = false) { observation?.capturedAtUtc }
        fun value(name: String, text: String?, sentinel: Boolean): ProbeField<*> =
            if (sentinel) unavailable("wifi.$name", source, FieldStatus.redacted, permission,
                DiagnosticCatalog.legacy("system_redacted_system_withheld_the_actual_value"), true)
            else readField("wifi.$name", source, permission, true) { text }
        fields += readField("wifi.rssiDbm", source, permission, sensitive = false) { observation?.rssiDbm }
        fields += readField("wifi.linkSpeedMbps", source, permission, note = DiagnosticCatalog.legacy("system_reported_link_speed_1_means_unknown_throughput_is_a_separa"), sensitive = false) { observation?.linkSpeedMbps }
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
