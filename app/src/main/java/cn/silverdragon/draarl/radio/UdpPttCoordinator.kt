package cn.silverdragon.draarl.radio

import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

internal data class CompletedPttTransmission(val startedAt: Long, val networkPayload: ByteArray)

internal data class UdpPttTailRequest(
    val samples: ShortArray,
    val generation: Int,
    val shouldContinue: () -> Boolean,
    val sendToRemote: Boolean,
    val recordPacket: (ByteArray) -> Unit
)

internal interface UdpPttCallbacks {
    fun isActive(generation: Int): Boolean
    fun isTransmitting(): Boolean
    fun beginTransmission(generation: Int): Boolean
    fun sendVoice(payload: ByteArray, generation: Int): Boolean
    fun streamTail(request: UdpPttTailRequest)
    fun completeTransmission(generation: Int, transmission: CompletedPttTransmission)
    fun reportError(message: String, generation: Int)
    fun timeoutTransmission(generation: Int)
}

internal class UdpPttCoordinator(
    private val clock: RadioClock,
    private val tasks: UdpSessionTaskCoordinator,
    private val audio: RadioAudioEngine,
    private val callbacks: UdpPttCallbacks,
    private val maxCachedVoiceBytes: Int
) {
    private val finishing = AtomicBoolean(false)
    private val recordingLock = Any()

    @Volatile private var active = false

    private var startedAt = 0L
    private var recording = ByteArrayOutputStream()

    init {
        require(maxCachedVoiceBytes > 0) { "PTT recording limit must be positive" }
    }

    @Synchronized
    fun start(generation: Int, timeoutMillis: Long): Boolean {
        var transmissionStarted = false
        if (!active && !finishing.get()) {
            resetRecording()
            audio.playback.resetDecoder()
            val captureStarted = audio.capture.start(
                onPacket = { payload ->
                    if (active && callbacks.isTransmitting() && callbacks.sendVoice(payload, generation)) {
                        appendRecording(payload)
                    }
                },
                onError = { message -> callbacks.reportError(message, generation) }
            )
            if (captureStarted) {
                active = true
                transmissionStarted = callbacks.beginTransmission(generation)
                if (!transmissionStarted) {
                    active = false
                    audio.capture.stop()
                }
            }
        }
        if (transmissionStarted) {
            startedAt = clock.nowMillis()
            rescheduleTimeout(generation, timeoutMillis)
        }
        return transmissionStarted
    }

    @Synchronized
    fun stop(generation: Int, tailSamples: ShortArray, sendTailToRemote: Boolean) {
        if (!active || !finishing.compareAndSet(false, true)) return
        tasks.cancelPttTimeout()
        audio.capture.stop()
        if (tailSamples.isEmpty()) {
            finish(generation)
            return
        }
        audio.playback.resetDecoder()
        runCatching {
            tasks.execute {
                try {
                    runCatching {
                        callbacks.streamTail(
                            UdpPttTailRequest(
                                samples = tailSamples,
                                generation = generation,
                                shouldContinue = finishing::get,
                                sendToRemote = sendTailToRemote,
                                recordPacket = ::appendRecording
                            )
                        )
                    }.onFailure { error ->
                        callbacks.reportError(error.message ?: "发射尾音发送失败", generation)
                    }
                } finally {
                    finish(generation)
                }
            }
        }.onFailure {
            finish(generation)
        }
    }

    @Synchronized
    fun rescheduleTimeout(generation: Int, timeoutMillis: Long) {
        tasks.cancelPttTimeout()
        if (!active || !callbacks.isActive(generation) || !callbacks.isTransmitting()) return
        val remaining = timeoutMillis - (clock.nowMillis() - startedAt)
        tasks.schedulePttTimeout(remaining.coerceAtLeast(0L)) {
            if (active && callbacks.isActive(generation) && callbacks.isTransmitting()) {
                callbacks.timeoutTransmission(generation)
            }
        }
    }

    @Synchronized
    fun cancel() {
        active = false
        finishing.set(false)
        tasks.cancelPttTimeout()
        audio.capture.stop()
        resetRecording()
    }

    @Synchronized
    private fun finish(generation: Int) {
        if (!finishing.compareAndSet(true, false)) return
        active = false
        callbacks.completeTransmission(
            generation,
            CompletedPttTransmission(startedAt = startedAt, networkPayload = takeRecording())
        )
    }

    private fun appendRecording(payload: ByteArray) = synchronized(recordingLock) {
        val remaining = maxCachedVoiceBytes - recording.size()
        if (payload.size <= remaining) recording.write(payload)
    }

    private fun resetRecording() = synchronized(recordingLock) {
        recording = ByteArrayOutputStream()
    }

    private fun takeRecording(): ByteArray = synchronized(recordingLock) {
        recording.toByteArray().also { recording = ByteArrayOutputStream() }
    }
}
