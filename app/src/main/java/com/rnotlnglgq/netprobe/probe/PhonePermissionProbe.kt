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
            DiagnosticCatalog.legacy("enable_the_subscription_reader_to_collect_these_fields"), sensitive = false))
        val feature = if (Build.VERSION.SDK_INT >= 33) PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION else PackageManager.FEATURE_TELEPHONY
        if (!context.packageManager.hasSystemFeature(feature)) return listOf(unavailable(path, source,
            FieldStatus.unsupported_feature, permission, DiagnosticCatalog.legacy("device_feature_missing_1_s" ,feature), sensitive = false))
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            return listOf(unavailable(path, source, FieldStatus.permission_denied, permission, DiagnosticCatalog.legacy("permission_absent_or_revoked"), sensitive = false))
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
                    permission = PhoneAccess.dataNetworkType(Build.VERSION.SDK_INT), note = DiagnosticCatalog.legacy("data_bearer_technology_0_means_unknown_status_bar_5g_display_is_a")) {
                    perSub.value.dataNetworkType
                } else fields += unavailable("$prefix.dataNetworkType", "TelephonyManager.getDataNetworkType",
                    FieldStatus.unsupported_feature, PhoneAccess.dataNetworkType(Build.VERSION.SDK_INT), DiagnosticCatalog.legacy("telephony_radio_access_feature_missing"), true).withAcquisition(PhoneAccess.PHONE)
            }
        }
        return fields
    }
    fun history(enabled: Boolean): SubscriptionHistoryDocument = SubscriptionHistoryProbe(context, sessionIds).capture(enabled)
}
