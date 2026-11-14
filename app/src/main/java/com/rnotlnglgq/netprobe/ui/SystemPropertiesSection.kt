package com.rnotlnglgq.netprobe.ui

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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SystemPropertiesSection(document: PropertyDocument, busy: Boolean, reveal: Boolean,
    onRead: () -> Unit, onCopied: (String) -> Unit) {
    var group by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val byPath = document.fields.associateBy { it.path }
    Panel("系统属性") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRead, enabled = !busy) { Text(if (document.capturedAtUtc == null) "读取属性" else "重新读取") }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        }
        document.completedAtUtc?.let { time ->
            val display = runCatching { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(time)) }.getOrDefault(time)
            Text("${document.fields.count { it.status == FieldStatus.available }} / ${PropertyCatalog.entries.size} 项有值 · $display")
        } ?: Text("尚未读取")
    }
    ItemTabs(listOf("系统", "运营商", "网络"), group) { group = it }
    OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
        label = { Text("搜索本组属性") }, modifier = Modifier.fillMaxWidth())
    val selected = listOf("system", "carrier", "network")[group]
    val specs = PropertyCatalog.entries.filter { it.group == selected && (it.key.contains(search, true) || it.title.contains(search, true)) }
    if (specs.isEmpty()) Text("没有匹配的属性")
    specs.forEach { spec ->
        val field = byPath[PropertyCatalog.path(spec)]
        Panel(spec.title) {
            SelectionContainer { Text(spec.key, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            when {
                field == null || field.status == FieldStatus.not_requested -> Text("未读取")
                field.status == FieldStatus.empty -> Text("空值或不可见")
                field.status != FieldStatus.available -> Text(Presentation.status(field.status))
                spec.sensitive && !reveal -> Text("已隐藏")
                else -> {
                    val value = field.value as? String ?: ""
                    SelectionContainer { Text(value, fontFamily = FontFamily.Monospace) }
                    TextButton(onClick = { copyProbeText(context, spec.key, value, spec.sensitive); onCopied("已复制属性值") }) { Text("复制值") }
                }
            }
            field?.let { FieldDetails(listOf(it), reveal, onCopied) }
        }
    }
}
