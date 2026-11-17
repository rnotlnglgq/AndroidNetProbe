package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun ExportScreen(onDismiss: () -> Unit, onExport: (Boolean) -> Unit) {
    val strings = rememberUiStrings()
    var raw by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(strings.get(R.string.ui_export_json)) },
        text = {
            Column {
                Text(strings.get(R.string.ui_addresses_and_carrier_information_are_hidden_by_default))
                Row {
                    Checkbox(checked = raw, onCheckedChange = { raw = it })
                    Text(strings.get(R.string.ui_include_full_data))
                }
                Text(strings.get(R.string.ui_files_saved_to_cloud_storage_may_be_synced_by_its_provider))
            }
        },
        confirmButton = { TextButton(onClick = { onExport(raw) }) { Text(strings.get(R.string.ui_save_to)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.get(R.string.ui_cancel)) } })
}
