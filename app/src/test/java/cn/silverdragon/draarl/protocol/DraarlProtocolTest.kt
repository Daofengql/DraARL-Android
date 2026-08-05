package cn.silverdragon.draarl.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraarlProtocolTest {
    @Test
    fun jwtAuthMatchesServerHeaderLayout() {
        val packet = DraarlProtocol.ghostAuth(
            "header.payload.signature",
            "41a065d7-f9e1-4785-bbcb-22c3ca8784ad",
        )

        assertEquals("DraA", packet.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(packet.size, ((packet[4].toInt() and 0xff) shl 8) or (packet[5].toInt() and 0xff))
        assertEquals(DraarlProtocol.TYPE_JWT_AUTH, packet[48].toInt())
        assertEquals(DraarlProtocol.DEVICE_MODEL_ANDROID, packet[49].toInt() and 0xff)
        assertEquals(0, packet[50].toInt())
        val payload = packet.copyOfRange(90, packet.size).toString(Charsets.UTF_8)
        assertTrue(payload.contains("\"token\":\"header.payload.signature\""))
        assertTrue(payload.contains("\"client_instance_id\":\"41a065d7-f9e1-4785-bbcb-22c3ca8784ad\""))
        assertTrue(payload.contains("multi_receive_v1"))
    }

    @Test
    fun packetRoundTripsAllRoutingFields() {
        val encoded = DraarlProtocol.encode(
            type = DraarlProtocol.TYPE_TEXT,
            data = "测试消息".toByteArray(),
            username = "alice",
            ssid = 101,
            deviceModel = 101,
            dmrId = 0x123456,
            callsign = "BG7XXX",
        )

        val decoded = DraarlProtocol.decode(encoded)!!
        assertEquals("alice", decoded.username)
        assertEquals("BG7XXX", decoded.callsign)
        assertEquals(101, decoded.ssid)
        assertEquals(0x123456, decoded.dmrId)
        assertEquals(0L, decoded.reserved)
        assertEquals("测试消息", decoded.data.toString(Charsets.UTF_8))
    }

    @Test
    fun reservedRoundTripsUnsignedSessionTag() {
        val decoded = DraarlProtocol.decode(
            DraarlProtocol.text(
                message = "hello",
                username = "alice",
                ssid = 101,
                sessionTag = 0xf1234567L,
            ),
        )!!

        assertEquals(0xf1234567L, decoded.reserved)
        assertEquals("alice", decoded.username)
    }

    @Test
    fun parsesVersionedAuthenticationSuccess() {
        val data = ("\u0000" +
            "{\"version\":1,\"session_id\":\"session-1\",\"session_tag\":270544961," +
            "\"client_instance_id\":\"41a065d7-f9e1-4785-bbcb-22c3ca8784ad\"," +
            "\"tx_group_id\":1001,\"rx_group_ids\":[1001,1002]}").toByteArray()
        val packet = DraarlProtocol.decode(
            DraarlProtocol.encode(
                type = DraarlProtocol.TYPE_JWT_AUTH,
                data = data,
                ssid = 101,
                reserved = 270544961,
            ),
        )!!

        val session = DraarlProtocol.parseGhostAuthSuccess(packet)

        assertEquals("session-1", session.sessionId)
        assertEquals(270544961L, session.sessionTag)
        assertEquals(1001, session.txGroupId)
        assertEquals(listOf(1001, 1002), session.rxGroupIds)
    }

    @Test
    fun mergedOpusFramesRoundTrip() {
        val frames = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5))
        val decoded = DraarlProtocol.splitOpusFrames(DraarlProtocol.mergeOpusFrames(frames))

        assertEquals(2, decoded.size)
        assertArrayEquals(frames[0], decoded[0])
        assertArrayEquals(frames[1], decoded[1])
    }

    @Test
    fun malformedLengthIsRejected() {
        val packet = DraarlProtocol.text("hello")
        packet[5] = 90
        assertNull(DraarlProtocol.decode(packet))
    }
}
