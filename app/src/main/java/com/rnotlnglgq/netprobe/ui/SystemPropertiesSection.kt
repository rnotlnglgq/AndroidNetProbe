package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rnotlnglgq.netprobe.model.FieldStatus
import com.rnotlnglgq.netprobe.probe.PropertyCatalog
import com.rnotlnglgq.netprobe.probe.PropertyDocument

@Composable
fun SystemPropertiesSection(document: PropertyDocument, busy: Boolean, reveal: Boolean,
    onRead: () -> Unit, onCopied: (String) -> Unit) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    var group by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val byPath = document.fields.associateBy { it.path }
    Panel(strings.get(R.string.ui_system_properties)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRead, enabled = !busy) { Text(if (document.capturedAtUtc == null) strings.get(R.string.ui_read_properties) else strings.get(R.string.ui_read_again)) }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        }
        document.completedAtUtc?.let { time ->
            val display = runCatching { strings.time(time) }.getOrDefault(time)
            Text(strings.get(R.string.ui_1_s_2_s_properties_have_values_3_s ,document.fields.count { it.status == FieldStatus.available }, PropertyCatalog.entries.size, display))
        } ?: Text(strings.get(R.string.ui_not_read_yet))
    }
    ItemTabs(listOf(strings.get(R.string.ui_system), strings.get(R.string.ui_carrier), strings.get(R.string.ui_network)), group) { group = it }
    OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
        label = { Text(strings.get(R.string.ui_search_properties_in_this_group)) }, modifier = Modifier.fillMaxWidth())
    val selected = listOf("system", "carrier", "network")[group]
    val specs = PropertyCatalog.entries.filter { it.group == selected && (it.key.contains(search, true) || strings.legacy(it.title).contains(search, true)) }
    if (specs.isEmpty()) Text(strings.get(R.string.ui_no_matching_properties))
    specs.forEach { spec ->
        val field = byPath[PropertyCatalog.path(spec)]
        Panel(strings.legacy(spec.title)) {
            SelectionContainer { Text(spec.key, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            when {
                field == null || field.status == FieldStatus.not_requested -> Text(strings.get(R.string.ui_not_read))
                field.status == FieldStatus.empty -> Text(strings.get(R.string.ui_empty_or_unavailable))
                field.status != FieldStatus.available -> Text(presentation.status(field.status))
                spec.sensitive && !reveal -> Text(strings.get(R.string.ui_hidden))
                else -> {
                    val value = field.value as? String ?: ""
                    SelectionContainer { Text(value, fontFamily = FontFamily.Monospace) }
                    TextButton(onClick = { copyProbeText(context, spec.key, value, spec.sensitive); onCopied(strings.get(R.string.ui_property_value_copied)) }) { Text(strings.get(R.string.ui_copy_value)) }
                }
            }
            field?.let { FieldDetails(listOf(it), reveal, onCopied) }
        }
    }
}
