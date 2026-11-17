package com.rnotlnglgq.netprobe.data

import android.content.Context
import android.os.Build
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.probe.*
import java.time.Instant
import java.util.UUID

class ProbeRepository(private val context: Context) {
    private val runId = UUID.randomUUID().toString()
    private val network = NetworkSnapshotProbe(context)
    private val interfaces = InterfaceProbe()
    private val telephony = TelephonyProbe(context)
    private val phone = PhonePermissionProbe(context)
    private val permissions = PermissionAudit(context)

    @Synchronized
    fun captureHistory(enabled: Boolean, shouldRead: () -> Boolean = { true }): SubscriptionHistoryDocument {
        // Recheck after acquiring the repository lock, so a cancelled queued read cannot start later.
        if (!shouldRead()) throw java.util.concurrent.CancellationException("History read cancelled")
        return phone.history(enabled)
    }

    @Synchronized
    fun capture(event: String, sample: NetworkSample? = null, phoneEnabled: Boolean = false, wifiFields: List<ProbeField<*>> = emptyList(), endpointFields: List<ProbeField<*>> = emptyList()): NetworkSnapshot {
        val start = sample?.capturedAtUtc ?: Instant.now().toString()
        val read = network.collect(sample)
        val fields = mutableListOf<ProbeField<*>>()
        fields += read.fields
        fun module(name: String, block: () -> List<ProbeField<*>>) {
            try { fields += block() } catch (e: java.util.concurrent.CancellationException) { throw e } catch (e: Exception) {
                fields += readField<List<ProbeField<*>>>("$name.module", name, sensitive = false) { throw e }
            }
        }
        val visible = try { interfaces.collect() } catch (e: Exception) {
            listOf(readField<String>("visibleInterfaces.module", "InterfaceProbe", sensitive = false) { throw e })
        }
        fields += visible
        val defaultName = read.fields.firstOrNull { it.path == FieldKeys.DEFAULT_INTERFACE }?.value as? String
        val match = visible.firstOrNull { it.path.endsWith(".name") && it.value == defaultName && defaultName != null }
        val fromLink = (read.fields.firstOrNull { it.path == FieldKeys.DEFAULT_ADDRESSES }?.value as? List<*>)
            ?.filterIsInstance<AddressRecord>()?.map { it.address.substringBefore('%') }?.toSet()
        val fromJava = (visible.firstOrNull { it.path == match?.path?.removeSuffix("name") + "addresses" }?.value as? List<*>)
            ?.filterIsInstance<AddressRecord>()?.map { it.address.substringBefore('%') }?.toSet()
        fields += readField("visibleInterfaces.crossCheck", "LinkProperties vs NetworkInterface address sets", sensitive = true,
            note = "两者范围不同；不一致不等于采集错误，也不代表存在不可见网络") {
            if (defaultName == null) null else InterfaceComparison(match != null,
                if (fromLink != null && fromJava != null) fromLink.intersect(fromJava).size else null,
                if (fromLink != null && fromJava != null) (fromLink - fromJava).toList() else null,
                if (fromLink != null && fromJava != null) (fromJava - fromLink).toList() else null)
        }
        module("telephony") { telephony.collect() }
        module("subscriptions") { phone.collect(phoneEnabled) }
        fields += wifiFields
        module("permissions") { permissions.collect() }
        fields += if (endpointFields.isEmpty()) externalFields(emptyList()) else endpointFields
        return NetworkSnapshot(capturedAtUtc = start, completedAtUtc = Instant.now().toString(),
            runId = runId, event = event,
            environment = Environment(Build.VERSION.SDK_INT, context.applicationInfo.targetSdkVersion,
                Build.MANUFACTURER, Build.MODEL, context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"), fieldResults = fields, warnings = read.warnings + "不同 API 非原子采样；本工具不以单一布尔值判断多来源真实性或完整性")
    }
}

data class InterfaceComparison(val defaultInterfaceVisible: Boolean, val sharedAddressCount: Int?,
    val linkOnlyAddresses: List<String>?, val javaOnlyAddresses: List<String>?)
