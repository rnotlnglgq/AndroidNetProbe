package com.rnotlnglgq.netprobe.probe

import android.content.Context
import android.net.*
import android.os.Build
import android.system.OsConstants
import com.rnotlnglgq.netprobe.model.*
import java.time.Instant
import java.util.UUID

/** Read-only callback arguments; snapshots across APIs are not atomic. */
data class NetworkSample(
    val network: Network?, val capabilities: NetworkCapabilities?, val links: LinkProperties?,
    val capturedAtUtc: String = Instant.now().toString(), val event: String = "callback"
)

data class NetworkRead(val fields: List<ProbeField<*>>, val warnings: List<String>)

class NetworkSnapshotProbe(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val sessionIds = linkedMapOf<Network, String>()
    private fun sessionId(network: Network): String = sessionIds.getOrPut(network) {
        if (sessionIds.size >= 64) sessionIds.remove(sessionIds.keys.first())
        "net-${UUID.randomUUID()}"
    }

    fun collect(sample: NetworkSample? = null): NetworkRead {
        val fields = mutableListOf<ProbeField<*>>()
        val warnings = mutableListOf<String>()
        val permission = "ACCESS_NETWORK_STATE"
        val active = readField("defaultNetwork.state", (if (sample == null) "ConnectivityManager.activeNetwork" else "NetworkCallback network argument"), permission, sensitive = false) {
            sample?.let { it.network } ?: if (sample == null) manager.activeNetwork else null
        }
        val network = active.value
        fields += ProbeField("defaultNetwork.state", if (network != null) "available" else null,
            if (active.status == FieldStatus.empty) FieldStatus.network_unavailable else active.status,
            active.source, permission, active.note, active.errorType, sensitive = false)
        val caps = readField("defaultNetwork.capabilities", "ConnectivityManager.getNetworkCapabilities", permission, sensitive = false) {
            if (sample != null) sample.capabilities else network?.let { manager.getNetworkCapabilities(it) }
        }
        val links = readField("defaultNetwork.linkProperties", "ConnectivityManager.getLinkProperties", permission, sensitive = false) {
            if (sample != null) sample.links else network?.let { manager.getLinkProperties(it) }
        }
        fun <T> add(path: String, source: String, dependency: ProbeField<*>? = null,
            minApi: Int = 26, sensitive: Boolean = false, note: String? = null, block: () -> T?) {
            fields += when {
                Build.VERSION.SDK_INT < minApi -> unavailable(path, source, FieldStatus.unsupported_api,
                    permission, "需要 API $minApi", sensitive)
                network == null -> unavailable(path, source,
                    if (active.status == FieldStatus.empty) FieldStatus.network_unavailable else active.status,
                    permission, "当前默认网络不可见或不存在", sensitive)
                dependency != null && dependency.value == null -> unavailable(path, source,
                    if (dependency.status == FieldStatus.empty) FieldStatus.network_unavailable else dependency.status,
                    permission, "网络属性暂不可用，可能正在切换", sensitive)
                else -> readField(path, source, permission, sensitive, note, block)
            }
        }
        add("defaultNetwork.sessionNetworkId", "session_random_uuid") { network?.let { sessionId(it) } }
        add(FieldKeys.DEFAULT_TRANSPORTS, "NetworkCapabilities.hasTransport", caps) {
            val choices = mutableListOf(
                NetworkCapabilities.TRANSPORT_WIFI to "WIFI", NetworkCapabilities.TRANSPORT_CELLULAR to "CELLULAR",
                NetworkCapabilities.TRANSPORT_VPN to "VPN", NetworkCapabilities.TRANSPORT_ETHERNET to "ETHERNET",
                NetworkCapabilities.TRANSPORT_BLUETOOTH to "BLUETOOTH", NetworkCapabilities.TRANSPORT_WIFI_AWARE to "WIFI_AWARE")
            if (Build.VERSION.SDK_INT >= 27) choices += NetworkCapabilities.TRANSPORT_LOWPAN to "LOWPAN"
            if (Build.VERSION.SDK_INT >= 31) choices += NetworkCapabilities.TRANSPORT_USB to "USB"
            if (Build.VERSION.SDK_INT >= 34) choices += NetworkCapabilities.TRANSPORT_THREAD to "THREAD"
            if (Build.VERSION.SDK_INT >= 35) choices += NetworkCapabilities.TRANSPORT_SATELLITE to "SATELLITE"
            choices.filter { caps.value!!.hasTransport(it.first) }.map { it.second }
        }
        add(FieldKeys.DEFAULT_VALIDATED, "NetworkCapabilities.hasCapability(VALIDATED)", caps) {
            caps.value!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        add("defaultNetwork.internetCapability", "NetworkCapabilities.hasCapability(INTERNET)", caps) {
            caps.value!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        add(FieldKeys.DEFAULT_METERED, "NetworkCapabilities.hasCapability(NOT_METERED)", caps) {
            !caps.value!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        add(FieldKeys.DEFAULT_VPN, "NetworkCapabilities.hasTransport(VPN)", caps,
            note = "仅描述 App 默认网络；未主动枚举 VPN 底层网络") {
            caps.value!!.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
        add("defaultNetwork.downstreamKbps", "NetworkCapabilities.getLinkDownstreamBandwidthKbps", caps,
            note = "系统估计的第一跳带宽，非测速结果") { caps.value!!.linkDownstreamBandwidthKbps }
        add("defaultNetwork.upstreamKbps", "NetworkCapabilities.getLinkUpstreamBandwidthKbps", caps,
            note = "系统估计的第一跳带宽，非测速结果") { caps.value!!.linkUpstreamBandwidthKbps }
        add("defaultNetwork.signalStrength", "NetworkCapabilities.getSignalStrength", caps, 29,
            note = "单位随传输类型变化；系统未提供时为空") {
            if (Build.VERSION.SDK_INT >= 29) caps.value!!.signalStrength.takeUnless { it == Int.MIN_VALUE } else null
        }
        add("defaultNetwork.captivePortal", "NetworkCapabilities.hasCapability(CAPTIVE_PORTAL)", caps) {
            caps.value!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        }
        add("defaultNetwork.notRestricted", "NetworkCapabilities.hasCapability(NOT_RESTRICTED)", caps) {
            caps.value!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        }
        add("defaultNetwork.notSuspended", "NetworkCapabilities.hasCapability(NOT_SUSPENDED)", caps, 28) {
            if (Build.VERSION.SDK_INT >= 28) caps.value!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED) else null
        }
        add(FieldKeys.DEFAULT_INTERFACE, "LinkProperties.getInterfaceName", links) { links.value!!.interfaceName }
        fun addresses() = links.value!!.linkAddresses.map { link ->
            AddressRecord(link.address.hostAddress ?: "", if (link.address.address.size == 16) "IPv6" else "IPv4",
                link.prefixLength, IpClassifier.classify(link.address), link.scope, link.flags,
                if (link.address.address.size == 16) link.flags and OsConstants.IFA_F_TEMPORARY != 0 else null,
                link.flags and OsConstants.IFA_F_DEPRECATED != 0, "link_address_flags", FieldStatus.available)
        }
        add(FieldKeys.DEFAULT_ADDRESSES, "LinkProperties.getLinkAddresses / LinkAddress getters", links,
            sensitive = true) { addresses() }
        add("ipv4.defaultNetworkAddresses", "LinkProperties.getLinkAddresses / LinkAddress getters", links,
            sensitive = true) { addresses().filter { it.family == "IPv4" } }
        add("ipv6.defaultNetworkAddresses", "LinkProperties.getLinkAddresses / LinkAddress getters", links,
            sensitive = true) { addresses().filter { it.family == "IPv6" } }
        add("defaultNetwork.dnsServers", "LinkProperties.getDnsServers", links, sensitive = true) {
            links.value!!.dnsServers.map { it.hostAddress }
        }
        add("defaultNetwork.routes", "LinkProperties.getRoutes / RouteInfo getters", links, sensitive = true,
            note = "RouteInfo.type 需要 API 33；低版本为 null") {
            links.value!!.routes.map { r -> RouteRecord(r.destination.address.hostAddress ?: "",
                r.destination.prefixLength, r.gateway?.hostAddress, r.`interface`,
                if (Build.VERSION.SDK_INT >= 33) r.type else null) }
        }
        add("defaultNetwork.mtu", "LinkProperties.getMtu", links, 29,
            note = "0 表示未设置非默认 MTU，不代表实际链路 MTU 为零") { if (Build.VERSION.SDK_INT >= 29) links.value!!.mtu else null }
        add("defaultNetwork.privateDns.active", "LinkProperties.isPrivateDnsActive", links, 28) { if (Build.VERSION.SDK_INT >= 28) links.value!!.isPrivateDnsActive else null }
        add("defaultNetwork.privateDns.serverName", "LinkProperties.getPrivateDnsServerName", links, 28,
            sensitive = true) { if (Build.VERSION.SDK_INT >= 28) links.value!!.privateDnsServerName else null }
        add("defaultNetwork.nat64Prefix", "LinkProperties.getNat64Prefix", links, 30, sensitive = true) {
            if (Build.VERSION.SDK_INT >= 30) links.value!!.nat64Prefix?.let { PrefixRecord(it.address.hostAddress ?: "", it.prefixLength) } else null
        }
        add("defaultNetwork.dnsSearchDomains", "LinkProperties.getDomains", links, sensitive = true) { links.value!!.domains }
        add("defaultNetwork.httpProxy", "LinkProperties.getHttpProxy / ProxyInfo getters", links, sensitive = true,
            note = "仅显示代理配置，不访问 PAC URL") {
            links.value!!.httpProxy?.let { ProxyRecord(it.host, it.port, it.exclusionList.toList(), it.pacFileUrl?.toString()) }
        }
        if (sample != null) warnings += "回调参数为同一默认网络的最近属性；接口和运营商在事件之后采样，不保证跨 API 原子性"
        return NetworkRead(fields, warnings)
    }
}

data class PrefixRecord(val address: String, val prefixLength: Int)

data class ProxyRecord(val host: String?, val port: Int, val exclusions: List<String>, val pacUrl: String?)
