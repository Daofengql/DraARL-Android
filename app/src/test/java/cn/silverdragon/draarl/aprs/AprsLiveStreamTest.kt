package cn.silverdragon.draarl.aprs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNotNull
import org.junit.Test

class AprsLiveStreamTest {
    @Test
    fun formatsRadiusFilterInKilometers() {
        val filter = AprsLiveFilter(39.9042, 116.4074, 160)
        assertEquals("r/39.90420/116.40740/100", filter.asAprsIsFilter())
    }

    @Test
    fun rejectsUnsafeFilterRange() {
        assertThrows(IllegalArgumentException::class.java) {
            AprsLiveFilter(39.9042, 116.4074, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AprsLiveFilter(91.0, 116.4074, 10)
        }
    }

    @Test
    fun parsesPositionPacketForLiveList() {
        val packet = parseAprsLivePacket(
            "N0CALL>APDRA1,TCPIP*:!3954.25N/11624.44E>/A=000164 DraARL",
            receivedAtMillis = 1234L
        )
        assertNotNull(packet)
        assertEquals("N0CALL", packet?.source)
        assertEquals(39.904166, packet?.latitude ?: 0.0, 0.00001)
        assertEquals(164, packet?.altitudeFeet)
        assertEquals(1234L, packet?.receivedAtMillis)
    }

    @Test
    fun ignoresNonPositionPacketForLiveList() {
        val packet = parseAprsLivePacket("N0CALL>APDRA1:>status")
        assertEquals(null, packet)
    }
}
