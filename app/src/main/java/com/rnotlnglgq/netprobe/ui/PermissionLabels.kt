package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.model.*

/** Header hints describe runtime authorization, not install-time normal permissions. */
object PermissionLabels {
    const val PHONE = "需授权：电话状态"
    const val WIFI = "需授权：精确位置 · 需开启系统位置"
    fun forField(field: ProbeField<*>): String? {
        val alternatives = PermissionCatalog.forField(field).runtimeAlternatives()
        val phone = alternatives.all { "READ_PHONE_STATE" in it }
        val precise = alternatives.all { "ACCESS_FINE_LOCATION" in it }
        val location = alternatives.all { "ACCESS_FINE_LOCATION" in it || "ACCESS_COARSE_LOCATION" in it }
        val names = listOfNotNull(if (phone) "电话状态" else null, if (precise) "精确位置" else if (location) "位置" else null)
        return names.takeIf { it.isNotEmpty() }?.joinToString("、", prefix = "需授权：")
    }
}
