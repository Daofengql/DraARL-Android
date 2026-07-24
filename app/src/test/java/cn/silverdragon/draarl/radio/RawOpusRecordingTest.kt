package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.protocol.DraarlProtocol
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RawOpusRecordingTest {
    @Test
    fun `decodes server raw opus header and little endian frames`() {
        val firstFrame = byteArrayOf(10, 20)
        val secondFrame = byteArrayOf(30, 40, 50)
        val payload = ByteBuffer.allocate(2 + firstFrame.size + 2 + secondFrame.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(firstFrame.size.toShort())
            .put(firstFrame)
            .putShort(secondFrame.size.toShort())
            .put(secondFrame)
            .array()
        val bytes = ByteBuffer.allocate(24 + payload.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OPUS".toByteArray(Charsets.US_ASCII))
            putShort(1)
            putInt(16_000)
            putShort(1)
            putShort(960)
            putInt(2)
            put(ByteArray(6))
            put(payload)
        }.array()

        val recording = RawOpusRecording.decode(bytes)

        assertEquals(16_000, recording.sampleRate)
        assertEquals(1, recording.channels)
        assertEquals(960, recording.frameSize)
        assertEquals(2, recording.frameCount)
        assertArrayEquals(payload, recording.payload)
        val frames = recording.splitFrames()
        assertEquals(2, frames.size)
        assertArrayEquals(firstFrame, frames[0])
        assertArrayEquals(secondFrame, frames[1])
    }

    @Test
    fun `rejects truncated server raw opus frame`() {
        val bytes = ByteBuffer.allocate(24 + 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OPUS".toByteArray(Charsets.US_ASCII))
            putShort(1)
            putInt(16_000)
            putShort(1)
            putShort(960)
            putInt(1)
            put(ByteArray(6))
            putShort(5)
            put(byteArrayOf(10, 20))
        }.array()

        assertThrows(IllegalArgumentException::class.java) {
            RawOpusRecording.decode(bytes)
        }
    }

    @Test
    fun `encodes and decodes raw opus recording`() {
        val frames = listOf(
            byteArrayOf(1, 2, 3),
            byteArrayOf(4, 5),
        )

        val bytes = RawOpusRecording.encode(
            sampleRate = 16_000,
            channels = 1,
            frameSize = 960,
            frames = frames,
        )
        val recording = RawOpusRecording.decode(bytes)

        assertEquals(2, recording.frameCount)
        recording.splitFrames().forEachIndexed { index, frame ->
            assertArrayEquals(frames[index], frame)
        }
    }

    @Test
    fun `converts big endian network payload to raw opus recording`() {
        val frames = listOf(
            byteArrayOf(10, 20, 30),
            byteArrayOf(40, 50),
        )
        val networkPayload = DraarlProtocol.mergeOpusFrames(frames)

        val rawBytes = RawOpusRecording.fromNetworkPayload(networkPayload)
        val recording = RawOpusRecording.decode(rawBytes)

        recording.splitFrames().forEachIndexed { index, frame ->
            assertArrayEquals(frames[index], frame)
        }
    }
}
