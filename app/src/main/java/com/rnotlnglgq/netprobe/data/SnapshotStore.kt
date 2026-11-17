package com.rnotlnglgq.netprobe.data

import com.rnotlnglgq.netprobe.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread owned store. Every consumer observes the same assembled snapshot. */
class SnapshotStore {
    private var base: NetworkSnapshot? = null
    private val sections = linkedMapOf<FieldSection, List<ProbeField<*>>>()
    private val mutableSnapshot = MutableStateFlow<NetworkSnapshot?>(null)
    val snapshot = mutableSnapshot.asStateFlow()

    fun replaceBase(value: NetworkSnapshot) { base = value; publish() }
    fun replaceSection(section: FieldSection, fields: List<ProbeField<*>>) {
        require(fields.all { section.owns(it.path) }) { "Section ownership mismatch" }
        sections[section] = fields.toList()
        publish()
    }
    private fun publish() {
        val current = base ?: return
        val fields = current.fieldResults.filterNot { field -> sections.keys.any { it.owns(field.path) } } + sections.values.flatten()
        require(fields.map { it.path }.distinct().size == fields.size) { "Duplicate field path" }
        mutableSnapshot.value = current.copy(fieldResults = fields)
    }
}

enum class FieldSection(val root: String) {
    PROPERTIES("systemProperties"), NATIVE("nativeInterfaces"), HISTORY("subscriptionHistory");
    fun owns(path: String) = path.startsWith("$root.") || path.startsWith("$root[")
}
