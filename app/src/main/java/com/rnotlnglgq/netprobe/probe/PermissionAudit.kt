package com.rnotlnglgq.netprobe.probe

import android.content.Context
import android.content.pm.PackageManager
import com.rnotlnglgq.netprobe.model.*

class PermissionAudit(private val context: Context) {
    @Suppress("DEPRECATION")
    fun collect(): List<ProbeField<*>> {
        val declared = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        val names = listOf("INTERNET", "ACCESS_NETWORK_STATE", "READ_PHONE_STATE", "READ_BASIC_PHONE_STATE",
            "ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION", "ACCESS_WIFI_STATE", "NEARBY_WIFI_DEVICES")
        return names.map { name ->
            readField("permissions.$name", "PackageManager.requestedPermissions / Context.checkSelfPermission", sensitive = false) {
                val full = "android.permission.$name"
                when {
                    full !in declared -> "not_declared"
                    context.checkSelfPermission(full) == PackageManager.PERMISSION_GRANTED -> "granted"
                    else -> "denied"
                }
            }
        } + listOf(readField("permissions.manifestDeclared", "PackageManager.requestedPermissions",
            note = DiagnosticCatalog.legacy("includes_merged_library_permissions_androidx_dynamic_receiver_not"), sensitive = false) {
            declared.sorted()
        }) + listOf(PackageManager.FEATURE_TELEPHONY, PackageManager.FEATURE_WIFI).map { feature ->
            readField("features.$feature", "PackageManager.hasSystemFeature", sensitive = false) {
                context.packageManager.hasSystemFeature(feature)
            }
        }
    }
}
