package com.rnotlnglgq.netprobe.probe

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

data class BoundedHttpResponse(val status: Int, val body: String)

/** HTTP/1 response framing only; TLS, hostname and deadline are handled by the transport. */
object HttpResponseReader {
    private const val LIMIT = 16_384
    fun read(input: InputStream, beforeRead: () -> Unit = {}): BoundedHttpResponse {
        fun byte(): Int { beforeRead(); return input.read() }
        fun line(): String {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val value = byte()
                if (value < 0 || bytes.size() > 4096) throw EndpointResponseException()
                if (value == 10) return bytes.toString("US-ASCII").removeSuffix("\r")
                bytes.write(value)
            }
        }
        val statusLine = line()
        if (!statusLine.matches(Regex("HTTP/1[.][01] [0-9]{3}.*"))) throw EndpointResponseException()
        val status = statusLine.substring(9, 12).toInt()
        val headers = linkedMapOf<String, String>()
        var headerBytes = statusLine.length
        var headerCount = 0
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            headerBytes += header.length
            headerCount++
            if (headerBytes > LIMIT || headerCount > 64 || ':' !in header) throw EndpointResponseException()
            val name = header.substringBefore(':').trim().lowercase(Locale.ROOT)
            val value = header.substringAfter(':').trim()
            if (headers.containsKey(name) && name in setOf("content-length", "transfer-encoding", "content-encoding"))
                throw EndpointResponseException()
            // Repeated non-framing headers (e.g. Set-Cookie) are not used or sent back.
            headers[name] = value
        }
        if (status !in 200..299 || status == 204) return BoundedHttpResponse(status, "")
        if (headers["content-encoding"]?.lowercase(Locale.ROOT).let { it != null && it != "identity" })
            throw EndpointResponseException()
        val output = ByteArrayOutputStream()
        fun exact(size: Int) {
            if (size < 0 || output.size() + size > LIMIT) throw EndpointResponseException()
            val buffer = ByteArray(minOf(size, 4096))
            var remaining = size
            while (remaining > 0) {
                beforeRead()
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count <= 0) throw EndpointResponseException()
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
        val transfer = headers["transfer-encoding"]?.lowercase(Locale.ROOT)
        if (transfer != null) {
            if (transfer != "chunked" || headers.containsKey("content-length")) throw EndpointResponseException()
            var chunkCount = 0
            while (true) {
                if (++chunkCount > LIMIT) throw EndpointResponseException()
                val chunk = line().substringBefore(';').trim()
                if (!chunk.matches(Regex("[0-9a-fA-F]{1,8}"))) throw EndpointResponseException()
                val size = chunk.toLong(16)
                if (size > LIMIT) throw EndpointResponseException()
                if (size == 0L) {
                    while (true) {
                        val trailer = line()
                        if (trailer.isEmpty()) break
                        headerBytes += trailer.length
                        if (++headerCount > 64 || headerBytes > LIMIT || ':' !in trailer) throw EndpointResponseException()
                    }
                    break
                }
                exact(size.toInt())
                if (byte() != 13 || byte() != 10) throw EndpointResponseException()
            }
        } else if (headers.containsKey("content-length")) {
            val text = headers.getValue("content-length")
            if (!text.matches(Regex("[0-9]{1,8}"))) throw EndpointResponseException()
            exact(text.toInt())
        } else {
            // HTTP/1.0/Connection: close response. Enforce both size limit and transport deadline.
            while (true) {
                val value = byte()
                if (value < 0) break
                if (output.size() >= LIMIT) throw EndpointResponseException()
                output.write(value)
            }
        }
        return BoundedHttpResponse(status, output.toString("UTF-8"))
    }
}
