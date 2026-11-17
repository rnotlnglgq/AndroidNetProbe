package com.rnotlnglgq.netprobe.model

import com.google.gson.Gson

data class PermissionComparison(
    val experiment: String, val before: NetworkSnapshot, val after: NetworkSnapshot,
    val changedPaths: List<String>,
    val note: String = "保持网络和设备条件相同；差异可能来自网络变化，不能单凭此次对照归因于权限"
) {
    companion object {
        private fun indexed(fields: List<ProbeField<*>>): Map<String, ProbeField<*>> {
            val identities = fields.filter { it.path.endsWith(".name") || it.path.endsWith(".sessionSubscriptionId") }
                .associate { it.path.substringBeforeLast('.') to it.value.toString() }
            return fields.filterNot { it.path.endsWith(".capturedAtUtc") || it.path.endsWith(".completedAtUtc") || it.path.endsWith(".observedAtUtc") }
                .associateBy { field ->
                    val group = Regex("""^([^\[]+\[\d+])""").find(field.path)?.value
                    if (group != null && identities[group] != null) field.path.replaceRange(0, group.length, "${group.substringBefore('[')}[${identities[group]}]") else field.path
                }
        }

        fun between(experiment: String, before: NetworkSnapshot, after: NetworkSnapshot): PermissionComparison {
            val gson = Gson()
            val old = indexed(before.fieldResults)
            val current = indexed(after.fieldResults)
            val changed = (old.keys + current.keys).sorted().filter { key ->
                val a = old[key]; val b = current[key]
                a?.status != b?.status || gson.toJson(a?.value) != gson.toJson(b?.value)
            }.map { current[it]?.path ?: requireNotNull(old[it]).path }.distinct()
            return PermissionComparison(experiment, before, after, changed)
        }
    }
}
