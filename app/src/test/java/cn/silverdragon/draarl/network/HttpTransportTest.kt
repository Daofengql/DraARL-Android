package cn.silverdragon.draarl.network

import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTransportTest {
    @Test
    fun jsonRequestPreservesMethodHeadersAndBody() = withServer { server, transport ->
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"data":{"saved":true}}"""))

        val response = transport.execute(
            HttpRequest(
                url = server.url("/api/items?limit=2").toString(),
                method = "POST",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer access-token"
                ),
                body = HttpRequestBody.Bytes(
                    """{"name":"portable"}""".toByteArray(),
                    "application/json; charset=utf-8"
                )
            )
        )

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/items?limit=2", recorded.path)
        assertEquals("Bearer access-token", recorded.getHeader("Authorization"))
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
        assertEquals("""{"name":"portable"}""", recorded.body.readUtf8())
        assertEquals(201, response.status)
        assertTrue(response.toApiJson().getJSONObject("data").getBoolean("saved"))
    }

    @Test
    fun errorResponseUsesHttpStatusAndCarriesRetryAfter() = withServer { server, transport ->
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "42")
                .setBody("""{"code":200,"message":"slow down"}""")
        )

        val json = transport.execute(HttpRequest(server.url("/limited").toString(), "GET")).toApiJson()

        assertEquals(429, json.getInt("code"))
        assertEquals("slow down", json.getString("message"))
        assertEquals(42, json.getInt(HTTP_RETRY_AFTER_SECONDS))
    }

    @Test
    fun emptySuccessResponseProducesStatusJson() = withServer { server, transport ->
        server.enqueue(MockResponse().setResponseCode(204))

        val response = transport.execute(HttpRequest(server.url("/empty").toString(), "DELETE"))

        assertArrayEquals(ByteArray(0), response.body)
        assertEquals(204, response.toApiJson().getInt("code"))
    }

    @Test
    fun invalidJsonReportsTheResponseStatus() = withServer { server, transport ->
        server.enqueue(MockResponse().setResponseCode(502).setBody("not-json"))

        val response = transport.execute(HttpRequest(server.url("/broken").toString(), "GET"))
        val error = assertThrows(ApiException::class.java, response::toApiJson)

        assertEquals(502, error.code)
        assertEquals("服务器返回了无法识别的数据", error.message)
    }

    @Test
    fun postWithoutPayloadUsesAnEmptyRequestBody() = withServer { server, transport ->
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        transport.execute(HttpRequest(server.url("/sync").toString(), "POST"))

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(0L, recorded.bodySize)
    }

    @Test
    fun multipartRequestCarriesFileAndTextParts() = withServer { server, transport ->
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val fileBytes = byteArrayOf(0x01, 0x02, 0x03)

        transport.execute(
            HttpRequest(
                url = server.url("/upload").toString(),
                method = "POST",
                body = HttpRequestBody.Multipart(
                    listOf(
                        HttpPart("file", fileBytes, "avatar.bin", "application/octet-stream"),
                        HttpPart("file_type", "avatar".toByteArray())
                    )
                )
            )
        )

        val recorded = server.takeRequest()
        val body = recorded.body.readByteArray()
        val text = body.toString(Charsets.ISO_8859_1)
        assertTrue(recorded.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
        assertTrue(text.contains("name=\"file\"; filename=\"avatar.bin\""))
        assertTrue(text.contains("name=\"file_type\""))
        assertTrue(text.contains("avatar"))
        assertTrue(body.indexOfSequence(fileBytes) >= 0)
    }

    @Test
    fun cleartextRequestIsRejectedByDefault() = withServer { server, _ ->
        val error = assertThrows(HttpTransportException::class.java) {
            OkHttpTransport().newCall(HttpRequest(server.url("/insecure").toString(), "GET"))
        }

        assertTrue(error.message.orEmpty().contains("HTTPS"))
        assertEquals("GET", error.request.method)
    }

    @Test
    fun requestReadTimeoutIsAppliedPerCall() = withServer { server, transport ->
        server.enqueue(
            MockResponse()
                .setHeadersDelay(500, TimeUnit.MILLISECONDS)
                .setBody("{}")
        )

        val error = assertThrows(HttpTransportException::class.java) {
            transport.execute(
                HttpRequest(
                    url = server.url("/slow").toString(),
                    method = "GET",
                    readTimeoutMillis = 50
                )
            )
        }

        assertTrue(error.cause is SocketTimeoutException)
    }

    @Test
    fun activeCallCanBeCancelled() = withServer { server, transport ->
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val call = transport.newCall(HttpRequest(server.url("/waiting").toString(), "GET"))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<HttpResponse>(call::execute)
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))

            call.cancel()

            val failure = assertThrows(ExecutionException::class.java) {
                future.get(2, TimeUnit.SECONDS)
            }
            assertTrue(failure.cause is HttpTransportException)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun withServer(block: (MockWebServer, HttpTransport) -> Unit) {
        val server = MockWebServer()
        server.start()
        try {
            block(server, OkHttpTransport(allowCleartext = true))
        } finally {
            server.shutdown()
        }
    }
}

private fun ByteArray.indexOfSequence(sequence: ByteArray): Int {
    if (sequence.isEmpty()) return 0
    for (start in 0..size - sequence.size) {
        if (sequence.indices.all { offset -> this[start + offset] == sequence[offset] }) return start
    }
    return -1
}
