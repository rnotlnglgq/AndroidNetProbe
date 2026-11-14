package com.rnotlnglgq.netprobe.ui

import com.rnotlnglgq.netprobe.model.*
import java.net.InetAddress
import com.rnotlnglgq.netprobe.probe.EndpointPolicy

/** UI projections only. Collection and export keep their original fields and values. */
data class FieldGroup(val key: String, val title: String, val fields: List<ProbeField<*>>)

object Presentation {
    fun indexedGroups(fields: List<ProbeField<*>>, root: String, defaultName: String): List<FieldGroup> {
        val pattern = Regex("^${Regex.escape(root)}\\[(\\d+)]\\.")
        return fields.mapNotNull { field -> pattern.find(field.path)?.groupValues?.get(1)?.let { it to field } }
            .groupBy({ it.first }, { it.second }).toSortedMap(compareBy { it.toInt() }).map { (index, group) ->
                val name = group.firstOrNull { it.path.endsWith(".name") }?.value as? String
                val session = group.firstOrNull { it.path.endsWith(".sessionSubscriptionId") }?.value as? String
                FieldGroup(name ?: session ?: "$root[$index]", name ?: "$defaultName ${index.toInt() + 1}", group)
            }
    }
    fun selected(groups: List<FieldGroup>, key: String?): FieldGroup? = groups.firstOrNull { it.key == key } ?: groups.firstOrNull()
    fun addresses(value: Any?): List<AddressRecord> = (value as? List<*>)?.filterIsInstance<AddressRecord>().orEmpty()
    fun status(status: FieldStatus): String = when (status) {
        FieldStatus.available -> "可用"
        FieldStatus.empty -> "无数据"
        FieldStatus.permission_denied -> "未授权"
        FieldStatus.redacted -> "已脱敏"
        FieldStatus.unsupported_api -> "当前系统不支持"
        FieldStatus.unsupported_feature -> "设备不支持"
        FieldStatus.network_unavailable -> "无网络"
        FieldStatus.error -> "读取失败"
        FieldStatus.not_requested -> "未启用"
    }
    fun text(value: Any?): String = when (value) {
        null -> "无数据"
        true -> "是"
        false -> "否"
        is String -> when (value) {
            "granted" -> "已授权"; "denied" -> "未授权"; "not_declared" -> "未声明"
            "available" -> "可用"; "WIFI" -> "Wi-Fi"; "CELLULAR" -> "蜂窝"; "ETHERNET" -> "以太网"
            else -> value
        }
        is Number -> value.toString()
        is List<*> -> value.joinToString("、") { text(it) }
        else -> "结构化数据"
    }
    fun fieldText(field: ProbeField<*>): String {
        val n = (field.value as? Number)?.toInt()
        val key = field.path.substringAfterLast('.')
        if (field.path == "defaultNetwork.mtu" && n == 0) return "系统默认"
        if (n != null) {
            val label = when (key) {
                "dataNetworkType", "accessNetworkTechnology" -> radioType(n)
                "simState" -> mapOf(0 to "未知", 1 to "未检测到 SIM", 2 to "需要 PIN", 3 to "需要 PUK", 4 to "网络锁定", 5 to "就绪", 6 to "未就绪", 7 to "已永久停用", 8 to "卡读取错误", 9 to "受限", 10 to "已加载")[n]
                "slotIndex" -> if (n >= 0) "卡槽 ${n + 1}" else "无有效卡槽"
                "dataRoaming" -> mapOf(0 to "关闭", 1 to "开启")[n]
                "voiceState" -> mapOf(0 to "有服务", 1 to "无服务", 2 to "仅紧急呼叫", 3 to "无线电已关闭")[n]
                "dataState" -> mapOf(0 to "已断开", 1 to "连接中", 2 to "已连接", 3 to "已暂停", 4 to "断开中")[n]
                "standard" -> mapOf(0 to "未知", 1 to "传统 Wi-Fi", 4 to "802.11n", 5 to "802.11ac", 6 to "802.11ax", 7 to "802.11ad", 8 to "802.11be")[n]
                else -> null
            }
            if (label != null) return label
        }
        return text(field.value)
    }
    fun radioType(value: Int): String = mapOf(0 to "未知", 1 to "GPRS", 2 to "EDGE", 3 to "UMTS", 4 to "CDMA",
        5 to "EVDO 0", 6 to "EVDO A", 7 to "1xRTT", 8 to "HSDPA", 9 to "HSUPA", 10 to "HSPA", 11 to "iDEN",
        12 to "EVDO B", 13 to "LTE", 14 to "eHRPD", 15 to "HSPA+", 16 to "GSM", 17 to "TD-SCDMA", 18 to "IWLAN", 19 to "LTE CA", 20 to "NR")[value] ?: value.toString()
    fun endpointState(state: String): String = when (state) {
        "success" -> "连接成功"; "dns_error" -> "DNS 解析失败"; "timeout" -> "超时"
        "tls_error" -> "TLS 验证失败"; "connection_error" -> "连接失败"; "network_unavailable" -> "无网络"
        "address_family_unavailable" -> "无此协议地址"; "configuration_error" -> "配置或地址族不符"
        "http_error" -> "HTTP 错误"; "invalid_response" -> "响应格式错误"; else -> state
    }
    fun addressType(address: AddressRecord): String {
        // Numeric input only: never resolve names to format a UI label.
        val raw = address.address.substringBefore('%')
        val bytes = if (EndpointPolicy.numericFamily(raw) != null)
            runCatching { InetAddress.getByName(raw).address }.getOrNull() else null
        if (bytes != null && bytes.size == 16) {
            if (bytes.all { it == 0.toByte() }) return "未指定"
            if (bytes.dropLast(1).all { it == 0.toByte() } && bytes.last() == 1.toByte()) return "回环"
        }
        if (bytes?.size == 16 && bytes.take(4).map { it.toInt() and 255 } == listOf(0x20, 1, 0x0d, 0xb8)) return "文档保留"
        if (bytes?.size == 4) {
            val b = bytes.map { it.toInt() and 255 }
            if (b.take(3) in listOf(listOf(192, 0, 2), listOf(198, 51, 100), listOf(203, 0, 113))) return "文档保留"
        }
        return when (address.classification) {
            "link-local" -> "链路本地"
            "unique-local" -> "唯一本地（内网）"
            "global-unicast" -> "全局单播"
            "private" -> "私有内网"
            "shared-cgnat" -> "共享地址（CGNAT）"
            "loopback" -> "回环"
            "unspecified" -> "未指定"
            "multicast" -> "组播"
            "other-ipv4" -> "其他 IPv4"
            else -> "其他地址"
        }
    }
    fun label(path: String): String {
        if (path == "externalChecks.ipv4.result") return "IPv4 连接"
        if (path == "externalChecks.ipv6.result") return "IPv6 连接"
        if (path.endsWith("serviceState.state")) return "服务状态"
        if (path.endsWith("signalStrength.state")) return "信号状态"
        val key = path.substringAfterLast('.')
        return labels[key] ?: key
    }
    private val labels = mapOf(
        "state" to "状态", "sessionNetworkId" to "会话网络 ID", "transports" to "连接方式", "validated" to "互联网验证",
        "internetCapability" to "互联网能力", "metered" to "按流量计费", "vpn" to "VPN", "interfaceName" to "接口",
        "name" to "名称", "up" to "已启用", "loopback" to "回环接口", "mtu" to "MTU", "count" to "接口数",
        "downstreamKbps" to "下行带宽估计（Kbps）", "upstreamKbps" to "上行带宽估计（Kbps）", "signalStrength" to "信号强度",
        "captivePortal" to "认证门户", "notRestricted" to "非受限网络", "notSuspended" to "未暂停",
        "linkAddresses" to "链路地址", "defaultNetworkAddresses" to "地址", "addresses" to "接口地址",
        "dnsServers" to "DNS 服务器", "routes" to "路由", "dnsSearchDomains" to "DNS 搜索域", "httpProxy" to "HTTP 代理",
        "active" to "Private DNS", "serverName" to "Private DNS 主机", "nat64Prefix" to "NAT64 前缀",
        "crossCheck" to "接口地址对照", "simOperatorName" to "SIM 运营商", "simOperatorMccMnc" to "SIM MCC/MNC",
        "registeredOperatorName" to "注册运营商", "registeredOperatorMccMnc" to "注册 MCC/MNC", "registeredMccMnc" to "注册 MCC/MNC",
        "simMccMnc" to "SIM MCC/MNC", "simCountryIso" to "SIM 国家", "networkCountryIso" to "网络国家",
        "simState" to "SIM 状态", "networkRoaming" to "网络漫游", "simCarrierId" to "运营商 ID", "simCarrierIdName" to "运营商品牌",
        "simSpecificCarrierId" to "细分运营商 ID", "simSpecificCarrierIdName" to "细分运营商名称",
        "carrierIdFromSimMccMnc" to "MCC/MNC 对应运营商 ID", "activeModemCount" to "活动 Modem 数",
        "activeCount" to "活动订阅数", "sessionSubscriptionId" to "会话订阅 ID", "slotIndex" to "卡槽索引",
        "displayName" to "SIM 名称", "countryIso" to "订阅国家", "carrierId" to "订阅运营商 ID",
        "dataRoaming" to "数据漫游设置", "opportunistic" to "机会性订阅", "subscriptionType" to "订阅类型",
        "portIndex" to "端口索引", "usageSetting" to "使用设置", "onlyNonTerrestrial" to "仅非地面网络",
        "carrierName" to "运营商", "embedded" to "eSIM", "mcc" to "MCC", "mnc" to "MNC", "dataNetworkType" to "数据网络制式",
        "dataState" to "数据连接状态", "voiceState" to "语音服务状态", "operatorNumeric" to "注册 PLMN",
        "operatorAlphaLong" to "运营商长名", "operatorAlphaShort" to "运营商短名", "roaming" to "漫游",
        "manualSelection" to "手动选网", "channelNumber" to "信道", "cellBandwidthsKhz" to "小区带宽（kHz）",
        "duplexMode" to "双工模式", "registrations" to "网络注册", "level" to "信号等级", "radios" to "各制式信号",
        "locationEnabled" to "系统位置开关", "observedAtUtc" to "采样时间", "ssid" to "Wi-Fi 名称", "bssid" to "BSSID",
        "rssiDbm" to "信号（dBm）", "linkSpeedMbps" to "连接速率（Mbps）", "frequencyMhz" to "频率（MHz）",
        "standard" to "Wi-Fi 标准", "txLinkSpeedMbps" to "发送速率（Mbps）", "rxLinkSpeedMbps" to "接收速率（Mbps）",
        "securityType" to "安全类型", "hiddenSsid" to "隐藏 SSID", "manifestDeclared" to "应用声明的权限"
    )
}
