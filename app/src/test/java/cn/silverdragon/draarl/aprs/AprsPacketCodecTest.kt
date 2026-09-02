package cn.silverdragon.draarl.aprs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AprsPacketCodecTest {
    @Test
    fun decodesPositionPacketWithAltitudeAndComment() {
        val decoded = AprsPacketCodec.decode(
            "N0CALL>APDRA1,TCPIP*:!3954.25N/11624.44E>/A=000164 DraARL"
        )
        assertEquals("N0CALL", decoded.source)
        assertEquals("APDRA1", decoded.destination)
        assertEquals(39.904166, decoded.latitude, 0.00001)
        assertEquals(116.407333, decoded.longitude, 0.00001)
        assertEquals('/', decoded.symbolTable)
        assertEquals('>', decoded.symbolCode)
        assertEquals(164, decoded.altitudeFeet)
        assertEquals("DraARL", decoded.comment)
    }

    @Test
    fun roundTripsNegativeCoordinates() {
        val packet = AprsPacketCodec.encode(
            callsign = "N0CALL",
            latitude = -33.8688,
            longitude = 151.2093,
            altitudeMeters = null,
            comment = "Sydney"
        )
        val decoded = AprsPacketCodec.decode(packet)
        assertEquals(-33.8688, decoded.latitude, 0.01)
        assertEquals(151.2093, decoded.longitude, 0.01)
        assertEquals("Sydney", decoded.comment)
    }

    @Test
    fun rejectsMalformedPositionPacket() {
        assertThrows(IllegalArgumentException::class.java) {
            AprsPacketCodec.decode("N0CALL>APDRA1:hello")
        }
    }
}
