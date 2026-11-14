package com.rnotlnglgq.netprobe.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rnotlnglgq.netprobe.data.JsonExport
import com.rnotlnglgq.netprobe.model.AddressRecord
import com.rnotlnglgq.netprobe.model.ProbeField
import com.rnotlnglgq.netprobe.probe.EndpointResult

/** Copy only on an explicit tap. Hidden values never enter the clipboard. */
fun copyProbeText(context: Context, label: String, text: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText(label, text)
    if (Build.VERSION.SDK_INT >= 33 && sensitive) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

@Composable
fun CopyableValue(field: ProbeField<*>, reveal: Boolean, onCopied: (String) -> Unit) {
    val context = LocalContext.current
    val hidden = field.sensitive && !reveal
    if (hidden) {
        Text("已隐藏")
        return
    }
    val json = JsonExport.gson.toJson(field.value)
    SelectionContainer {
        Text(json, fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = {
            val text = (field.value as? String) ?: json
            copyProbeText(context, field.path, text, field.sensitive)
            onCopied("已复制")
        }, enabled = field.value != null) { Text("复制值") }
        TextButton(onClick = {
            copyProbeText(context, field.path, JsonExport.gson.toJson(field), field.sensitive)
            onCopied("已复制字段 JSON")
        }) { Text("复制字段 JSON") }
    }
    val external = field.value as? EndpointResult
    val observedIp = external?.cloudflare?.observedIp ?: external?.reply?.observedIp
    if (observedIp != null) {
        SelectionContainer { Text("出口 IP：$observedIp", fontFamily = FontFamily.Monospace) }
        TextButton(onClick = {
            copyProbeText(context, "出口 IP", observedIp, true)
            onCopied("已复制出口 IP")
        }) { Text("复制出口 IP") }
    }
    val addresses = (field.value as? List<*>)?.filterIsInstance<AddressRecord>().orEmpty()
    if (addresses.isNotEmpty()) {
        Text("地址", style = MaterialTheme.typography.bodySmall)
        addresses.forEach { address ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SelectionContainer {
                Text(address.address, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.horizontalScroll(rememberScrollState()))
            }
            Text("/${address.prefixLength} · ${address.family} · ${Presentation.addressType(address)}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    copyProbeText(context, "IP 地址", address.address, true)
                    onCopied("已复制 IP 地址")
                }) { Text("复制地址") }
                TextButton(onClick = {
                    copyProbeText(context, "IP / 前缀", "${address.address}/${address.prefixLength}", true)
                    onCopied("已复制地址与前缀")
                }) { Text("复制 CIDR") }
            }
        }
    }
}
