package cn.silverdragon.draarl.radio

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioAudioCacheTest {
    private val directory = Files.createTempDirectory("radio-audio-cache").toFile()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `stores and reads validated raw recording`() {
        val cache = RadioAudioCache(directory)
        val bytes = rawRecording(byteArrayOf(1, 2, 3))

        cache.put("https://example.test/voice/1.raw", bytes)

        assertArrayEquals(bytes, cache.get("https://example.test/voice/1.raw"))
    }

    @Test
    fun `deletes corrupt cached recording`() {
        val cache = RadioAudioCache(directory)
        val key = "https://example.test/voice/2.raw"
        cache.put(key, rawRecording(byteArrayOf(4, 5, 6)))
        val cacheFile = directory.listFiles().orEmpty().single()
        cacheFile.writeBytes(byteArrayOf(1, 2, 3))

        assertNull(cache.get(key))
        assertFalse(cacheFile.exists())
    }

    @Test
    fun `removes least recently used recordings above size limit`() {
        val first = rawRecording(ByteArray(32) { 1 })
        val second = rawRecording(ByteArray(32) { 2 })
        val cache = RadioAudioCache(directory, maxBytes = first.size.toLong() + 1)

        cache.put("first", first)
        directory.listFiles().orEmpty().single().setLastModified(1L)
        cache.put("second", second)

        assertNull(cache.get("first"))
        assertArrayEquals(second, cache.get("second"))
        assertTrue(directory.listFiles().orEmpty().size == 1)
    }

    private fun rawRecording(frame: ByteArray): ByteArray = RawOpusRecording.encode(
        sampleRate = 16_000,
        channels = 1,
        frameSize = 960,
        frames = listOf(frame),
    )
}
