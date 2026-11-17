package com.rnotlnglgq.netprobe.probe

import com.rnotlnglgq.netprobe.model.*
import java.net.NetworkInterface
import java.util.Collections

class InterfaceProbe {
    fun collect(): List<ProbeField<*>> {
        val all = readField("visibleInterfaces.enumeration", "NetworkInterface.getNetworkInterfaces",
            note = DiagnosticCatalog.legacy("interfaces_accessible_to_this_app_scope_differs_from_default_netw"), sensitive = false) {
            NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }
        }
        val fields = mutableListOf<ProbeField<*>>()
        fields += ProbeField("visibleInterfaces.count", all.value?.size, all.status, all.source,
            all.permission, all.note, all.errorType, sensitive = false)
        all.value?.forEachIndexed { i, face ->
            val path = "visibleInterfaces[$i]"
            fields += readField("$path.name", "NetworkInterface.getName", sensitive = false) { face.name }
            fields += readField("$path.up", "NetworkInterface.isUp", sensitive = false) { face.isUp }
            fields += readField("$path.loopback", "NetworkInterface.isLoopback", sensitive = false) { face.isLoopback }
            fields += readField("$path.mtu", "NetworkInterface.getMTU", sensitive = false) { face.mtu }
            fields += readField("$path.addresses", "NetworkInterface.getInterfaceAddresses",
                sensitive = true, note = DiagnosticCatalog.legacy("the_java_api_provides_addresses_linkaddress_flags_and_scope_remai")) {
                face.interfaceAddresses.map { entry ->
                    AddressRecord(entry.address.hostAddress ?: "",
                        if (entry.address.address.size == 16) "IPv6" else "IPv4",
                        entry.networkPrefixLength.toInt(), IpClassifier.classify(entry.address),
                        flagsSource = "NetworkInterface: flags unavailable", flagsStatus = FieldStatus.unsupported_api)
                }
            }
        }
        return fields
    }
}
