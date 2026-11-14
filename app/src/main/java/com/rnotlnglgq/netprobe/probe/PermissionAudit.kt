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
            readField("permissions.$name", "PackageManager.requestedPermissions / Context.checkSelfPermission") {
                val full = "android.permission.$name"
                when {
                    full !in declared -> "not_declared"
                    context.checkSelfPermission(full) == PackageManager.PERMISSION_GRANTED -> "granted"
                    else -> "denied"
                }
            }
        } + listOf(readField("permissions.manifestDeclared", "PackageManager.requestedPermissions",
            note = "包含依赖库合并的权限。AndroidX DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION 为应用自定义 signature 权限，不赋予设备数据访问能力") {
            declared.sorted()
        }) + listOf(PackageManager.FEATURE_TELEPHONY, PackageManager.FEATURE_WIFI).map { feature ->
            readField("features.$feature", "PackageManager.hasSystemFeature") {
                context.packageManager.hasSystemFeature(feature)
            }
        }
    }
}
