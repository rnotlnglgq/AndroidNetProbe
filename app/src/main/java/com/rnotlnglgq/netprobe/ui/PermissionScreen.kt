package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun PermissionScreen(model: ProbeViewModel, onDismiss: () -> Unit, onRequest: (String) -> Unit, onSettings: () -> Unit) {
    val strings = rememberUiStrings()
    val presentation = androidx.compose.runtime.remember(strings) { Presentation(strings) }
    var comparisonDetails by remember { mutableStateOf(false) }
    var explanation by remember { mutableStateOf<String?>(null) }
    if (explanation != null) {
        val kind = explanation!!
        AlertDialog(onDismissRequest = { explanation = null }, title = { Text(if (kind == "phone") strings.get(R.string.ui_sim_and_network_technology) else strings.get(R.string.ui_wi_fi_permissions)) },
            text = { Text(if (kind == "phone")
                strings.get(R.string.ui_phone_permission_allows_reading_current_subscriptions_system_reta)
                else strings.get(R.string.ui_reading_the_wi_fi_name_and_bssid_requires_precise_location_permis)) },
            confirmButton = { TextButton(onClick = {
                explanation = null
                if (model.beginExperiment(kind)) { onDismiss(); onRequest(kind) }
            }) { Text(strings.get(R.string.ui_grant_and_record_comparison)) } },
            dismissButton = { TextButton(onClick = { explanation = null }) { Text(strings.get(R.string.ui_cancel)) } })
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(strings.get(R.string.ui_permissions_and_comparison)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(strings.get(R.string.ui_choose_the_information_to_read_turning_a_switch_off_stops_reading))
                Text(strings.get(R.string.ui_current_sample_1_s ,model.snapshot?.capturedAtUtc ?: strings.get(R.string.ui_waiting_for_the_first_sample)))
                Row {
                    Text(strings.get(R.string.ui_subscriptions_phone_state_permission), modifier = Modifier.weight(1f))
                    Switch(checked = model.phoneExperiment, onCheckedChange = { model.setExperiment("phone", it) })
                }
                Row {
                    Text(strings.get(R.string.ui_wi_fi_precise_location_permission), modifier = Modifier.weight(1f))
                    Switch(checked = model.wifiExperiment, onCheckedChange = { model.setExperiment("wifi", it) })
                }
                Button(onClick = { explanation = "phone" }, enabled = model.snapshot != null && !model.busy) { Text(strings.get(R.string.ui_allow_subscription_reading)) }
                Button(onClick = { explanation = "wifi" }, enabled = model.snapshot != null && !model.busy) { Text(strings.get(R.string.ui_allow_wi_fi_reading)) }
                OutlinedButton(onClick = model::disableExperiments) { Text(strings.get(R.string.ui_turn_off_both_readers)) }
                TextButton(onClick = onSettings) { Text(strings.get(R.string.ui_app_permission_settings)) }
                model.comparison?.let { comparison ->
                    Text(strings.get(R.string.ui_comparison_1_s ,if (comparison.experiment == "wifi") "Wi-Fi" else "SIM"))
                    Text(strings.get(R.string.ui_before_1_s_nafter_2_s ,comparison.before.capturedAtUtc, comparison.after.capturedAtUtc))
                    Text(strings.quantity(R.plurals.changed_field_count,comparison.changedPaths.size))

                    TextButton(onClick = { comparisonDetails = !comparisonDetails }) { Text(if (comparisonDetails) strings.get(R.string.ui_collapse_differences) else strings.get(R.string.ui_view_differences)) }
                    if (comparisonDetails) comparison.changedPaths.forEach { path ->
                        val before = comparison.before.fieldResults.firstOrNull { it.path == path }
                        val after = comparison.after.fieldResults.firstOrNull { it.path == path }
                        Text("${presentation.label(path)}：${before?.let { presentation.status(it.status) } ?: strings.get(R.string.ui_not_collected)} → ${after?.let { presentation.status(it.status) } ?: strings.get(R.string.ui_not_collected)}")
                        if (model.reveal) androidx.compose.foundation.text.selection.SelectionContainer { Text("${com.rnotlnglgq.netprobe.data.JsonExport.gson.toJson(before?.value)} → ${com.rnotlnglgq.netprobe.data.JsonExport.gson.toJson(after?.value)}") }
                    }
                }
                TextButton(onClick = model::resampleComparison, enabled = model.baselineAvailable) { Text(strings.get(R.string.ui_sample_comparison_again)) }
                Text(strings.get(R.string.ui_exported_json_includes_this_comparison))
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(strings.get(R.string.ui_back)) } })
}
