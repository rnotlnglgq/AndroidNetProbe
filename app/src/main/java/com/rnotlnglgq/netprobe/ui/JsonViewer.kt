package com.rnotlnglgq.netprobe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rnotlnglgq.netprobe.data.JsonExport
import com.rnotlnglgq.netprobe.model.*

/** Snapshot and JSON strings are frozen for the entire viewer session, including export time. */
data class JsonPreview(val redacted: String, val raw: String) {
    companion object {
        fun of(snapshot: NetworkSnapshot, events: List<NetworkEvent>, comparison: PermissionComparison?) =
            JsonPreview(JsonExport.render(snapshot, false, events, comparison), JsonExport.render(snapshot, true, events, comparison))
    }
}

@Composable
fun JsonViewer(preview: JsonPreview, onDismiss: () -> Unit, onCopied: (String) -> Unit) {
    var copied by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf(false) }
    val document = if (raw) preview.raw else preview.redacted
    // TextFieldValue retains cursor/selection; readOnly prevents altering the captured document.
    var value by remember(document) { mutableStateOf(TextFieldValue(document)) }
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(16.dp)) {
                Text("JSON", style = MaterialTheme.typography.titleLarge)
                Text("当前快照 · 长按可选择文本", style = MaterialTheme.typography.bodySmall)
                Row {
                    Checkbox(checked = raw, onCheckedChange = { raw = it })
                    Text("包含完整地址和运营商信息", modifier = Modifier.padding(top = 12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        copyProbeText(context, "NetProbe JSON", document, raw)
                        copied = if (raw) "已复制完整 JSON" else "已复制遮挡 JSON"
                        onCopied(copied)
                    }) { Text("复制全文") }
                    OutlinedButton(onClick = onDismiss) { Text("关闭") }
                }
                if (copied.isNotEmpty()) Text(copied)
                BasicTextField(value = value, onValueChange = { value = it }, readOnly = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp))
            }
        }
    }
}
