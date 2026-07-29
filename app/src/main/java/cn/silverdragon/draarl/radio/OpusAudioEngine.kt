package cn.silverdragon.draarl.radio

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OpusAudioEngine(
    audioCache: RadioAudioCache,
    private val historicalAudioLoader: HistoricalAudioLoader = HistoricalAudioLoader(audioCache),
    onPlaybackLevel: (Float) -> Unit = {},
) {
    private val released = AtomicBoolean(false)
    private val recordingPlaybackGeneration = AtomicInteger(0)
    private val capture = OpusCaptureController()
    private val playback = OpusPlaybackController(onPlaybackLevel)
    private val downloadExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "draarl-audio-download")
    }

    fun startCapture(onPacket: (ByteArray) -> Unit, onError: (String) -> Unit): Boolean {
        if (released.get()) return false
        stopRecordingPlayback()
        return capture.start(onPacket, onError)
    }

    fun stopCapture() {
        capture.stop()
    }

    fun play(mergedFrames: ByteArray, onError: (String) -> Unit) {
        if (released.get() || capture.isCapturing) return
        playback.playLive(mergedFrames, onError)
    }

    fun playRecording(
        audioCacheKey: String,
        audioUrl: String,
        onFinished: () -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        if (
            released.get() || capture.isCapturing ||
            (audioCacheKey.isBlank() && audioUrl.isBlank())
        ) return false
        val generation = recordingPlaybackGeneration.incrementAndGet()
        val submitted = executeIfActive(
            downloadExecutor,
            { isRecordingPlaybackActive(generation) },
        ) {
            val result = runCatching { historicalAudioLoader.load(audioCacheKey, audioUrl) }
            if (result.isFailure) {
                failRecordingPlayback(generation, result.exceptionOrNull()?.message ?: "语音回放失败", onError)
                return@executeIfActive
            }
            val playbackSubmitted = playback.playRecording(
                bytes = result.getOrThrow(),
                isActive = { isRecordingPlaybackActive(generation) },
                onFinished = { finishRecordingPlayback(generation, onFinished) },
                onError = { message -> failRecordingPlayback(generation, message, onError) },
            )
            if (!playbackSubmitted) {
                failRecordingPlayback(generation, "语音播放线程不可用", onError)
            }
        }
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

    fun resetDecoder() {
        if (!released.get()) playback.resetDecoder()
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        recordingPlaybackGeneration.incrementAndGet()
        downloadExecutor.shutdownNow()
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
}
