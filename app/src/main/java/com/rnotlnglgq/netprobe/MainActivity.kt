package com.rnotlnglgq.netprobe

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.rnotlnglgq.netprobe.ui.ProbeScreen
import com.rnotlnglgq.netprobe.ui.ProbeViewModel
import com.rnotlnglgq.netprobe.ui.theme.NetProbeTheme

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[ProbeViewModel::class.java] }
    private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { model.writeExport(it) }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.permissionsReturned() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NetProbeTheme { ProbeScreen(model, onPermissionRequest = { kind ->
            permissions.launch(if (kind == "phone") arrayOf(Manifest.permission.READ_PHONE_STATE)
                else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }, onSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }) { raw ->
            if (model.prepareExport(raw)) exporter.launch("netprobe-${if (raw) "raw" else "redacted"}.json")
        } } }
    }
    override fun onStart() { super.onStart(); model.enterForeground() }
    override fun onStop() { model.leaveForeground(); super.onStop() }
}
