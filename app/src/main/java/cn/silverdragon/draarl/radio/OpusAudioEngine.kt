package cn.silverdragon.draarl.radio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import cn.silverdragon.draarl.protocol.DraarlProtocol
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OpusAudioEngine {
    private val capturing = AtomicBoolean(false)
    private val captureGeneration = AtomicInteger(0)
    private val released = AtomicBoolean(false)
    private val playbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "draarl-opus-playback")
    }
    @Volatile private var captureThread: Thread? = null
    @Volatile private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var decoder: OpusDecoder? = null
    @Volatile private var muted = false

    @SuppressLint("MissingPermission")
    fun startCapture(onPacket: (ByteArray) -> Unit, onError: (String) -> Unit): Boolean {
        if (released.get()) return false
        if (!capturing.compareAndSet(false, true)) return true
        val currentGeneration = captureGeneration.incrementAndGet()
        var createdRecorder: AudioRecord? = null
        return runCatching {
            val minimumBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimumBuffer, FRAME_SAMPLES * 4),
            )
            createdRecorder = recorder
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }
            if (
                released.get() ||
                !capturing.get() ||
                currentGeneration != captureGeneration.get()
            ) {
                if (captureGeneration.get() == currentGeneration) capturing.set(false)
                recorder.release()
                return false
            }
            audioRecord = recorder
            captureThread = Thread(
                { captureLoop(recorder, currentGeneration, onPacket, onError) },
                "draarl-opus-capture",
            ).apply {
                start()
            }
            true
        }.getOrElse { error ->
            if (captureGeneration.get() == currentGeneration) capturing.set(false)
            createdRecorder?.let(::releaseRecorder)
            if (!released.get()) onError(error.message ?: "无法启动麦克风")
            false
        }
    }

    fun stopCapture() {
        captureGeneration.incrementAndGet()
        capturing.set(false)
        val recorder = audioRecord
        val thread = captureThread
        runCatching { recorder?.stop() }
        thread?.interrupt()
        recorder?.let(::releaseRecorder)
    }

    fun play(mergedFrames: ByteArray, onError: (String) -> Unit) {
        if (released.get() || muted || capturing.get()) return
        val frames = DraarlProtocol.splitOpusFrames(mergedFrames)
        if (frames.isEmpty()) return
        runCatching {
            playbackExecutor.execute {
                if (released.get()) return@execute
                runCatching {
                    val localDecoder = decoder ?: OpusDecoder(SAMPLE_RATE, CHANNELS).also { decoder = it }
                    val track = audioTrack ?: createAudioTrack().also { audioTrack = it }
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                    frames.forEach { frame ->
                        val pcm = ShortArray(MAX_DECODE_SAMPLES)
                        val decodedSamples = localDecoder.decode(
                            frame,
                            0,
                            frame.size,
                            pcm,
                            0,
                            MAX_DECODE_SAMPLES,
                            false,
                        )
                        if (decodedSamples > 0) {
                            track.write(pcm, 0, decodedSamples, AudioTrack.WRITE_BLOCKING)
                        }
                    }
                }.onFailure { error -> if (!released.get()) onError(error.message ?: "语音播放失败") }
            }
        }.onFailure { error -> if (!released.get()) onError(error.message ?: "语音播放失败") }
    }

    fun setMuted(value: Boolean) {
        muted = value
        runCatching { audioTrack?.setVolume(if (value) 0f else 1f) }
    }

    fun resetDecoder() {
        if (released.get()) return
        runCatching {
            playbackExecutor.execute {
                if (released.get()) return@execute
                decoder = null
                runCatching { audioTrack?.flush() }
            }
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        stopCapture()
        runCatching {
            playbackExecutor.execute {
                runCatching { audioTrack?.stop() }
                runCatching { audioTrack?.release() }
                audioTrack = null
                decoder = null
            }
        }
        playbackExecutor.shutdown()
    }

    private fun captureLoop(
        recorder: AudioRecord,
        currentGeneration: Int,
        onPacket: (ByteArray) -> Unit,
        onError: (String) -> Unit,
    ) {
        val encoder = OpusEncoder(SAMPLE_RATE, CHANNELS, OpusApplication.OPUS_APPLICATION_VOIP).apply {
            bitrate = BIT_RATE
            complexity = 5
        }
        val accumulator = ArrayList<ByteArray>(FRAMES_PER_PACKET)
        try {
            recorder.startRecording()
            while (isCaptureActive(currentGeneration) && !Thread.currentThread().isInterrupted) {
                val pcm = readFrame(recorder, currentGeneration) ?: break
                val encoded = ByteArray(MAX_ENCODED_FRAME)
                val encodedLength = encoder.encode(
                    pcm,
                    0,
                    FRAME_SAMPLES,
                    encoded,
                    0,
                    encoded.size,
                )
                if (encodedLength > 0) accumulator += encoded.copyOf(encodedLength)
                if (accumulator.size >= FRAMES_PER_PACKET) {
                    onPacket(DraarlProtocol.mergeOpusFrames(accumulator.toList()))
                    accumulator.clear()
                }
            }
        } catch (error: Exception) {
            if (isCaptureActive(currentGeneration) && !released.get()) {
                onError(error.message ?: "语音采集失败")
            }
        } finally {
            if (captureGeneration.get() == currentGeneration) capturing.set(false)
            runCatching { recorder.stop() }
            releaseRecorder(recorder)
            synchronized(this) {
                if (captureThread === Thread.currentThread()) captureThread = null
            }
        }
    }

    private fun readFrame(recorder: AudioRecord, currentGeneration: Int): ShortArray? {
        val frame = ShortArray(FRAME_SAMPLES)
        var offset = 0
        while (offset < frame.size && isCaptureActive(currentGeneration)) {
            val read = recorder.read(frame, offset, frame.size - offset, AudioRecord.READ_BLOCKING)
            if (read <= 0) return null
            offset += read
        }
        return frame.takeIf { offset == frame.size }
    }

    private fun isCaptureActive(currentGeneration: Int): Boolean =
        capturing.get() && captureGeneration.get() == currentGeneration && !released.get()

    private fun createAudioTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, FRAME_SAMPLES * 8))
            .build()
    }

    private fun releaseRecorder(recorder: AudioRecord) {
        runCatching { recorder.release() }
        synchronized(this) {
            if (audioRecord === recorder) audioRecord = null
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNELS = 1
        private const val FRAME_SAMPLES = 960
        private const val MAX_DECODE_SAMPLES = 5_760
        private const val FRAMES_PER_PACKET = 2
        private const val BIT_RATE = 16_000
        private const val MAX_ENCODED_FRAME = 400
    }
}
