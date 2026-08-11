package cn.silverdragon.draarl.radio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusRecordingWavEncoderTest {
    @Test
    fun `encodes raw opus recording as mono 16 bit wav`() {
        val pcm = ShortArray(OpusAudioFormat.FRAME_SAMPLES) { index ->
            (sin(2.0 * PI * 440.0 * index / OpusAudioFormat.SAMPLE_RATE) * Short.MAX_VALUE / 4).toInt().toShort()
        }
        val opusFrame = requireNotNull(OpusFrameEncoder().encode(pcm))
        val rawRecording = RawOpusRecording.encode(
            sampleRate = OpusAudioFormat.SAMPLE_RATE,
            channels = OpusAudioFormat.CHANNELS,
            frameSize = OpusAudioFormat.FRAME_SAMPLES,
            frames = listOf(opusFrame)
        )

        val wav = OpusRecordingWavEncoder.encode(rawRecording)
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", wav.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(wav.size - 8, header.getInt(4))
        assertEquals("WAVE", wav.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(OpusAudioFormat.CHANNELS, header.getShort(22).toInt())
        assertEquals(OpusAudioFormat.SAMPLE_RATE, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals("data", wav.copyOfRange(36, 40).toString(Charsets.US_ASCII))
        assertEquals(wav.size - WAV_HEADER_SIZE, header.getInt(40))
        assertTrue(wav.size > WAV_HEADER_SIZE)
    }

    companion object {
        private const val WAV_HEADER_SIZE = 44
    }
}
