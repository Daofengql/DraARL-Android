package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLevelTest {
    @Test
    fun silenceHasNoLevel() {
        assertEquals(0f, normalizedPcmLevel(ShortArray(960)), 0.001f)
    }

    @Test
    fun fullScaleSignalReachesPeak() {
        assertTrue(normalizedPcmLevel(ShortArray(960) { Short.MAX_VALUE }) > 0.99f)
    }

    @Test
    fun speechLevelFallsBetweenSilenceAndPeak() {
        val level = normalizedPcmLevel(ShortArray(960) { 3_276 })
        assertTrue(level in 0.6f..0.75f)
    }
}
