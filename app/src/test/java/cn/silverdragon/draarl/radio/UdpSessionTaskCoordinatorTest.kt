package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpSessionTaskCoordinatorTest {
    @Test
    fun `session owns heartbeat watchdog and ptt timeout`() {
        val scheduler = FakeRadioScheduler()
        val coordinator = UdpSessionTaskCoordinator(scheduler)
        coordinator.startSession(heartbeatIntervalMillis = 2_000, watchdogIntervalMillis = 250, {}, {})
        coordinator.schedulePttTimeout(5_000) {}

        coordinator.stopSession()

        assertEquals(listOf(0L to 2_000L, 250L to 250L), scheduler.periodic.map { it.delay to it.interval })
        assertTrue(scheduler.periodic.all(FakeTask::cancelled))
        assertTrue(scheduler.oneShot.single().cancelled)
    }

    @Test
    fun `restarting session cancels previous periodic tasks`() {
        val scheduler = FakeRadioScheduler()
        val coordinator = UdpSessionTaskCoordinator(scheduler)
        coordinator.startSession(2_000, 250, {}, {})
        val previous = scheduler.periodic.toList()

        coordinator.startSession(3_000, 500, {}, {})

        assertTrue(previous.all(FakeTask::cancelled))
        assertFalse(scheduler.periodic.takeLast(2).any(FakeTask::cancelled))
    }

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
        coordinator.startSession(2_000, 250, {}, {})
        coordinator.scheduleReconnect(3_000, shouldKeep = { true }) {}
        val reconnect = scheduler.oneShot.single()

        coordinator.cancelReconnect()
        coordinator.close()

        assertTrue(reconnect.cancelled)
        assertTrue(scheduler.periodic.all(FakeTask::cancelled))
        assertTrue(scheduler.closed)
    }

    private class FakeRadioScheduler : RadioScheduler {
        val periodic = mutableListOf<FakeTask>()
        val oneShot = mutableListOf<FakeTask>()
        var closed = false

        override fun execute(task: () -> Unit) = task()

        override fun schedule(delayMillis: Long, task: () -> Unit): RadioScheduledTask =
            FakeTask(delayMillis, interval = null, action = task).also(oneShot::add)

        override fun scheduleWithFixedDelay(
            initialDelayMillis: Long,
            delayMillis: Long,
            task: () -> Unit
        ): RadioScheduledTask = FakeTask(initialDelayMillis, delayMillis, task).also(periodic::add)

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
