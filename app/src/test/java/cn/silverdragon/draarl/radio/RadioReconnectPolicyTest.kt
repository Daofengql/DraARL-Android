package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioReconnectPolicyTest {
    @Test
    fun `waits until the server session has expired`() {
        val lastPacketSentAt = 1_000_000L

        assertEquals(
            13_000L,
            RadioReconnectPolicy.retryDelayMillis(lastPacketSentAt, now = 1_008_000L),
        )
    }

    @Test
    fun `uses minimum delay when no packet was sent or session already expired`() {
        assertEquals(3_000L, RadioReconnectPolicy.retryDelayMillis(lastPacketSentAt = 0L, now = 1_000_000L))
        assertEquals(
            3_000L,
            RadioReconnectPolicy.retryDelayMillis(lastPacketSentAt = 1_000_000L, now = 1_030_000L),
        )
    }
}
