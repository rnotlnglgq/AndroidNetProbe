package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.ui.PermissionLabels
import org.junit.Assert.*
import org.junit.Test

class PermissionLabelsTest {
    @Test fun ordinaryPermissionsAndUnrestrictedGettersDoNotGetRuntimeBadge() {
        listOf("none","ACCESS_NETWORK_STATE","INTERNET","READ_BASIC_PHONE_STATE").forEach {
            assertNull(PermissionLabels.forField(readField("x","api",it) { "value" }))
        }
    }
    @Test fun acquisitionPermissionsRemainVisibleEvenIfGetterItselfIsUnrestricted() {
        val field=readField("subscriptions[0].displayName","SubscriptionInfo.getDisplayName") { "name" }.withAcquisition("READ_PHONE_STATE")
        assertEquals("需授权：电话状态",PermissionLabels.forField(field))
        assertEquals("需授权：电话状态、位置",PermissionLabels.forField(field.withAcquisition("ACCESS_COARSE_LOCATION")))
    }
    @Test fun preciseLocationAndLocationAlternativesAreNotConflated() {
        assertEquals("需授权：精确位置",PermissionLabels.forField(readField("x","api","ACCESS_FINE_LOCATION + ACCESS_WIFI_STATE") { "value" }))
        assertEquals("需授权：位置",PermissionLabels.forField(readField("x","api","ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION") { "value" }))
        assertNull(PermissionLabels.forField(readField("x","api","READ_PHONE_STATE or READ_BASIC_PHONE_STATE") { "value" }))
    }
    @Test fun denialDoesNotHideTheExpectedPermission() {
        val field=unavailable("x","api",FieldStatus.permission_denied,"READ_PHONE_STATE + ACCESS_COARSE_LOCATION")
        assertEquals("需授权：电话状态、位置",PermissionLabels.forField(field))
    }
}
