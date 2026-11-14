package com.rnotlnglgq.netprobe.probe

import android.content.Context
import android.net.*
import android.os.Handler
import android.os.Looper

/** All observer state and delivery run on the main looper. No synchronous network getters here. */
class NetworkChangeObserver(context: Context, private val onSample: (NetworkSample) -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    val isRegistered: Boolean get() = callback != null

    fun start() {
        if (callback != null) return
        val next = object : ConnectivityManager.NetworkCallback() {
            var network: Network? = null
            var caps: NetworkCapabilities? = null
            var links: LinkProperties? = null
            fun deliver(event: String) {
                if (callback !== this) return
                onSample(NetworkSample(network, caps, links, event = event))
            }
            override fun onAvailable(network: Network) {
                this.network = network
                caps = null
                links = null
            }
            override fun onCapabilitiesChanged(network: Network, nc: NetworkCapabilities) {
                if (this.network != network) return
                caps = NetworkCapabilities(nc)
                if (links != null) deliver("capabilities_changed")
            }
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                if (this.network != network) return
                links = lp // Callback-owned data is retained read-only; the copy constructor is not public SDK.
                deliver("link_properties_changed")
            }
            override fun onLost(network: Network) {
                if (this.network != network) return
                this.network = null
                caps = null
                links = null
                deliver("default_network_lost")
            }
        }
        manager.registerDefaultNetworkCallback(next, Handler(Looper.getMainLooper()))
        callback = next
    }

    fun stop() {
        val old = callback ?: return
        callback = null
        manager.unregisterNetworkCallback(old)
    }
}
