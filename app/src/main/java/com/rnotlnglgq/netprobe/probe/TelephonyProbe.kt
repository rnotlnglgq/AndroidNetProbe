package com.rnotlnglgq.netprobe.probe

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import com.rnotlnglgq.netprobe.model.*

class TelephonyProbe(private val context: Context) {
    fun collect(): List<ProbeField<*>> {
        val manager = context.getSystemService(TelephonyManager::class.java)
        fun <T> field(name: String, api: String, subscription: Boolean, minApi: Int = 26, block: () -> T?): ProbeField<*> {
            val feature = if (Build.VERSION.SDK_INT >= 33) {
                if (subscription) PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION else PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS
            } else PackageManager.FEATURE_TELEPHONY
            return if (Build.VERSION.SDK_INT < minApi) unavailable("telephony.$name", api, FieldStatus.unsupported_api, note = "需要 API $minApi")
            else if (manager == null || !context.packageManager.hasSystemFeature(feature))
                unavailable("telephony.$name", api, FieldStatus.unsupported_feature, note = "设备未声明 $feature")
            else readField("telephony.$name", api, sensitive = true,
                note = "空值不代表没有 SIM；默认 TelephonyManager 的订阅选择由系统决定", block = block)
        }
        val fields = listOf(
            field("simOperatorName", "TelephonyManager.getSimOperatorName", true) { manager.simOperatorName },
            field("simOperatorMccMnc", "TelephonyManager.getSimOperator", true) { manager.simOperator },
            field("registeredOperatorName", "TelephonyManager.getNetworkOperatorName", false) { manager.networkOperatorName },
            field("registeredOperatorMccMnc", "TelephonyManager.getNetworkOperator", false) { manager.networkOperator },
            field("simCountryIso", "TelephonyManager.getSimCountryIso", true) { manager.simCountryIso },
            field("networkCountryIso", "TelephonyManager.getNetworkCountryIso", false) { manager.networkCountryIso },
            field("simState", "TelephonyManager.getSimState", true) { manager.simState },
            field("networkRoaming", "TelephonyManager.isNetworkRoaming", false) { manager.isNetworkRoaming },
            field("simCarrierId", "TelephonyManager.getSimCarrierId", true, 28) { if (Build.VERSION.SDK_INT >= 28) manager.simCarrierId.takeUnless { it == TelephonyManager.UNKNOWN_CARRIER_ID } else null },
            field("simCarrierIdName", "TelephonyManager.getSimCarrierIdName", true, 28) { if (Build.VERSION.SDK_INT >= 28) manager.simCarrierIdName?.toString() else null },
            field("simSpecificCarrierId", "TelephonyManager.getSimSpecificCarrierId", true, 29) { if (Build.VERSION.SDK_INT >= 29) manager.simSpecificCarrierId.takeUnless { it == TelephonyManager.UNKNOWN_CARRIER_ID } else null },
            field("simSpecificCarrierIdName", "TelephonyManager.getSimSpecificCarrierIdName", true, 29) { if (Build.VERSION.SDK_INT >= 29) manager.simSpecificCarrierIdName?.toString() else null },
            field("carrierIdFromSimMccMnc", "TelephonyManager.getCarrierIdFromSimMccMnc", true, 29) { if (Build.VERSION.SDK_INT >= 29) manager.carrierIdFromSimMccMnc.takeUnless { it == TelephonyManager.UNKNOWN_CARRIER_ID } else null },
            field("activeModemCount", "TelephonyManager.getActiveModemCount", true, 30) { if (Build.VERSION.SDK_INT >= 30) manager.activeModemCount else null })
        val radio = if (Build.VERSION.SDK_INT >= 33) PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS else PackageManager.FEATURE_TELEPHONY
        val data = if (Build.VERSION.SDK_INT >= 33) PackageManager.FEATURE_TELEPHONY_DATA else PackageManager.FEATURE_TELEPHONY
        val signalFields = if (manager != null && context.packageManager.hasSystemFeature(radio)) RadioSignalProbe.collect("telephony", manager)
            else listOf(unavailable("telephony.signalStrength.state", "TelephonyManager.getSignalStrength", FieldStatus.unsupported_feature))
        val dataFields = if (manager != null && context.packageManager.hasSystemFeature(data)) listOf(readField("telephony.dataState", "TelephonyManager.getDataState", sensitive=true) { manager.dataState })
            else listOf(unavailable("telephony.dataState", "TelephonyManager.getDataState", FieldStatus.unsupported_feature))
        return fields + signalFields + dataFields
    }
}
