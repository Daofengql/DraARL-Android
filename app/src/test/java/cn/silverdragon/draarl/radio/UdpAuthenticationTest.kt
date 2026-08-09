package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.protocol.DraarlPacket
import cn.silverdragon.draarl.protocol.DraarlProtocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class UdpAuthenticationTest {
    @Test
    fun `parses a matching successful session`() {
        val authenticated = UdpAuthentication.parse(
            packet = packet(successPayload(clientInstanceId = "CLIENT-ID"), ssid = 0, reserved = 42L),
            expectedClientInstanceId = "client-id"
        )

        assertEquals("session-1", authenticated.session.sessionId)
        assertEquals(42L, authenticated.session.sessionTag)
        assertEquals(7, authenticated.session.txGroupId)
        assertEquals(listOf(7, 8), authenticated.session.rxGroupIds)
        assertEquals(DraarlProtocol.SSID_ANDROID, authenticated.ssid)
    }

    @Test
    fun `rejects a response without a status code`() {
        val error = assertThrows(UdpAuthenticationException::class.java) {
            UdpAuthentication.parse(packet(byteArrayOf()), "client-id")
        }

        assertEquals("UDP 认证响应缺少状态码", error.message)
    }

    @Test
    fun `maps a server authentication failure`() {
        val error = assertThrows(UdpAuthenticationException::class.java) {
            UdpAuthentication.parse(
                packet(byteArrayOf(1) + "expired".toByteArray()),
                "client-id"
            )
        }

        assertEquals(DraarlProtocol.authError(1, "expired"), error.message)
    }

    @Test
    fun `rejects malformed success metadata`() {
        val error = assertThrows(UdpAuthenticationException::class.java) {
            UdpAuthentication.parse(packet(byteArrayOf(0, 1, 2)), "client-id")
        }

        assertEquals(true, error.message?.startsWith("UDP 认证响应无效："))
    }

    @Test
    fun `rejects a session issued to another client instance`() {
        val error = assertThrows(UdpAuthenticationException::class.java) {
            UdpAuthentication.parse(
                packet(successPayload(clientInstanceId = "another-client"), reserved = 42L),
                "client-id"
            )
        }

        assertEquals("UDP 认证响应的客户端实例不匹配", error.message)
    }

    @Test
    fun `receiver ignores unrelated packets and returns authentication`() {
        var now = 0L
        val unrelated = packet(byteArrayOf(1)).copy(type = DraarlProtocol.TYPE_TEXT)
        val authentication = packet(byteArrayOf(1))
        val packets = ArrayDeque(listOf(unrelated, authentication))
        val receiver = UdpAuthenticationReceiver(timeoutMillis = 5_000, nowMillis = { now })

        val received = receiver.await {
            now += 1_000
            packets.removeFirstOrNull()
        }

        assertSame(authentication, received)
    }

    @Test
    fun `receiver applies one total deadline across receive attempts`() {
        var now = 0L
        var attempts = 0
        val receiver = UdpAuthenticationReceiver(timeoutMillis = 5_000, nowMillis = { now })

        val error = assertThrows(UdpAuthenticationTimeoutException::class.java) {
            receiver.await {
                attempts++
                now += 1_000
                null
            }
        }

        assertEquals("UDP 认证超时", error.message)
        assertEquals(5, attempts)
    }

    private fun successPayload(clientInstanceId: String): ByteArray {
        val metadata = JSONObject()
            .put("version", 1)
            .put("session_id", "session-1")
            .put("session_tag", 42L)
            .put("client_instance_id", clientInstanceId)
            .put("tx_group_id", 7)
            .put("rx_group_ids", JSONArray(listOf(7, 8)))
            .toString()
            .toByteArray()
        return byteArrayOf(0) + metadata
    }

    private fun packet(data: ByteArray, ssid: Int = 101, reserved: Long = 0) = DraarlPacket(
        username = "operator",
        devicePassword = "",
        type = DraarlProtocol.TYPE_JWT_AUTH,
        deviceModel = 0,
        ssid = ssid,
        dmrId = 0,
        callsign = "BG0ABC",
        reserved = reserved,
        data = data
    )
}
