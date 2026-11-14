package com.rnotlnglgq.netprobe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun ExportScreen(onDismiss: () -> Unit, onExport: (Boolean) -> Unit) {
    var raw by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("导出 JSON") },
        text = {
            Column {
                Text("默认隐藏地址和运营商信息。")
                Row {
                    Checkbox(checked = raw, onCheckedChange = { raw = it })
                    Text("包含完整数据")
                }
                Text("选择云盘时，文件可能由云盘同步。")
            }
        },
        confirmButton = { TextButton(onClick = { onExport(raw) }) { Text("保存到…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
