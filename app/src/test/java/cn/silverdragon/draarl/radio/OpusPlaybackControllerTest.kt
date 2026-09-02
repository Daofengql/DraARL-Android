package cn.silverdragon.draarl.radio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusPlaybackControllerTest {
    @Test
    fun `mute does not wait for the playback executor`() {
        assertUpdateDoesNotWaitForPlayback { controller ->
            controller.setMuted(true)
        }
    }

    @Test
    fun `denoise updates do not wait for the playback executor`() {
        assertUpdateDoesNotWaitForPlayback { controller ->
            controller.setDenoiseEnabled(true)
            controller.setDenoiseWetMix(0.8f)
            controller.setDenoiseEnabled(false)
        }
    }

    private fun assertUpdateDoesNotWaitForPlayback(update: (OpusPlaybackController) -> Unit) {
        val executor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue()
        )
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        executor.execute {
            workerStarted.countDown()
            releaseWorker.await()
        }
        assertTrue(workerStarted.await(2, TimeUnit.SECONDS))
        val controller = OpusPlaybackController(executor = executor)

        try {
            update(controller)
            assertTrue(executor.queue.isEmpty())
        } finally {
            controller.release()
            releaseWorker.countDown()
        }
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
    }

    @Test
    fun `release is idempotent and suppresses callbacks from an in-flight task`() {
        val executor = Executors.newSingleThreadExecutor()
        val activeChecks = AtomicInteger(0)
        val workerCheckStarted = CountDownLatch(1)
        val allowWorkerCheck = CountDownLatch(1)
        val levelUpdates = AtomicInteger(0)
        val finishedCallbacks = AtomicInteger(0)
        val errorCallbacks = AtomicInteger(0)
        val controller = OpusPlaybackController(
            onLevel = { levelUpdates.incrementAndGet() },
            executor = executor
        )

        assertTrue(
            controller.playRecording(
                bytes = byteArrayOf(1, 2, 3),
                isActive = {
                    if (activeChecks.incrementAndGet() == 2) {
                        workerCheckStarted.countDown()
                        allowWorkerCheck.await()
                    }
                    true
                },
                onFinished = { finishedCallbacks.incrementAndGet() },
                onError = { errorCallbacks.incrementAndGet() }
            )
        )
        assertTrue(workerCheckStarted.await(2, TimeUnit.SECONDS))

        controller.release()
        controller.release()
        assertEquals(1, levelUpdates.get())

        allowWorkerCheck.countDown()
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        assertEquals(2, activeChecks.get())
        assertEquals(1, levelUpdates.get())
        assertEquals(0, finishedCallbacks.get())
        assertEquals(0, errorCallbacks.get())
    }
}
