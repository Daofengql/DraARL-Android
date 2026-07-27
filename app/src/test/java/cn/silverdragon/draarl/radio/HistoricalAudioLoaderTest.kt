package cn.silverdragon.draarl.radio

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoricalAudioLoaderTest {
    private val directory = Files.createTempDirectory("historical-audio-loader").toFile()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `downloads once then reuses validated local recording`() {
        val calls = AtomicInteger()
        val recording = RawOpusRecording.encode(16_000, 1, 960, listOf(byteArrayOf(1, 2, 3)))
        val loader = HistoricalAudioLoader(RadioAudioCache(directory)) {
            calls.incrementAndGet()
            recording
        }

        assertArrayEquals(recording, loader.load("record-1", "https://example.test/1.raw"))
        assertArrayEquals(recording, loader.load("record-1", ""))
        assertEquals(1, calls.get())
    }
}
