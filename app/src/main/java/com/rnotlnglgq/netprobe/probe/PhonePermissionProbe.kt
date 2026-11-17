package com.rnotlnglgq.netprobe.probe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.rnotlnglgq.netprobe.model.*

class PhonePermissionProbe(private val context: Context) {
    private val sessionIds = SessionSubscriptionIds()
    fun collect(enabled: Boolean): List<ProbeField<*>> {
        val source = "SubscriptionManager.getActiveSubscriptionInfoList"
        val permission = "READ_PHONE_STATE"
        val path = "subscriptions.activeCount"
        if (!enabled) return listOf(unavailable(path, source, FieldStatus.not_requested, permission,
            "需用户开启订阅实验；即使权限已授予也不会自动采集", sensitive = false))
        val feature = if (Build.VERSION.SDK_INT >= 33) PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION else PackageManager.FEATURE_TELEPHONY
        if (!context.packageManager.hasSystemFeature(feature)) return listOf(unavailable(path, source,
            FieldStatus.unsupported_feature, permission, "设备未声明 $feature", sensitive = false))
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return listOf(unavailable(path, source, FieldStatus.permission_denied, permission, "用户未授予或已撤回权限", sensitive = false))
        val manager = context.getSystemService(SubscriptionManager::class.java)
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val subscriptions = readField(path, source, permission, sensitive = false) { manager?.activeSubscriptionInfoList }
        val fields = mutableListOf<ProbeField<*>>(ProbeField(path, subscriptions.value?.size,
            subscriptions.status, source, permission, subscriptions.note, subscriptions.errorType, sensitive = true))
        subscriptions.value?.sortedBy { it.simSlotIndex }?.forEachIndexed { index, info ->
            val prefix = "subscriptions[$index]"
            fields += SubscriptionRecordFields.collect(prefix, info, sessionIds)
            fun <T> add(name: String, api: String, permission: String = "none", note: String? = null, block: () -> T?) {
                fields += readField("$prefix.$name", api, permission, true, note, block).withAcquisition(PhoneAccess.PHONE)
            }
            // Raw subscription IDs remain local and are never serialized; never read ICCID, IMSI or phone number.
            val perSub = readField("$prefix.manager", "TelephonyManager.createForSubscriptionId", "none", sensitive = false) {
                telephony?.createForSubscriptionId(info.subscriptionId)
            }
            if (perSub.value == null) {
                fields += unavailable("$prefix.radio", "TelephonyManager.createForSubscriptionId",
                    perSub.status, "none", perSub.note, true).withAcquisition(PhoneAccess.PHONE)
            } else {
                fields += ServiceStateProbe(context).collect(prefix, perSub.value).map { it.withAcquisition(PhoneAccess.PHONE) }
                add("registeredOperatorName", "TelephonyManager.createForSubscriptionId.getNetworkOperatorName") { perSub.value.networkOperatorName }
                add("registeredMccMnc", "TelephonyManager.createForSubscriptionId.getNetworkOperator") { perSub.value.networkOperator }
                val radioFeature = Build.VERSION.SDK_INT < 33 || context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)
                if (radioFeature) add("dataNetworkType", "TelephonyManager.createForSubscriptionId.getDataNetworkType",
                    permission = PhoneAccess.dataNetworkType(Build.VERSION.SDK_INT), note = "实际数据承载制式常量；0=UNKNOWN，不等同状态栏 5G 图标；未采集状态栏显示信息") {
                    perSub.value.dataNetworkType
                } else fields += unavailable("$prefix.dataNetworkType", "TelephonyManager.getDataNetworkType",
                    FieldStatus.unsupported_feature, PhoneAccess.dataNetworkType(Build.VERSION.SDK_INT), "缺少 TELEPHONY_RADIO_ACCESS", true).withAcquisition(PhoneAccess.PHONE)
            }
        }
        return fields
    }
    fun history(enabled: Boolean): SubscriptionHistoryDocument = SubscriptionHistoryProbe(context, sessionIds).capture(enabled)
}
