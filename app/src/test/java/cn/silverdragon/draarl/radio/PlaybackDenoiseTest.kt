package cn.silverdragon.draarl.radio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackDenoiseTest {
    @Test
    fun `converts strength percent to wet mix`() {
        assertEquals(0f, denoiseStrengthPercentToWetMix(-10), 0f)
        assertEquals(0.5f, denoiseStrengthPercentToWetMix(50), 0f)
        assertEquals(1f, denoiseStrengthPercentToWetMix(120), 0f)
    }

    @Test
    fun `blends denoised PCM with original signal`() {
        val original = shortArrayOf(1000, -1000, 3000, -3000)
        val denoised = shortArrayOf(0, 0, 1000, -1000)

        val blended = blendDenoisedPcm(original, denoised, wetMix = 0.5f)

        assertArrayEquals(shortArrayOf(500, -500, 2000, -2000), blended)
    }

    @Test
    fun `returns original PCM for zero wet mix`() {
        val original = shortArrayOf(100, -100)

        val blended = blendDenoisedPcm(original, shortArrayOf(0, 0), wetMix = 0f)

        assertSame(original, blended)
    }
}
