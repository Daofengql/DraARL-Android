package cn.silverdragon.draarl.radio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

class OpusAudioEngine(
    audioCache: RadioAudioStore,
    private val historicalAudioLoader: HistoricalAudioLoader = HistoricalAudioLoader(audioCache),
    onPlaybackLevel: (Float) -> Unit = {},
    private val onCaptureLevel: (Float) -> Unit = {},
    ioDispatcher: CoroutineDispatcher = defaultAudioIoDispatcher()
) {
    private val released = AtomicBoolean(false)
    private val recordingPlaybackGeneration = AtomicInteger(0)
    private val capture = OpusCaptureController()
    private val playback = OpusPlaybackController(onPlaybackLevel)
    private val downloadScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    fun startCapture(onPacket: (ByteArray) -> Unit, onError: (String) -> Unit): Boolean {
        if (released.get()) return false
        stopRecordingPlayback()
        return capture.start(onPacket, onCaptureLevel, onError)
    }

    fun stopCapture() {
        capture.stop()
    }

    fun play(mergedFrames: ByteArray, onError: (String) -> Unit) {
        play(DEFAULT_LIVE_STREAM, mergedFrames, onError)
    }

    fun play(streamKey: String, mergedFrames: ByteArray, onError: (String) -> Unit) {
        if (released.get() || capture.isCapturing) return
        playback.playLive(streamKey, mergedFrames, onError)
    }

    fun endLiveStream(streamKey: String) {
        if (!released.get()) playback.endLiveStream(streamKey)
    }

    fun playRecording(
        audioCacheKey: String,
        audioUrl: String,
        onFinished: () -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (
            released.get() || capture.isCapturing ||
            (audioCacheKey.isBlank() && audioUrl.isBlank())
        ) {
            return false
        }
        val generation = recordingPlaybackGeneration.incrementAndGet()
        val download = downloadScope.launch(start = CoroutineStart.LAZY) {
            if (!isRecordingPlaybackActive(generation)) return@launch
            val result = runCatching {
                runInterruptible { historicalAudioLoader.load(audioCacheKey, audioUrl) }
            }
            if (result.exceptionOrNull() is CancellationException) return@launch
            if (result.isFailure) {
                failRecordingPlayback(generation, result.exceptionOrNull()?.message ?: "语音回放失败", onError)
                return@launch
            }
            val playbackSubmitted = playback.playRecording(
                bytes = result.getOrThrow(),
                isActive = { isRecordingPlaybackActive(generation) },
                onFinished = { finishRecordingPlayback(generation, onFinished) },
                onError = { message -> failRecordingPlayback(generation, message, onError) }
            )
            if (!playbackSubmitted) {
                failRecordingPlayback(generation, "语音播放线程不可用", onError)
            }
        }
        val submitted = isRecordingPlaybackActive(generation) && download.start()
        if (!submitted && !released.get()) onError("无法启动语音回放")
        return submitted
    }

    fun stopRecordingPlayback() {
        recordingPlaybackGeneration.incrementAndGet()
        playback.stopRecording()
    }

    fun setMuted(value: Boolean) {
        playback.setMuted(value)
    }

    fun setDenoiseEnabled(value: Boolean) {
        playback.setDenoiseEnabled(value)
    }

    fun setDenoiseWetMix(value: Float) {
        playback.setDenoiseWetMix(value)
    }

    fun resetDecoder() {
        if (!released.get()) playback.resetDecoder()
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        recordingPlaybackGeneration.incrementAndGet()
        downloadScope.cancel()
        capture.release()
        playback.release()
    }

    private fun finishRecordingPlayback(generation: Int, onFinished: () -> Unit) {
        if (
            !released.get() &&
            recordingPlaybackGeneration.compareAndSet(generation, generation + 1)
        ) {
            onFinished()
        }
    }

    private fun failRecordingPlayback(generation: Int, message: String, onError: (String) -> Unit) {
        if (
            !released.get() &&
            recordingPlaybackGeneration.compareAndSet(generation, generation + 1)
        ) {
            onError(message)
        }
    }

    private fun isRecordingPlaybackActive(expectedGeneration: Int): Boolean =
        !released.get() && recordingPlaybackGeneration.get() == expectedGeneration

    companion object {
        private const val DEFAULT_LIVE_STREAM = "local-monitor"
    }
}

@Suppress("InjectDispatcher")
private fun defaultAudioIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
