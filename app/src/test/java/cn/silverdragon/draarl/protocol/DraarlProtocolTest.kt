package cn.silverdragon.draarl.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraarlProtocolTest {
    @Test
    fun jwtAuthMatchesServerHeaderLayout() {
        val packet = DraarlProtocol.jwtAuth("header.payload.signature")

        assertEquals("DraA", packet.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(packet.size, ((packet[4].toInt() and 0xff) shl 8) or (packet[5].toInt() and 0xff))
        assertEquals(DraarlProtocol.TYPE_JWT_AUTH, packet[48].toInt())
        assertEquals(DraarlProtocol.DEVICE_MODEL_ANDROID, packet[49].toInt() and 0xff)
        assertEquals(0, packet[50].toInt())
        assertEquals("header.payload.signature", packet.copyOfRange(90, packet.size).toString(Charsets.UTF_8))
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
        assertEquals("测试消息", decoded.data.toString(Charsets.UTF_8))
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
