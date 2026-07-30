package cn.silverdragon.draarl.radio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import cn.silverdragon.draarl.protocol.DraarlProtocol
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal class OpusPlaybackController(
    private val onLevel: (Float) -> Unit = {},
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "draarl-opus-playback")
    }
    private val released = AtomicBoolean(false)
    private val decoder = OpusFrameDecoder()
    private val denoiser = RnnoisePlaybackDenoiser()
    private var audioTrack: AudioTrack? = null
    @Volatile private var muted = false

    fun playLive(mergedFrames: ByteArray, onError: (String) -> Unit) {
        if (released.get() || muted) return
        val frames = DraarlProtocol.splitOpusFrames(mergedFrames)
        if (frames.isEmpty()) return
        val submitted = executeIfActive(executor, { !released.get() }) {
            runCatching {
                val track = obtainTrack()
                for (frame in frames) {
                    if (released.get()) break
                    writeFrame(track, frame)
                }
            }.onFailure { error ->
                onLevel(0f)
                if (!released.get()) onError(error.message ?: "语音播放失败")
            }
        }
        if (!submitted && !released.get()) onError("语音播放线程不可用")
    }

    fun playRecording(
        bytes: ByteArray,
        isActive: () -> Boolean,
        onFinished: () -> Unit,
        onError: (String) -> Unit,
    ): Boolean = executeIfActive(executor, { !released.get() && isActive() }) {
        val result = runCatching {
            val recording = RawOpusRecording.decode(bytes)
            require(
                recording.sampleRate == OpusAudioFormat.SAMPLE_RATE &&
                    recording.channels == OpusAudioFormat.CHANNELS,
            ) { "不支持的语音采样格式" }
            val frames = recording.splitFrames()
            require(frames.isNotEmpty()) { "语音记录没有可播放的数据" }
            decoder.reset()
            denoiser.reset()
            val track = obtainTrack(reset = true)
            for (frame in frames) {
                if (!isActive() || released.get()) break
                writeFrame(track, frame)
            }
        }
        onLevel(0f)
        if (isActive() && !released.get()) {
            result.fold(
                onSuccess = { onFinished() },
                onFailure = { onError(it.message ?: "语音回放失败") },
            )
        }
    }

    fun stopRecording() {
        onLevel(0f)
        executeIfActive(executor, { !released.get() }) {
            runCatching { audioTrack?.pause() }
            runCatching { audioTrack?.flush() }
        }
    }

    fun setMuted(value: Boolean) {
        muted = value
        if (value) onLevel(0f)
        executeIfActive(executor, { !released.get() }) {
            runCatching { audioTrack?.setVolume(if (value) 0f else 1f) }
        }
    }

    fun setDenoiseEnabled(value: Boolean) {
        executeIfActive(executor, { !released.get() }) {
            denoiser.setEnabled(value)
        }
    }

    fun setDenoiseWetMix(value: Float) {
        executeIfActive(executor, { !released.get() }) {
            denoiser.setWetMix(value)
        }
    }

    fun resetDecoder() {
        onLevel(0f)
        executeIfActive(executor, { !released.get() }) {
            decoder.reset()
            denoiser.reset()
            runCatching { audioTrack?.flush() }
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        onLevel(0f)
        val cleanup = Runnable {
            val track = audioTrack
            audioTrack = null
            runCatching { track?.stop() }
            runCatching { track?.release() }
            denoiser.release()
        }
        try {
            executor.execute(cleanup)
        } catch (_: RejectedExecutionException) {
            cleanup.run()
        }
        executor.shutdown()
    }

    private fun obtainTrack(reset: Boolean = false): AudioTrack {
        val track = audioTrack ?: createAudioTrack().also { audioTrack = it }
        runCatching { track.setVolume(if (muted) 0f else 1f) }
        if (reset) {
            runCatching { track.pause() }
            runCatching { track.flush() }
        }
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        return track
    }

    private fun createAudioTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            OpusAudioFormat.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(OpusAudioFormat.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, OpusAudioFormat.FRAME_SAMPLES * 8))
            .build()
    }

    private fun writeFrame(track: AudioTrack, frame: ByteArray) {
        val pcm = decoder.decode(frame)
        if (pcm.isNotEmpty()) {
            val playbackPcm = denoiser.process(pcm)
            onLevel(normalizedPcmLevel(playbackPcm))
            track.write(playbackPcm, 0, playbackPcm.size, AudioTrack.WRITE_BLOCKING)
        }
    }
}
