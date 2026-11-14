package com.rnotlnglgq.netprobe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.rnotlnglgq.netprobe.probe.*

@Composable
fun EndpointScreen(model: ProbeViewModel, onDismiss: () -> Unit) {
    var v4 by remember { mutableStateOf(model.endpointIpv4) }
    var v6 by remember { mutableStateOf(model.endpointIpv6) }
    var custom by remember { mutableStateOf(false) }
    var owned by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<String?>(null) }
    val valid = EndpointPolicy.valid(EndpointConfig(v4.trim(), "IPv4")) && EndpointPolicy.valid(EndpointConfig(v6.trim(), "IPv6")) &&
        runCatching { EndpointPolicy.parse(EndpointConfig(v4.trim(), "IPv4")).host != EndpointPolicy.parse(EndpointConfig(v6.trim(), "IPv6")).host }.getOrDefault(false)
    if (confirming != null) {
        val cloudflare = confirming == "cloudflare"
        AlertDialog(onDismissRequest = { confirming = null }, title = { Text(if (cloudflare) "连接 Cloudflare？" else "连接自有服务器？") },
            text = { Text(if (cloudflare)
                "分别检测 IPv4 和 IPv6。Cloudflare 会看到你的出口 IP 和请求时间。\n${CloudflareTrace.ENDPOINT}"
                else "服务器会看到你的出口 IP 和请求时间。\nIPv4：$v4\nIPv6：$v6") },
            confirmButton = { TextButton(onClick = {
                confirming = null
                if (cloudflare) model.runCloudflare() else {
                    model.saveEndpoints(v4.trim(), v6.trim())
                    model.runExternal()
                }
                onDismiss()
            }) { Text("开始检测") } },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("取消") } })
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("公网检测") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Cloudflare", style = MaterialTheme.typography.titleMedium)
                Text("检测 IPv4 / IPv6 连通性和出口 IP。")
                androidx.compose.foundation.text.selection.SelectionContainer { Text(CloudflareTrace.ENDPOINT) }
                Button(onClick = { confirming = "cloudflare" }, enabled = !model.externalBusy) { Text("检测 Cloudflare…") }
                TextButton(onClick = { custom = !custom }) { Text(if (custom) "收起自有服务器" else "配置自有服务器") }
                if (custom) {
                HorizontalDivider()
                Text("自有服务器", style = MaterialTheme.typography.titleMedium)
                Text("填写分别只支持 IPv4 和 IPv6 的 HTTPS 地址。")
                OutlinedTextField(value = v4, onValueChange = { v4 = it }, label = { Text("IPv4-only HTTPS URL") }, singleLine = true)
                OutlinedTextField(value = v6, onValueChange = { v6 = it }, label = { Text("IPv6-only HTTPS URL") }, singleLine = true)
                Row { Checkbox(checked = owned, onCheckedChange = { owned = it }); Text("这些域名由我拥有或管理") }
                if (!valid) Text("请填写两个不同的 HTTPS 域名。")
                OutlinedButton(onClick = { model.saveEndpoints(v4.trim(), v6.trim()) }, enabled = valid) { Text("保存配置") }
                }
            }
        },
        confirmButton = {
            if (custom) TextButton(onClick = { confirming = "owned" }, enabled = valid && owned && !model.externalBusy) { Text("检测自有服务器…") }
            else TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = { if (custom) TextButton(onClick = onDismiss) { Text("返回") } })
}
