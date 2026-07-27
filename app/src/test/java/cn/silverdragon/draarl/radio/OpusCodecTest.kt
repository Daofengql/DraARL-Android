package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusCodecTest {
    @Test
    fun `capture encoder output is accepted by shared decoder`() {
        val pcm = ShortArray(OpusAudioFormat.FRAME_SAMPLES) { index ->
            ((index % 64) * 200 - 6_400).toShort()
        }

        val encoded = OpusFrameEncoder().encode(pcm)
        assertNotNull(encoded)
        assertTrue(encoded!!.isNotEmpty())

        val decoded = OpusFrameDecoder().decode(encoded)
        assertEquals(OpusAudioFormat.FRAME_SAMPLES, decoded.size)
    }
}
