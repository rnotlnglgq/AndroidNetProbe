package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.model.ProbeField

/** Header hints describe runtime authorization, not install-time normal permissions. */
object PermissionLabels {
    const val PHONE = "需授权：电话状态"
    const val WIFI = "需授权：精确位置 · 需开启系统位置"
    fun forField(field: ProbeField<*>): String? {
        val direct = field.permission
        val acquisition = field.acquisitionPermissions.joinToString(" + ")
        val phone = "READ_PHONE_STATE" in acquisition ||
            ("READ_PHONE_STATE" in direct && !(" or " in direct && "READ_BASIC_PHONE_STATE" in direct))
        val precise = "ACCESS_FINE_LOCATION" in acquisition || ("ACCESS_FINE_LOCATION" in direct &&
            !(" or " in direct && "ACCESS_COARSE_LOCATION" in direct))
        val location = precise || "ACCESS_COARSE_LOCATION" in direct || "ACCESS_COARSE_LOCATION" in acquisition
        val names = listOfNotNull(if (phone) "电话状态" else null, if (precise) "精确位置" else if (location) "位置" else null)
        return names.takeIf { it.isNotEmpty() }?.joinToString("、", prefix = "需授权：")
    }
}
