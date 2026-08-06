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

    @Test
    fun `channel message path uses server page default when limit is omitted`() {
        assertEquals(
            "/api/groups/7/messages?message_type=all",
            groupMessagesPath(groupId = 7, limit = null, cursor = "", messageType = "all"),
        )
    }

    @Test
    fun `channel message path preserves explicit cursor and page size`() {
        assertEquals(
            "/api/groups/7/messages?message_type=voice&limit=25&cursor=next+page",
            groupMessagesPath(groupId = 7, limit = 25, cursor = "next page", messageType = "voice"),
        )
    }

    @Test
    fun `stable API errors provide actionable retry messages`() {
        assertEquals(
            "请在 42 秒后重试",
            friendlyApiErrorMessage("message_api_user_rate_limited", "ignored", 42),
        )
        assertEquals(
            "当前账号或 Android 客户端尚未开放多频道收听，请仅保留发送频道",
            friendlyApiErrorMessage("ghost_multi_receive_disabled", "ignored", null),
        )
    }
}
