package com.rnotlnglgq.netprobe.probe

import com.google.gson.Gson
import com.rnotlnglgq.netprobe.model.*
import java.net.*
import java.time.Instant
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

class ConfigurationException : Exception()
class EndpointResponseException : Exception()
class NetworkUnavailableException : Exception()
class AddressFamilyUnavailableException : Exception()

data class EndpointConfig(val url: String, val family: String, val responseFormat: String = "owned_json")
data class HttpExchange(val status: Int, val body: String, val peerAddress: String, val peerFamily: String,
    val resolvedFamilies: List<String>, val localAddress: String? = null)
interface EndpointTransport { fun exchange(config: EndpointConfig): HttpExchange }
data class ServerReply(val observedIp: String?, val ipFamily: String?, val serverTimeUtc: String?, val requestId: String?)
data class EndpointResult(
    val endpoint: String, val expectedFamily: String, val state: String,
    val startedAtUtc: String, val endedAtUtc: String, val httpStatus: Int? = null,
    val reply: ServerReply? = null, val peerAddress: String? = null,
    val peerFamily: String? = null, val resolvedFamilies: List<String> = emptyList(),
    val errorType: String? = null,
    val requestMode: String = "specified_network_captured_from_app_default",
    val provider: String = "owned",
    val cloudflare: CloudflareObservation? = null,
    val localAddress: String? = null
)

object EndpointPolicy {
    fun parse(config: EndpointConfig): URI {
        val uri = URI(config.url)
        require(config.family in listOf("IPv4", "IPv6"))
        require(config.responseFormat in listOf("owned_json", "cloudflare_trace"))
        if (config.responseFormat == "cloudflare_trace") require(config.url == CloudflareTrace.ENDPOINT)
        require(config.url.length <= 2048 && uri.scheme == "https" && uri.host != null)
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(uri.host.contains('.') && !uri.host.all { it.isDigit() || it == '.' } && ':' !in uri.host)
        require(uri.port == -1 || uri.port in 1..65535)
        return uri
    }
    fun valid(config: EndpointConfig): Boolean = runCatching { parse(config) }.isSuccess
    fun numericFamily(ip: String): String? {
        if (ip.contains(':') && ip.matches(Regex("[0-9a-fA-F:.]+")))
            return runCatching { if (InetAddress.getByName(ip) is Inet6Address) "IPv6" else "IPv4" }.getOrNull()
        val parts = ip.split('.')
        return if (parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) &&
                    it.toIntOrNull() in 0..255 && (it == "0" || !it.startsWith('0')) }) "IPv4" else null
    }
}

class PublicEndpointProbe(private val transport: EndpointTransport) {
    fun check(config: EndpointConfig): EndpointResult {
        val start = Instant.now().toString()
        var exchange: HttpExchange? = null
        fun result(state: String, reply: ServerReply? = null, error: Exception? = null, trace: CloudflareObservation? = null) = EndpointResult(
            config.url, config.family, state, start, Instant.now().toString(), exchange?.status, reply,
            exchange?.peerAddress, exchange?.peerFamily, exchange?.resolvedFamilies.orEmpty(), error?.javaClass?.simpleName,
            provider = if (config.responseFormat == "cloudflare_trace") "cloudflare" else "owned", cloudflare = trace, localAddress = exchange?.localAddress)
        if (!EndpointPolicy.valid(config)) return result("configuration_error")
        return try {
            exchange = transport.exchange(config)
            if (exchange!!.status !in 200..299) return result("http_error")
            if (exchange!!.peerFamily != config.family || config.responseFormat == "owned_json" && exchange!!.resolvedFamilies.any { it != config.family })
                return result("configuration_error")
            if (config.responseFormat == "cloudflare_trace") {
                val trace = CloudflareTrace.parse(exchange!!.body)
                return if (trace.ipFamily == config.family) result("success", trace = trace)
                    else result("configuration_error", trace = trace)
            }
            val reply = Gson().fromJson(exchange!!.body, ServerReply::class.java) ?: throw EndpointResponseException()
            if (reply.observedIp == null || reply.ipFamily == null || reply.serverTimeUtc == null || reply.requestId == null)
                throw EndpointResponseException()
            Instant.parse(reply.serverTimeUtc)
            if (!reply.requestId.matches(Regex("[a-zA-Z0-9._-]{1,128}"))) throw EndpointResponseException()
            if (EndpointPolicy.numericFamily(reply.observedIp) != config.family || reply.ipFamily != config.family)
                result("configuration_error", reply)
            else result("success", reply)
        } catch (e: CancellationException) { throw e }
        catch (e: InterruptedException) { throw CancellationException("cancelled") }
        catch (e: Exception) {
            val state = when (e) {
                is ConfigurationException -> "configuration_error"
                is NetworkUnavailableException -> "network_unavailable"
                is AddressFamilyUnavailableException -> "address_family_unavailable"
                is UnknownHostException, is java.util.concurrent.RejectedExecutionException -> "dns_error"
                is SocketTimeoutException, is java.util.concurrent.TimeoutException -> "timeout"
                is SSLException -> "tls_error"
                is ConnectException, is SocketException -> "connection_error"
                else -> "invalid_response"
            }
            result(state, error = e)
        }
    }
}

fun externalFields(results: List<EndpointResult>): List<ProbeField<*>> {
    if (results.isEmpty()) return listOf(unavailable("externalChecks.state", "PublicEndpointProbe",
        FieldStatus.not_requested, "INTERNET", DiagnosticCatalog.legacy("https_check_awaits_a_user_action"), sensitive = false))
    return results.flatMap { result ->
        val prefix = "externalChecks.${result.expectedFamily.lowercase()}"
        listOf(
            readField("$prefix.state", "PublicEndpointProbe", "INTERNET", sensitive = false) { result.state },
            readField("$prefix.result", "Network DNS / bound Socket / system TLS / ${result.provider} HTTPS endpoint", "INTERNET", true,
                DiagnosticCatalog.legacy("uses_the_app_default_network_captured_at_start_socket_peer_and_se")) { result })
    }
}
