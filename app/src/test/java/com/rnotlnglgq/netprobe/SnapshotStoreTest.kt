package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.data.*
import com.rnotlnglgq.netprobe.model.*
import org.junit.Assert.*
import org.junit.Test

class SnapshotStoreTest {
    private fun base() = NetworkSnapshot(capturedAtUtc="start", completedAtUtc="end",runId="run",event="manual",
        environment=Environment(35,37,"test","test","0.1.0"),fieldResults=listOf(readField("network.value","test", sensitive = false) { true }))
    @Test fun baseRefreshKeepsAllManualSectionsInComparisonAndExport() {
        val store = SnapshotStore()
        FieldSection.entries.forEach { store.replaceSection(it,listOf(readField("${it.root}.count","test", sensitive = false) { 1 })) }
        store.replaceBase(base())
        val before = requireNotNull(store.snapshot.value)
        store.replaceBase(base().copy(completedAtUtc="later"))
        val after = requireNotNull(store.snapshot.value)
        assertEquals(4,after.fieldResults.size)
        assertTrue(PermissionComparison.between("phone",before,after).changedPaths.isEmpty())
    }
    @Test fun sectionReplacementAndOwnershipAreChecked() {
        val store=SnapshotStore();store.replaceBase(base())
        store.replaceSection(FieldSection.NATIVE,listOf(readField("nativeInterfaces.count","test", sensitive = false) { 2 }))
        store.replaceSection(FieldSection.NATIVE,listOf(readField("nativeInterfaces.count","test", sensitive = false) { 0 }))
        assertEquals(2,store.snapshot.value!!.fieldResults.size)
        try { store.replaceSection(FieldSection.HISTORY,listOf(readField("other.count","test", sensitive = false) { 1 })); fail() }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun comparisonUsesEntityIdentityAndIgnoresMetadataAndSamplingTime() {
        fun fields(a:Int,b:Int)=listOf(readField("subscriptions[$a].sessionSubscriptionId","test", sensitive = false) { "A" },
            readField("subscriptions[$a].carrierName","test", sensitive = false) { "Carrier A" },
            readField("subscriptions[$b].sessionSubscriptionId","test", sensitive = false) { "B" },
            readField("subscriptions[$b].carrierName","test", sensitive = false) { "Carrier B" },
            readField("subscriptionHistory.capturedAtUtc","test", sensitive = false) { "$a" })
        val before=base().copy(fieldResults=fields(0,1))
        val after=base().copy(fieldResults=fields(1,0).map { it.copy(note="new wording") })
        assertTrue(PermissionComparison.between("phone",before,after).changedPaths.isEmpty())
    }
}
