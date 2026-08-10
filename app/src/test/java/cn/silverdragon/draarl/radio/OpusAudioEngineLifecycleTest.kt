package cn.silverdragon.draarl.radio

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusAudioEngineLifecycleTest {
    private val directory = Files.createTempDirectory("opus-engine-lifecycle").toFile()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `release is idempotent and rejects later playback`() {
        val engine = OpusAudioEngine(RadioAudioCache(directory))

        engine.release()
        engine.release()

        assertFalse(engine.playRecording("", "https://example.test/voice.raw", {}, {}))
    }

    @Test
    fun `download completing after release cannot submit playback or invoke callbacks`() {
        val downloadStarted = CountDownLatch(1)
        val allowCompletion = CountDownLatch(1)
        val callbackInvoked = AtomicBoolean(false)
        val recording = RawOpusRecording.encode(16_000, 1, 960, listOf(byteArrayOf(1, 2, 3)))
        val loader = HistoricalAudioLoader(RadioAudioCache(directory)) {
            downloadStarted.countDown()
            while (true) {
                try {
                    allowCompletion.await()
                    break
                } catch (_: InterruptedException) {
                    // Model a network stack that completes after cancellation.
                }
            }
            recording
        }
        val engine = OpusAudioEngine(RadioAudioCache(directory), loader)

        assertTrue(
            engine.playRecording(
                audioCacheKey = "race",
                audioUrl = "https://example.test/voice.raw",
                onFinished = { callbackInvoked.set(true) },
                onError = { callbackInvoked.set(true) }
            )
        )
        assertTrue(downloadStarted.await(2, TimeUnit.SECONDS))

        engine.release()
        allowCompletion.countDown()
        Thread.sleep(100)

        assertFalse(callbackInvoked.get())
    }

    @Test
    fun `stopping recording playback interrupts download without callback or cache`() {
        val downloadStarted = CountDownLatch(1)
        val downloadInterrupted = CountDownLatch(1)
        val allowCompletion = CountDownLatch(1)
        val callbackInvoked = CountDownLatch(1)
        val recording = RawOpusRecording.encode(16_000, 1, 960, listOf(byteArrayOf(1, 2, 3)))
        val cache = RadioAudioCache(directory)
        val loader = HistoricalAudioLoader(cache) {
            downloadStarted.countDown()
            try {
                check(allowCompletion.await(2, TimeUnit.SECONDS))
                recording
            } catch (error: InterruptedException) {
                downloadInterrupted.countDown()
                throw error
            }
        }
        val engine = OpusAudioEngine(cache, loader)
        try {
            assertTrue(
                engine.playRecording(
                    audioCacheKey = "interrupt",
                    audioUrl = "https://example.test/voice.raw",
                    onFinished = callbackInvoked::countDown,
                    onError = { callbackInvoked.countDown() }
                )
            )
            assertTrue(downloadStarted.await(2, TimeUnit.SECONDS))

            engine.stopRecordingPlayback()

            assertTrue(downloadInterrupted.await(2, TimeUnit.SECONDS))
            assertFalse(callbackInvoked.await(100, TimeUnit.MILLISECONDS))
            assertFalse(cache.contains("interrupt"))
        } finally {
            allowCompletion.countDown()
            engine.release()
        }
    }

    @Test
    fun `invalid historical audio reports one error without a finished callback`() {
        val callback = CountDownLatch(1)
        val errors = AtomicInteger(0)
        val finishes = AtomicInteger(0)
        val loader = HistoricalAudioLoader(RadioAudioCache(directory)) { byteArrayOf(1, 2, 3) }
        val engine = OpusAudioEngine(RadioAudioCache(directory), loader)

        assertTrue(
            engine.playRecording(
                audioCacheKey = "invalid",
                audioUrl = "https://example.test/invalid.raw",
                onFinished = {
                    finishes.incrementAndGet()
                    callback.countDown()
                },
                onError = {
                    errors.incrementAndGet()
                    callback.countDown()
                }
            )
        )
        assertTrue(callback.await(2, TimeUnit.SECONDS))

        assertTrue(errors.get() == 1)
        assertTrue(finishes.get() == 0)
        engine.release()
    }
}
