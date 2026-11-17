package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import com.rnotlnglgq.netprobe.model.*

/** Header hints describe runtime authorization, not install-time normal permissions. */
class PermissionLabels(private val strings: UiStrings) {
    val PHONE = strings.get(R.string.ui_permission_phone_state)
    val WIFI = strings.get(R.string.ui_permission_precise_location_system_location_required)
    fun forField(field: ProbeField<*>): String? {
        val alternatives = PermissionCatalog.forField(field).runtimeAlternatives()
        val phone = alternatives.all { "READ_PHONE_STATE" in it }
        val precise = alternatives.all { "ACCESS_FINE_LOCATION" in it }
        val location = alternatives.all { "ACCESS_FINE_LOCATION" in it || "ACCESS_COARSE_LOCATION" in it }
        val names = listOfNotNull(if (phone) strings.get(R.string.ui_phone_state) else null, if (precise) strings.get(R.string.ui_precise_location) else if (location) strings.get(R.string.ui_location) else null)
        return names.takeIf { it.isNotEmpty() }?.let { strings.get(R.string.ui_permission_1_s, it.joinToString(strings.get(R.string.ui_list_separator))) }
    }
}
