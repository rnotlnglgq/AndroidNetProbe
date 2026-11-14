package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.probe.*
import org.junit.Assert.*
import org.junit.Test

class HttpResponseReaderTest {
    private fun read(response: String) = HttpResponseReader.read(response.byteInputStream(Charsets.US_ASCII))
    @Test fun acceptsLengthChunkedAndCloseDelimitedWithSameBody() {
        val body = "ip=192.0.2.1\n"
        assertEquals(body, read("HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n\r\n$body").body)
        assertEquals(body, read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nSet-Cookie: a\r\nSet-Cookie: b\r\n\r\n${body.length.toString(16)};ext=test\r\n$body\r\n0\r\nX-Test: value\r\n\r\n").body)
        assertEquals(body, read("HTTP/1.0 200 OK\r\n\r\n$body").body)
    }
    @Test fun rejectsAmbiguousTruncatedOversizedOrCompressedResponses() {
        listOf(
            "Content-Length: 1\r\nContent-Length: 1\r\n\r\na",
            "Transfer-Encoding: chunked\r\nContent-Length: 1\r\n\r\na",
            "Content-Length: 10\r\n\r\na",
            "Content-Length: 16385\r\n\r\n",
            "Content-Encoding: gzip\r\nContent-Length: 1\r\n\r\na",
            "Transfer-Encoding: chunked\r\n\r\n100000\r\n",
            "Transfer-Encoding: chunked\r\n\r\n1\r\naXX0\r\n\r\n"
        ).forEach { response ->
            try { read("HTTP/1.1 200 OK\r\n$response"); fail("Invalid response accepted") }
            catch (_: EndpointResponseException) { }
        }
    }
    @Test fun preservesHttpFailureAndDeadlineHooks() {
        assertEquals(503, read("HTTP/1.1 503 Unavailable\r\n\r\n").status)
        var calls = 0
        HttpResponseReader.read("HTTP/1.1 200 OK\r\nContent-Length: 1\r\n\r\nx".byteInputStream()) { calls++ }
        assertTrue(calls > 0)
    }
}
