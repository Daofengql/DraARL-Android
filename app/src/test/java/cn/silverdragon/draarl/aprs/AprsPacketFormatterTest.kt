package cn.silverdragon.draarl.aprs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AprsPacketFormatterTest {
    @Test
    fun passcodeUsesBaseCallsignWithoutSsid() {
        assertEquals(AprsPacketFormatter.passcode("N0CALL-9"), AprsPacketFormatter.passcode("N0CALL"))
    }

    @Test
    fun packetContainsWgs84PositionAndAltitude() {
        val packet = AprsPacketFormatter.positionPacket(
            AprsConfig(callsign = "N0CALL", comment = "DraARL"),
            AprsPosition(latitude = 39.9042, longitude = 116.4074, altitudeMeters = 50.0),
        )
        assertTrue(packet.startsWith("N0CALL>APDRA1,TCPIP*:!3954.25N/11624.44E>"))
        assertTrue(packet.contains("/A=000164"))
        assertTrue(packet.endsWith(" DraARL"))
    }

    @Test
    fun packetSanitizesLineBreaks() {
        val packet = AprsPacketFormatter.positionPacket(
            AprsConfig(callsign = "N0CALL", comment = "a\nb|c"),
            AprsPosition(latitude = 0.0, longitude = 0.0),
        )
        assertTrue(!packet.contains('\n'))
        assertTrue(packet.endsWith(" a b c"))
    }

    @Test
    fun packetFormatsNegativeAltitudeWithoutPaddingInsideSign() {
        val packet = AprsPacketFormatter.positionPacket(
            AprsConfig(callsign = "N0CALL"),
            AprsPosition(latitude = 39.0, longitude = 116.0, altitudeMeters = -100.0),
        )
        assertTrue(packet.contains("/A=-00328"))
    }

    @Test
    fun packetCarriesCoordinateRoundingAcrossDegreeBoundary() {
        val packet = AprsPacketFormatter.positionPacket(
            AprsConfig(callsign = "N0CALL"),
            AprsPosition(latitude = 39.999999, longitude = 116.999999),
        )
        assertTrue(packet.contains("4000.00N"))
        assertTrue(packet.contains("11700.00E"))
        assertTrue(!packet.contains("60.00"))
    }
}
