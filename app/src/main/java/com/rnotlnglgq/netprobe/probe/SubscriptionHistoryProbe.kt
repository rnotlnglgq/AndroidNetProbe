package com.rnotlnglgq.netprobe.probe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.rnotlnglgq.netprobe.model.*
import java.time.Instant

/** Stores only explicitly requested, non-identifier SubscriptionInfo fields in memory. */
data class SubscriptionHistoryDocument(val capturedAtUtc: String?, val completedAtUtc: String?, val fields: List<ProbeField<*>>) {
    fun attach(snapshot: NetworkSnapshot) = snapshot.copy(fieldResults = snapshot.fieldResults.filterNot {
        it.path.startsWith("subscriptionHistory.") || it.path.startsWith("subscriptionHistory[")
    } + fields)
    companion object {
        const val SOURCE = "SubscriptionManager.getAllSubscriptionInfoList"
        fun initial(api: Int) = SubscriptionHistoryDocument(null, null, listOf(unavailable("subscriptionHistory.count", SOURCE,
            if (api < 34) FieldStatus.unsupported_api else FieldStatus.not_requested, PhoneAccess.PHONE,
            if (api < 34) DiagnosticCatalog.legacy("requires_android_14_api_34") else DiagnosticCatalog.legacy("manually_read_subscription_records_retained_by_the_system"), true)))
        fun collect(api: Int, enabled: Boolean, feature: Boolean, granted: Boolean,
            read: () -> List<List<ProbeField<*>>>): SubscriptionHistoryDocument {
            val start = Instant.now().toString()
            val count = when {
                api < 34 -> unavailable("subscriptionHistory.count", SOURCE, FieldStatus.unsupported_api, PhoneAccess.PHONE, DiagnosticCatalog.legacy("requires_api_34"), true)
                !enabled -> unavailable("subscriptionHistory.count", SOURCE, FieldStatus.not_requested, PhoneAccess.PHONE, DiagnosticCatalog.legacy("subscription_reading_disabled"), true)
                !feature -> unavailable("subscriptionHistory.count", SOURCE, FieldStatus.unsupported_feature, PhoneAccess.PHONE, DiagnosticCatalog.legacy("subscription_feature_missing"), true)
                !granted -> unavailable("subscriptionHistory.count", SOURCE, FieldStatus.permission_denied, PhoneAccess.PHONE, DiagnosticCatalog.legacy("phone_permission_required"), true)
                else -> null
            }
            val fields = mutableListOf<ProbeField<*>>()
            if (count != null) fields += count else {
                val records = readField("subscriptionHistory.count", SOURCE, PhoneAccess.PHONE, true) { read() }
                fields += ProbeField("subscriptionHistory.count", records.value?.size, if (records.value != null) FieldStatus.available else records.status, SOURCE, PhoneAccess.PHONE,
                    records.note, records.errorType, true)
                records.value?.forEach { fields += it }
            }
            val done = Instant.now().toString()
            fields += readField("subscriptionHistory.capturedAtUtc", SOURCE, sensitive = false) { start }
            fields += readField("subscriptionHistory.completedAtUtc", SOURCE, sensitive = false) { done }
            return SubscriptionHistoryDocument(start, done, fields)
        }
    }
}

class SubscriptionHistoryProbe(private val context: Context, private val ids: SessionSubscriptionIds) {
    fun capture(enabled: Boolean): SubscriptionHistoryDocument = SubscriptionHistoryDocument.collect(Build.VERSION.SDK_INT, enabled,
        context.packageManager.hasSystemFeature(if (Build.VERSION.SDK_INT >= 33) PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION else PackageManager.FEATURE_TELEPHONY),
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
        if (Build.VERSION.SDK_INT >= 34) {
            val manager = requireNotNull(context.getSystemService(android.telephony.SubscriptionManager::class.java))
            manager.allSubscriptionInfoList.sortedWith(compareBy({ it.simSlotIndex }, { it.subscriptionId })).mapIndexed { i, info ->
                SubscriptionRecordFields.collect("subscriptionHistory[$i]", info, ids)
            }
        } else emptyList()
    }
}
