package com.rnotlnglgq.netprobe.probe

import android.content.Context
import android.net.ConnectivityManager
import java.io.BufferedInputStream
import java.net.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** One run binds both requests to the Network that was the app default when the user confirmed. */
class AndroidEndpointTransport(context: Context) : EndpointTransport {
    private val network = context.getSystemService(ConnectivityManager::class.java).activeNetwork
    private val cancelled = AtomicBoolean(false)
    private val socket = AtomicReference<Socket?>()
    private val dnsTask = AtomicReference<Future<*>?>()

    fun cancel() {
        cancelled.set(true)
        dnsTask.getAndSet(null)?.cancel(true)
        runCatching { socket.getAndSet(null)?.close() }
    }
    private fun checkCancellation() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException()
    }
    override fun exchange(config: EndpointConfig): HttpExchange {
        checkCancellation()
        val uri = EndpointPolicy.parse(config)
        val selected = network ?: throw NetworkUnavailableException()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        fun remaining(): Int {
            checkCancellation()
            val ms = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (ms <= 0) throw SocketTimeoutException()
            return ms.coerceAtMost(10_000).toInt()
        }
        // Bounded resolver workers also support API 26–28. Cancelling may not interrupt system DNS;
        // at most two daemon workers may remain blocked, never an unbounded thread or retry loop.
        val task = DNS.submit(Callable { selected.getAllByName(uri.host).toList() })
        dnsTask.set(task)
        val addresses = try { task.get(remaining().toLong(), TimeUnit.MILLISECONDS) }
        catch (e: ExecutionException) { throw (e.cause as? Exception ?: e) }
        finally { task.cancel(true); dnsTask.compareAndSet(task, null) }
        if (addresses.isEmpty()) throw UnknownHostException()
        val families = addresses.map { if (it is Inet6Address) "IPv6" else "IPv4" }.distinct()
        if (config.responseFormat == "owned_json" && families.any { it != config.family }) throw ConfigurationException()
        val candidates = addresses.filter { (if (it is Inet6Address) "IPv6" else "IPv4") == config.family }
        if (candidates.isEmpty()) throw AddressFamilyUnavailableException()
        val port = if (uri.port == -1) 443 else uri.port
        val tcp = selected.socketFactory.createSocket()
        socket.set(tcp)
        try {
            tcp.connect(InetSocketAddress(candidates.first(), port), remaining())
            checkCancellation()
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(tcp, uri.host, port, true) as SSLSocket
            socket.set(tls)
            tls.soTimeout = remaining()
            tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            // System trust anchors, certificate/hostname verification. No custom trust manager.
            tls.startHandshake()
            val peer = tls.inetAddress
            val peerFamily = if (peer is Inet6Address) "IPv6" else "IPv4"
            if (peerFamily != config.family) throw ConfigurationException()
            val host = uri.host + if (port == 443) "" else ":$port"
            val path = uri.rawPath.takeUnless { it.isNullOrEmpty() } ?: "/"
            val accept = if (config.responseFormat == "cloudflare_trace") "text/plain" else "application/json"
            val request = "GET $path HTTP/1.1\r\nHost: $host\r\nAccept: $accept\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n"
            tls.outputStream.write(request.toByteArray(Charsets.US_ASCII))
            tls.outputStream.flush()
            val input = BufferedInputStream(tls.inputStream)
            val response = HttpResponseReader.read(input) { tls.soTimeout = remaining() }
            return HttpExchange(response.status, response.body, peer.hostAddress ?: "", peerFamily, families, tls.localAddress?.hostAddress)
        } finally { socket.getAndSet(null)?.close(); tcp.close() }
    }
    companion object {
        private val DNS = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, SynchronousQueue(),
            { runnable -> Thread(runnable, "netprobe-user-dns").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    }
}
