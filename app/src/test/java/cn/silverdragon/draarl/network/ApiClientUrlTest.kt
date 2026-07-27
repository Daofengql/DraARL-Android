package cn.silverdragon.draarl.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiClientUrlTest {
    @Test
    fun `normalizes an https base URL`() {
        assertEquals("https://ptt.4l2.cn", ApiClient.normalizeBaseUrl("ptt.4l2.cn/"))
    }

    @Test
    fun `rejects cleartext http base URL`() {
        assertThrows(ApiException::class.java) {
            ApiClient.normalizeBaseUrl("http://ptt.4l2.cn")
        }
    }

    @Test
    fun `resolves a relative resource against the fixed https origin`() {
        assertEquals(
            "https://ptt.4l2.cn/uploads/avatar/test.jpg",
            ApiClient.resolveHttpsUrl("https://ptt.4l2.cn", "/uploads/avatar/test.jpg"),
        )
    }

    @Test
    fun `accepts an absolute https resource URL`() {
        assertEquals(
            "https://cdn.example.test/audio/1.raw",
            ApiClient.resolveHttpsUrl("https://ptt.4l2.cn", "https://cdn.example.test/audio/1.raw"),
        )
    }

    @Test
    fun `rejects a cleartext resource URL`() {
        assertThrows(ApiException::class.java) {
            ApiClient.resolveHttpsUrl("https://ptt.4l2.cn", "http://cdn.example.test/audio/1.raw")
        }
    }
}
