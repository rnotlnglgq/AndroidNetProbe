package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.rnotlnglgq.netprobe.probe.*

@Composable
fun EndpointScreen(model: ProbeViewModel, onDismiss: () -> Unit) {
    val strings = rememberUiStrings()
    var v4 by remember { mutableStateOf(model.endpointIpv4) }
    var v6 by remember { mutableStateOf(model.endpointIpv6) }
    var custom by remember { mutableStateOf(false) }
    var owned by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<String?>(null) }
    val valid = EndpointPolicy.valid(EndpointConfig(v4.trim(), "IPv4")) && EndpointPolicy.valid(EndpointConfig(v6.trim(), "IPv6")) &&
        runCatching { EndpointPolicy.parse(EndpointConfig(v4.trim(), "IPv4")).host != EndpointPolicy.parse(EndpointConfig(v6.trim(), "IPv6")).host }.getOrDefault(false)
    if (confirming != null) {
        val cloudflare = confirming == "cloudflare"
        AlertDialog(onDismissRequest = { confirming = null }, title = { Text(if (cloudflare) strings.get(R.string.ui_connect_to_cloudflare) else strings.get(R.string.ui_connect_to_your_server)) },
            text = { Text(if (cloudflare)
                strings.get(R.string.ui_check_ipv4_and_ipv6_separately_cloudflare_will_see_your_public_ip ,CloudflareTrace.ENDPOINT)
                else strings.get(R.string.ui_the_server_will_see_your_public_ip_and_request_time_nipv4_1_s_nip ,v4, v6)) },
            confirmButton = { TextButton(onClick = {
                confirming = null
                if (cloudflare) model.runCloudflare() else {
                    model.saveEndpoints(v4.trim(), v6.trim())
                    model.runExternal()
                }
                onDismiss()
            }) { Text(strings.get(R.string.ui_start_check)) } },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(strings.get(R.string.ui_cancel)) } })
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(strings.get(R.string.ui_public_checks)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Cloudflare", style = MaterialTheme.typography.titleMedium)
                Text(strings.get(R.string.ui_check_ipv4_ipv6_connectivity_and_public_ip_addresses))
                androidx.compose.foundation.text.selection.SelectionContainer { Text(CloudflareTrace.ENDPOINT) }
                Button(onClick = { confirming = "cloudflare" }, enabled = !model.externalBusy) { Text(strings.get(R.string.ui_check_cloudflare)) }
                TextButton(onClick = { custom = !custom }) { Text(if (custom) strings.get(R.string.ui_collapse_server_configuration) else strings.get(R.string.ui_configure_your_server)) }
                if (custom) {
                HorizontalDivider()
                Text(strings.get(R.string.ui_your_server), style = MaterialTheme.typography.titleMedium)
                Text(strings.get(R.string.ui_enter_https_urls_supporting_only_ipv4_and_only_ipv6_respectively))
                OutlinedTextField(value = v4, onValueChange = { v4 = it }, label = { Text(strings.get(R.string.endpoint_ipv4_url_label)) }, singleLine = true)
                OutlinedTextField(value = v6, onValueChange = { v6 = it }, label = { Text(strings.get(R.string.endpoint_ipv6_url_label)) }, singleLine = true)
                Row { Checkbox(checked = owned, onCheckedChange = { owned = it }); Text(strings.get(R.string.ui_i_own_or_administer_these_domains)) }
                if (!valid) Text(strings.get(R.string.ui_enter_two_different_https_hostnames_f77e02))
                OutlinedButton(onClick = { model.saveEndpoints(v4.trim(), v6.trim()) }, enabled = valid) { Text(strings.get(R.string.ui_save_configuration)) }
                }
            }
        },
        confirmButton = {
            if (custom) TextButton(onClick = { confirming = "owned" }, enabled = valid && owned && !model.externalBusy) { Text(strings.get(R.string.ui_check_your_server)) }
            else TextButton(onClick = onDismiss) { Text(strings.get(R.string.ui_close)) }
        },
        dismissButton = { if (custom) TextButton(onClick = onDismiss) { Text(strings.get(R.string.ui_back)) } })
}
