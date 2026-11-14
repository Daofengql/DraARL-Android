package cn.silverdragon.draarl.radio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackQueueTest {
    @Test
    fun `slow playback discards oldest pending packets and resets the next output`() {
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val finished = CountDownLatch(6)
        val played = mutableListOf<Pair<Int, Boolean>>()
        val queue = LivePlaybackQueue<Int>(executor, { true }, { value, reset ->
            if (value == 0) { started.countDown(); resume.await() }
            played += value to reset
            finished.countDown()
        })
        try {
            queue.submit(0)
            assertTrue(started.await(2, TimeUnit.SECONDS))
            for (value in 1..10) queue.submit(value)
            assertEquals(5, queue.droppedBacklog.get())
            resume.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(0, 6, 7, 8, 9, 10), played.map { it.first })
            assertTrue(played[1].second)
        } finally { resume.countDown(); executor.shutdownNow() }
    }

    @Test
    fun `playback gives queued control work a turn between packets`() {
        val executor = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val finished = CountDownLatch(3)
        val order = mutableListOf<String>()
        val queue = LivePlaybackQueue<Int>(executor, { true }, { value, _ ->
            if (value == 1) { started.countDown(); resume.await() }
            order += "audio$value"; finished.countDown()
        })
        try {
            queue.submit(1)
            assertTrue(started.await(2, TimeUnit.SECONDS))
            queue.submit(2)
            executor.execute { order += "control"; finished.countDown() }
            resume.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("audio1", "control", "audio2"), order)
        } finally { resume.countDown(); executor.shutdownNow() }
    }
}
