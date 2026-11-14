package com.rnotlnglgq.netprobe.model

import com.google.gson.Gson

data class PermissionComparison(
    val experiment: String, val before: NetworkSnapshot, val after: NetworkSnapshot,
    val changedPaths: List<String>,
    val note: String = "保持网络和设备条件相同；差异可能来自网络变化，不能单凭此次对照归因于权限"
) {
    companion object {
        fun between(experiment: String, before: NetworkSnapshot, after: NetworkSnapshot): PermissionComparison {
            val gson = Gson()
            val old = before.fieldResults.associateBy { it.path }
            val current = after.fieldResults.associateBy { it.path }
            val changed = (old.keys + current.keys).sorted().filter { gson.toJson(old[it]) != gson.toJson(current[it]) }
            return PermissionComparison(experiment, before, after, changed)
        }
    }
}
