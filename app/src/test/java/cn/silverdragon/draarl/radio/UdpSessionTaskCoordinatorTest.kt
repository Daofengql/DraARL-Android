package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpSessionTaskCoordinatorTest {
    @Test
    fun `ptt timeout replacement cancels stale callback`() {
        val scheduler = FakeRadioScheduler()
        val coordinator = UdpSessionTaskCoordinator(scheduler)
        var timeouts = 0
        coordinator.schedulePttTimeout(5_000) { timeouts++ }
        val stale = scheduler.oneShot.single()

        coordinator.schedulePttTimeout(10_000) { timeouts++ }
        scheduler.oneShot.last().run()

        assertTrue(stale.cancelled)
        assertEquals(1, timeouts)
    }

    @Test
    fun `reconnect is retained only while state still allows it`() {
        val scheduler = FakeRadioScheduler()
        val coordinator = UdpSessionTaskCoordinator(scheduler)
        var reconnects = 0

        coordinator.scheduleReconnect(3_000, shouldKeep = { false }) { reconnects++ }
        scheduler.oneShot.single().run()

        assertTrue(scheduler.oneShot.single().cancelled)
        assertEquals(0, reconnects)
    }

    @Test
    fun `cancel reconnect and close release every owned task`() {
        val scheduler = FakeRadioScheduler()
        val coordinator = UdpSessionTaskCoordinator(scheduler)
        coordinator.schedulePttTimeout(5_000) {}
        coordinator.scheduleReconnect(3_000, shouldKeep = { true }) {}
        val pttTimeout = scheduler.oneShot.first()
        val reconnect = scheduler.oneShot.last()

        coordinator.cancelReconnect()
        coordinator.close()

        assertTrue(pttTimeout.cancelled)
        assertTrue(reconnect.cancelled)
        assertTrue(scheduler.closed)
    }

    private class FakeRadioScheduler : RadioScheduler {
        val oneShot = mutableListOf<FakeTask>()
        var closed = false

        override fun execute(task: () -> Unit) = task()

        override fun schedule(delayMillis: Long, task: () -> Unit): RadioScheduledTask =
            FakeTask(delayMillis, interval = null, action = task).also(oneShot::add)

        override fun scheduleWithFixedDelay(
            initialDelayMillis: Long,
            delayMillis: Long,
            task: () -> Unit
        ): RadioScheduledTask = error("Periodic tasks belong to UdpSessionMonitor")

        override fun close() {
            closed = true
        }
    }

    private class FakeTask(val delay: Long, val interval: Long?, private val action: () -> Unit) : RadioScheduledTask {
        var cancelled = false

        override fun cancel() {
            cancelled = true
        }

        fun run() {
            if (!cancelled) action()
        }
    }
}
