package com.rnotlnglgq.netprobe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun PermissionScreen(model: ProbeViewModel, onDismiss: () -> Unit, onRequest: (String) -> Unit, onSettings: () -> Unit) {
    var comparisonDetails by remember { mutableStateOf(false) }
    var explanation by remember { mutableStateOf<String?>(null) }
    if (explanation != null) {
        val kind = explanation!!
        AlertDialog(onDismissRequest = { explanation = null }, title = { Text(if (kind == "phone") "SIM 与网络制式" else "Wi-Fi 权限") },
            text = { Text(if (kind == "phone")
                "电话权限用于列出当前订阅、读取系统保留的订阅记录和数据网络制式。历史记录需在订阅页手动读取。"
                else "读取 Wi-Fi 名称和 BSSID 需要精确位置权限，并打开系统位置开关。") },
            confirmButton = { TextButton(onClick = {
                explanation = null
                if (model.beginExperiment(kind)) { onDismiss(); onRequest(kind) }
            }) { Text("授权并记录对照") } },
            dismissButton = { TextButton(onClick = { explanation = null }) { Text("取消") } })
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("权限与对照") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("选择要读取的信息。关闭后停止读取，设置会保留。")
                Text("当前采样：${model.snapshot?.capturedAtUtc ?: "等待首次采样"}")
                Row {
                    Text("订阅读取 · 电话状态权限", modifier = Modifier.weight(1f))
                    Switch(checked = model.phoneExperiment, onCheckedChange = { model.setExperiment("phone", it) })
                }
                Row {
                    Text("Wi-Fi 读取 · 精确位置权限", modifier = Modifier.weight(1f))
                    Switch(checked = model.wifiExperiment, onCheckedChange = { model.setExperiment("wifi", it) })
                }
                Button(onClick = { explanation = "phone" }, enabled = model.snapshot != null && !model.busy) { Text("授权订阅读取") }
                Button(onClick = { explanation = "wifi" }, enabled = model.snapshot != null && !model.busy) { Text("授权 Wi-Fi 读取") }
                OutlinedButton(onClick = model::disableExperiments) { Text("关闭这两项读取") }
                TextButton(onClick = onSettings) { Text("应用权限设置") }
                model.comparison?.let { comparison ->
                    Text("对照：${if (comparison.experiment == "wifi") "Wi-Fi" else "SIM"}")
                    Text("前：${comparison.before.capturedAtUtc}\n后：${comparison.after.capturedAtUtc}")
                    Text("${comparison.changedPaths.size} 个字段有变化")

                    TextButton(onClick = { comparisonDetails = !comparisonDetails }) { Text(if (comparisonDetails) "收起差异" else "查看差异") }
                    if (comparisonDetails) comparison.changedPaths.forEach { path ->
                        val before = comparison.before.fieldResults.firstOrNull { it.path == path }
                        val after = comparison.after.fieldResults.firstOrNull { it.path == path }
                        Text("${Presentation.label(path)}：${before?.let { Presentation.status(it.status) } ?: "未采集"} → ${after?.let { Presentation.status(it.status) } ?: "未采集"}")
                        if (model.reveal) androidx.compose.foundation.text.selection.SelectionContainer { Text("${com.rnotlnglgq.netprobe.data.JsonExport.gson.toJson(before?.value)} → ${com.rnotlnglgq.netprobe.data.JsonExport.gson.toJson(after?.value)}") }
                    }
                }
                TextButton(onClick = model::resampleComparison, enabled = model.baselineAvailable) { Text("重新采样对照") }
                Text("导出 JSON 包含本次对照。")
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("返回") } })
}
