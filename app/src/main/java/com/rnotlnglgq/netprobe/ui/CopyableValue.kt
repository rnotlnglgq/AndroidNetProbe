package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

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
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    val context = LocalContext.current
    val hidden = field.sensitive && !reveal
    if (hidden) {
        Text(strings.get(R.string.ui_hidden))
        return
    }
    val json = JsonExport.gson.toJson(field.value)
    SelectionContainer {
        Text(json, fontFamily = FontFamily.Monospace, style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = {
            val text = (field.value as? String) ?: json
            copyProbeText(context, field.path, text, field.sensitive)
            onCopied(strings.get(R.string.ui_copied))
        }, enabled = field.value != null) { Text(strings.get(R.string.ui_copy_value)) }
        TextButton(onClick = {
            copyProbeText(context, field.path, JsonExport.gson.toJson(field), field.sensitive)
            onCopied(strings.get(R.string.ui_field_json_copied))
        }) { Text(strings.get(R.string.ui_copy_field_json)) }
    }
    val external = field.value as? EndpointResult
    val observedIp = external?.cloudflare?.observedIp ?: external?.reply?.observedIp
    if (observedIp != null) {
        SelectionContainer { Text(strings.get(R.string.ui_public_ip_1_s ,observedIp), fontFamily = FontFamily.Monospace) }
        TextButton(onClick = {
            copyProbeText(context, strings.get(R.string.ui_public_ip), observedIp, true)
            onCopied(strings.get(R.string.ui_public_ip_copied))
        }) { Text(strings.get(R.string.ui_copy_public_ip)) }
    }
    val addresses = (field.value as? List<*>)?.filterIsInstance<AddressRecord>().orEmpty()
    if (addresses.isNotEmpty()) {
        Text(strings.get(R.string.ui_address), style = MaterialTheme.typography.bodySmall)
        addresses.forEach { address ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SelectionContainer {
                Text(address.address, fontFamily = FontFamily.Monospace, style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                    modifier = Modifier.horizontalScroll(rememberScrollState()))
            }
            Text("/${address.prefixLength} · ${address.family} · ${presentation.addressType(address)}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    copyProbeText(context, strings.get(R.string.ui_ip_addresses), address.address, true)
                    onCopied(strings.get(R.string.ui_ip_address_copied))
                }) { Text(strings.get(R.string.ui_copy_address)) }
                TextButton(onClick = {
                    copyProbeText(context, strings.get(R.string.ui_ip_prefix), "${address.address}/${address.prefixLength}", true)
                    onCopied(strings.get(R.string.ui_address_and_prefix_copied))
                }) { Text(strings.get(R.string.ui_copy_cidr)) }
            }
        }
    }
}
