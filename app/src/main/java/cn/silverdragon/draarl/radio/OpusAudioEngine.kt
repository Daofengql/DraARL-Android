package cn.silverdragon.draarl.radio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val recordingPlaybackLock = Any()
    private val recordingDownloadJob = AtomicReference<Job?>(null)
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
        synchronized(recordingPlaybackLock) {
            if (
                released.get() || capture.isCapturing ||
                (audioCacheKey.isBlank() && audioUrl.isBlank())
            ) {
                return false
            }
            val generation = recordingPlaybackGeneration.incrementAndGet()
            val download = createRecordingDownload(
                generation = generation,
                audioCacheKey = audioCacheKey,
                audioUrl = audioUrl,
                onFinished = onFinished,
                onError = onError
            )
            recordingDownloadJob.getAndSet(download)?.cancel()
            val submitted = isRecordingPlaybackActive(generation) && download.start()
            if (!submitted) {
                recordingDownloadJob.compareAndSet(download, null)
                download.cancel()
                if (!released.get()) onError("无法启动语音回放")
            }
            return submitted
        }
    }

    fun stopRecordingPlayback() {
        synchronized(recordingPlaybackLock) {
            recordingPlaybackGeneration.incrementAndGet()
            recordingDownloadJob.getAndSet(null)?.cancel()
            playback.stopRecording()
        }
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
        synchronized(recordingPlaybackLock) {
            recordingPlaybackGeneration.incrementAndGet()
            recordingDownloadJob.getAndSet(null)?.cancel()
        }
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

    private fun createRecordingDownload(
        generation: Int,
        audioCacheKey: String,
        audioUrl: String,
        onFinished: () -> Unit,
        onError: (String) -> Unit
    ): Job = downloadScope.launch(start = CoroutineStart.LAZY) {
        try {
            loadAndPlayRecording(generation, audioCacheKey, audioUrl, onFinished, onError)
        } finally {
            recordingDownloadJob.compareAndSet(coroutineContext[Job], null)
        }
    }

    private suspend fun loadAndPlayRecording(
        generation: Int,
        audioCacheKey: String,
        audioUrl: String,
        onFinished: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isRecordingPlaybackActive(generation)) return
        val result = runCatching {
            runInterruptible { historicalAudioLoader.load(audioCacheKey, audioUrl) }
        }
        when {
            result.exceptionOrNull() is CancellationException -> Unit

            result.isFailure -> failRecordingPlayback(
                generation,
                result.exceptionOrNull()?.message ?: "语音回放失败",
                onError
            )

            else -> {
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
