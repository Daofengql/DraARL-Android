package cn.silverdragon.draarl.radio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpPttCoordinatorTest {
    @Test
    fun `capture records only packets that were sent`() {
        val fixture = fixture()
        assertTrue(fixture.coordinator.start(GENERATION, timeoutMillis = 10_000L))
        assertFalse(fixture.coordinator.start(GENERATION, timeoutMillis = 10_000L))

        fixture.audio.emitPacket(VOICE_PACKET)
        fixture.callbacks.sendSucceeds = false
        fixture.audio.emitPacket(DROPPED_PACKET)
        fixture.coordinator.stop(GENERATION, tailSamples = shortArrayOf(), sendTailToRemote = true)

        assertEquals(listOf(VOICE_PACKET, DROPPED_PACKET), fixture.callbacks.sentPackets)
        val completed = fixture.callbacks.completed.single()
        assertEquals(FIXED_NOW, completed.startedAt)
        assertArrayEquals(VOICE_PACKET, completed.networkPayload)
        assertEquals(1, fixture.audio.captureStopCount)
    }

    @Test
    fun `capture and status rejection do not leave a timeout`() {
        val captureRejected = fixture(captureStarts = false)
        assertFalse(captureRejected.coordinator.start(GENERATION, 10_000L))
        assertEquals(0, captureRejected.callbacks.beginCalls)
        assertTrue(captureRejected.scheduler.oneShot.isEmpty())

        val statusRejected = fixture(beginStarts = false)
        assertFalse(statusRejected.coordinator.start(GENERATION, 10_000L))
        assertEquals(1, statusRejected.callbacks.beginCalls)
        assertEquals(1, statusRejected.audio.captureStopCount)
        assertTrue(statusRejected.scheduler.oneShot.isEmpty())
    }

    @Test
    fun `timeout replacement accounts for elapsed transmission time`() {
        val fixture = fixture()
        assertTrue(fixture.coordinator.start(GENERATION, timeoutMillis = 10_000L))
        val initialTimeout = fixture.scheduler.oneShot.single()

        fixture.clock.now += 4_000L
        fixture.coordinator.rescheduleTimeout(GENERATION, timeoutMillis = 10_000L)
        val replacement = fixture.scheduler.oneShot.last()
        replacement.run()

        assertTrue(initialTimeout.cancelled)
        assertEquals(6_000L, replacement.delay)
        assertEquals(listOf(GENERATION), fixture.callbacks.timeoutGenerations)
    }

    @Test
    fun `tail packets join the completed recording once`() {
        val fixture = fixture()
        assertTrue(fixture.coordinator.start(GENERATION, timeoutMillis = 10_000L))
        fixture.audio.emitPacket(VOICE_PACKET)

        fixture.coordinator.stop(GENERATION, tailSamples = shortArrayOf(1, 2), sendTailToRemote = true)
        fixture.coordinator.stop(GENERATION, tailSamples = shortArrayOf(1, 2), sendTailToRemote = true)

        assertEquals(1, fixture.callbacks.tailRequests)
        assertArrayEquals(VOICE_PACKET + TAIL_PACKET, fixture.callbacks.completed.single().networkPayload)
    }

    @Test
    fun `cancel prevents queued tail from completing stale transmission`() {
        val fixture = fixture(executeImmediately = false)
        assertTrue(fixture.coordinator.start(GENERATION, timeoutMillis = 10_000L))
        fixture.audio.emitPacket(VOICE_PACKET)
        fixture.coordinator.stop(GENERATION, tailSamples = shortArrayOf(1, 2), sendTailToRemote = true)

        fixture.coordinator.cancel()
        fixture.scheduler.executed.single().invoke()

        assertTrue(fixture.callbacks.completed.isEmpty())
        assertEquals(1, fixture.callbacks.tailRequests)
    }

    private fun fixture(
        captureStarts: Boolean = true,
        beginStarts: Boolean = true,
        executeImmediately: Boolean = true
    ): Fixture {
        val scheduler = FakeRadioScheduler(executeImmediately)
        val clock = MutableRadioClock(FIXED_NOW)
        val audio = FakeAudioEngine(captureStarts)
        val callbacks = FakeCallbacks(beginStarts)
        val coordinator = UdpPttCoordinator(
            clock = clock,
            tasks = UdpSessionTaskCoordinator(scheduler),
            audio = audio,
            callbacks = callbacks,
            maxCachedVoiceBytes = 1_024
        )
        return Fixture(coordinator, scheduler, clock, audio, callbacks)
    }

    private data class Fixture(
        val coordinator: UdpPttCoordinator,
        val scheduler: FakeRadioScheduler,
        val clock: MutableRadioClock,
        val audio: FakeAudioEngine,
        val callbacks: FakeCallbacks
    )

    private class MutableRadioClock(var now: Long) : RadioClock {
        override fun nowMillis(): Long = now
    }

    private class FakeCallbacks(private val beginStarts: Boolean) : UdpPttCallbacks {
        val sentPackets = mutableListOf<ByteArray>()
        val completed = mutableListOf<CompletedPttTransmission>()
        val timeoutGenerations = mutableListOf<Int>()
        var sendSucceeds = true
        var transmitting = false
        var beginCalls = 0
        var tailRequests = 0

        override fun isActive(generation: Int): Boolean = generation == GENERATION

        override fun isTransmitting(): Boolean = transmitting

        override fun beginTransmission(generation: Int): Boolean {
            beginCalls++
            transmitting = beginStarts
            return transmitting
        }

        override fun sendVoice(payload: ByteArray, generation: Int): Boolean {
            sentPackets += payload
            return sendSucceeds
        }

        override fun streamTail(request: UdpPttTailRequest) {
            tailRequests++
            if (request.shouldContinue() && request.sendToRemote) request.recordPacket(TAIL_PACKET)
        }

        override fun completeTransmission(generation: Int, transmission: CompletedPttTransmission) {
            transmitting = false
            completed += transmission
        }

        override fun reportError(message: String, generation: Int) = Unit

        override fun timeoutTransmission(generation: Int) {
            timeoutGenerations += generation
        }
    }

    private class FakeAudioEngine(private val captureStarts: Boolean) : RadioAudioEngine {
        private var packetCallback: ((ByteArray) -> Unit)? = null
        var captureStopCount = 0

        override val capture = object : RadioAudioCapture {
            override fun start(onPacket: (ByteArray) -> Unit, onError: (String) -> Unit): Boolean {
                packetCallback = onPacket
                return captureStarts
            }

            override fun stop() {
                captureStopCount++
            }
        }

        override val playback = object : RadioAudioPlayback {
            override fun playLocal(mergedFrames: ByteArray, onError: (String) -> Unit) = Unit
            override fun playStream(streamKey: String, mergedFrames: ByteArray, onError: (String) -> Unit) = Unit
            override fun endStream(streamKey: String) = Unit
            override fun playRecording(
                audioCacheKey: String,
                audioUrl: String,
                onFinished: () -> Unit,
                onError: (String) -> Unit
            ): Boolean = false
            override fun stopRecording() = Unit
            override fun setMuted(value: Boolean) = Unit
            override fun setDenoiseEnabled(value: Boolean) = Unit
            override fun setDenoiseWetMix(value: Float) = Unit
            override fun resetDecoder() = Unit
        }

        override fun release() = Unit

        fun emitPacket(payload: ByteArray) = requireNotNull(packetCallback)(payload)
    }

    private class FakeRadioScheduler(private val executeImmediately: Boolean) : RadioScheduler {
        val oneShot = mutableListOf<FakeTask>()
        val executed = mutableListOf<() -> Unit>()

        override fun execute(task: () -> Unit) {
            executed += task
            if (executeImmediately) task()
        }

        override fun schedule(delayMillis: Long, task: () -> Unit): RadioScheduledTask =
            FakeTask(delayMillis, task).also(oneShot::add)

        override fun scheduleWithFixedDelay(
            initialDelayMillis: Long,
            delayMillis: Long,
            task: () -> Unit
        ): RadioScheduledTask = error("Periodic tasks are not used by UdpPttCoordinator")

        override fun close() = Unit
    }

    private class FakeTask(val delay: Long, private val action: () -> Unit) : RadioScheduledTask {
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
        const val GENERATION = 7
        val VOICE_PACKET = byteArrayOf(1, 2, 3)
        val DROPPED_PACKET = byteArrayOf(4, 5)
        val TAIL_PACKET = byteArrayOf(6, 7)
    }
}
