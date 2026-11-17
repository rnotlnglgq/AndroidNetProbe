package com.rnotlnglgq.netprobe.probe

import com.rnotlnglgq.netprobe.model.DiagnosticCatalog

/** Only retain diagnostic fields we display; never serialize the arbitrary server body. */
data class CloudflareObservation(
    val observedIp: String,
    val ipFamily: String,
    val serverUnixTime: String?,
    val colo: String?,
    val countryCode: String?,
    val tls: String?,
    val http: String?,
    val warp: String?,
    val note: String = DiagnosticCatalog.legacy("cloudflare_observation_for_this_request_system_dns_and_single_sta")
)

object CloudflareTrace {
    const val ENDPOINT = "https://www.cloudflare.com/cdn-cgi/trace"
    fun parse(body: String): CloudflareObservation {
        if (body.length > 16_384) throw EndpointResponseException()
        val values = linkedMapOf<String, String>()
        body.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isNotEmpty()) {
                if ('=' !in line || line.length > 2048) throw EndpointResponseException()
                val key = line.substringBefore('=')
                val value = line.substringAfter('=')
                if (!key.matches(Regex("[a-zA-Z0-9_]{1,64}")) || values.put(key, value) != null)
                    throw EndpointResponseException()
            }
        }
        val ip = values["ip"] ?: throw EndpointResponseException()
        val family = EndpointPolicy.numericFamily(ip) ?: throw EndpointResponseException()
        fun safe(key: String, regex: String): String? = values[key]?.let {
            if (!it.matches(Regex(regex))) throw EndpointResponseException()
            it
        }
        return CloudflareObservation(ip, family, safe("ts", "[0-9]{1,16}([.][0-9]{1,9})?"),
            safe("colo", "[A-Z]{3}"), safe("loc", "[A-Z]{2}"),
            safe("tls", "[a-zA-Z0-9._ -]{1,32}"), safe("http", "[a-zA-Z0-9./_-]{1,32}"),
            safe("warp", "[a-zA-Z0-9_-]{1,32}"))
    }
}
