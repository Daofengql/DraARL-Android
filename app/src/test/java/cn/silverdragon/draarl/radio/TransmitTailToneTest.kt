package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransmitTailToneTest {
    @Test
    fun `off preset has no samples`() {
        assertTrue(TransmitTailToneGenerator.generate(TransmitTailTone.OFF).isEmpty())
    }

    @Test
    fun `enabled presets contain packet aligned audio`() {
        val packetSamples = OpusAudioFormat.FRAME_SAMPLES * OpusAudioFormat.FRAMES_PER_PACKET
        TransmitTailTone.entries.filterNot { it == TransmitTailTone.OFF }.forEach { preset ->
            val samples = TransmitTailToneGenerator.generate(preset)
            assertEquals(0, samples.size % packetSamples)
            assertTrue(samples.any { it != 0.toShort() })
        }
    }
}
