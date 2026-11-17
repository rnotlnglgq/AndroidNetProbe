package com.rnotlnglgq.netprobe.data

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.rnotlnglgq.netprobe.probe.*
import java.time.Instant

/** Android resources and factories are assembled at the application boundary. */
class ProbeServices(private val app: Application) {
    val preferences = app.getSharedPreferences("owned_endpoints", 0)
    val repository = ProbeRepository(app)
    val api: Int get() = Build.VERSION.SDK_INT
    fun phoneGranted() = app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    fun runtimePermissions(): Set<String> = listOf("READ_PHONE_STATE", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "NEARBY_WIFI_DEVICES")
        .filter { app.checkSelfPermission("android.permission.$it") == PackageManager.PERMISSION_GRANTED }.toSet()
    fun observer(callback: (NetworkSample) -> Unit) = NetworkChangeObserver(app,callback)
    fun wifi(callback: () -> Unit) = WifiProbe(app,callback)
    fun properties() = SystemPropertyProbe()
    fun native() = BionicInterfaceProbe()
    fun transport() = AndroidEndpointTransport(app)
    fun now() = Instant.now().toString()
    fun write(uri: Uri, document: String) {
        val stream = app.contentResolver.openOutputStream(uri,"wt") ?: error("No output stream")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(document) }
    }
}
