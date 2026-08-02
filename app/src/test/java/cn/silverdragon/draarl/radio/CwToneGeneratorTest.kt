package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CwToneGeneratorTest {
    @Test
    fun `normalization keeps supported characters`() {
        assertEquals("CQ CQ/TEST?", CwToneGenerator.normalize(" cq 中文 cq/test? "))
    }

    @Test
    fun `generated tone is packet aligned and speed affects duration`() {
        val slow = CwToneGenerator.generate("SOS", wordsPerMinute = 10, toneHz = 700)
        val fast = CwToneGenerator.generate("SOS", wordsPerMinute = 20, toneHz = 700)
        val packetSamples = OpusAudioFormat.FRAME_SAMPLES * OpusAudioFormat.FRAMES_PER_PACKET

        assertEquals(0, slow.samples.size % packetSamples)
        assertTrue(slow.samples.any { it != 0.toShort() })
        assertTrue(slow.durationMs > fast.durationMs)
    }
}
