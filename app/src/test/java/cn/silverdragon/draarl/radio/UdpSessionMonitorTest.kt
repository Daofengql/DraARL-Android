package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpSessionMonitorTest {
    @Test
    fun `start schedules heartbeat and watchdog at protocol intervals`() {
        val fixture = fixture()
        var heartbeats = 0
        val watchdogTimes = mutableListOf<Long>()

        fixture.monitor.start(
            heartbeat = { heartbeats++ },
            watchdog = { watchdogTimes += it },
            onServerSilence = {}
        )
        fixture.scheduler.periodic.forEach(FakeTask::run)

        assertEquals(listOf(0L to 2_000L, 250L to 250L), fixture.scheduler.periodic.map { it.delay to it.interval })
        assertEquals(1, heartbeats)
        assertEquals(listOf(FIXED_NOW), watchdogTimes)
    }

    @Test
    fun `server silence fires only after timeout is exceeded`() {
        val fixture = fixture()
        var timeouts = 0
        fixture.monitor.recordServerPacket()
        fixture.monitor.start({}, {}, { timeouts++ })
        val watchdog = fixture.scheduler.periodic.last()

        fixture.clock.now = FIXED_NOW + 8_000L
        watchdog.run()
        fixture.clock.now++
        watchdog.run()

        assertEquals(1, timeouts)
    }

    @Test
    fun `new server packet refreshes silence deadline`() {
        val fixture = fixture()
        var timeouts = 0
        fixture.monitor.recordServerPacket()
        fixture.monitor.start({}, {}, { timeouts++ })
        val watchdog = fixture.scheduler.periodic.last()

        fixture.clock.now = FIXED_NOW + 7_000L
        fixture.monitor.recordServerPacket()
        fixture.clock.now = FIXED_NOW + 14_999L
        watchdog.run()
        fixture.clock.now = FIXED_NOW + 15_001L
        watchdog.run()

        assertEquals(1, timeouts)
    }

    @Test
    fun `sent packet timestamp is retained for reconnect policy`() {
        val fixture = fixture()

        fixture.monitor.recordPacketSent()
        fixture.clock.now += 5_000L

        assertEquals(FIXED_NOW, fixture.monitor.lastPacketSentAt())
    }

    @Test
    fun `restart and stop cancel owned periodic tasks`() {
        val fixture = fixture()
        fixture.monitor.start({}, {}, {})
        val previous = fixture.scheduler.periodic.toList()

        fixture.monitor.start({}, {}, {})

        assertTrue(previous.all(FakeTask::cancelled))
        assertFalse(fixture.scheduler.periodic.takeLast(2).any(FakeTask::cancelled))

        fixture.monitor.stop()
        assertTrue(fixture.scheduler.periodic.takeLast(2).all(FakeTask::cancelled))
    }

    private fun fixture(): Fixture {
        val scheduler = FakeRadioScheduler()
        val clock = MutableRadioClock(FIXED_NOW)
        return Fixture(
            monitor = UdpSessionMonitor(
                scheduler = scheduler,
                clock = clock,
                heartbeatIntervalMillis = 2_000L,
                watchdogIntervalMillis = 250L,
                serverSilenceTimeoutMillis = 8_000L
            ),
            scheduler = scheduler,
            clock = clock
        )
    }

    private data class Fixture(
        val monitor: UdpSessionMonitor,
        val scheduler: FakeRadioScheduler,
        val clock: MutableRadioClock
    )

    private class MutableRadioClock(var now: Long) : RadioClock {
        override fun nowMillis(): Long = now
    }

    private class FakeRadioScheduler : RadioScheduler {
        val periodic = mutableListOf<FakeTask>()

        override fun execute(task: () -> Unit) = task()

        override fun schedule(delayMillis: Long, task: () -> Unit): RadioScheduledTask =
            error("One-shot tasks are not used by UdpSessionMonitor")

        override fun scheduleWithFixedDelay(
            initialDelayMillis: Long,
            delayMillis: Long,
            task: () -> Unit
        ): RadioScheduledTask = FakeTask(initialDelayMillis, delayMillis, task).also(periodic::add)

        override fun close() = Unit
    }

    private class FakeTask(val delay: Long, val interval: Long, private val action: () -> Unit) : RadioScheduledTask {
        var cancelled = false

        override fun cancel() {
            cancelled = true
        }

        fun run() {
            if (!cancelled) action()
        }
    }

    private companion object {
        const val FIXED_NOW = 1_000_000L
    }
}
