package cn.silverdragon.draarl.radio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import cn.silverdragon.draarl.protocol.DraarlProtocol
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class OpusCaptureController {
    private val capturing = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val released = AtomicBoolean(false)
    @Volatile private var captureThread: Thread? = null
    @Volatile private var audioRecord: AudioRecord? = null

    val isCapturing: Boolean
        get() = capturing.get()

    @SuppressLint("MissingPermission")
    fun start(
        onPacket: (ByteArray) -> Unit,
        onLevel: (Float) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        if (released.get()) return false
        if (!capturing.compareAndSet(false, true)) return true
        val currentGeneration = generation.incrementAndGet()
        var createdRecorder: AudioRecord? = null
        return runCatching {
            val minimumBuffer = AudioRecord.getMinBufferSize(
                OpusAudioFormat.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                OpusAudioFormat.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimumBuffer, OpusAudioFormat.FRAME_SAMPLES * 4),
            )
            createdRecorder = recorder
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }
            if (!isActive(currentGeneration)) {
                if (generation.get() == currentGeneration) capturing.set(false)
                recorder.release()
                return false
            }
            audioRecord = recorder
            captureThread = Thread(
                { captureLoop(recorder, currentGeneration, onPacket, onLevel, onError) },
                "draarl-opus-capture",
            ).apply(Thread::start)
            true
        }.getOrElse { error ->
            if (generation.get() == currentGeneration) capturing.set(false)
            createdRecorder?.let(::releaseRecorder)
            if (!released.get()) onError(error.message ?: "无法启动麦克风")
            false
        }
    }

    fun stop() {
        generation.incrementAndGet()
        capturing.set(false)
        runCatching { audioRecord?.stop() }
        captureThread?.interrupt()
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        stop()
    }

    private fun captureLoop(
        recorder: AudioRecord,
        currentGeneration: Int,
        onPacket: (ByteArray) -> Unit,
        onLevel: (Float) -> Unit,
        onError: (String) -> Unit,
    ) {
        val encoder = OpusFrameEncoder()
        val accumulator = ArrayList<ByteArray>(OpusAudioFormat.FRAMES_PER_PACKET)
        try {
            recorder.startRecording()
            while (isActive(currentGeneration) && !Thread.currentThread().isInterrupted) {
                val pcm = readFrame(recorder, currentGeneration) ?: break
                onLevel(normalizedPcmLevel(pcm))
                encoder.encode(pcm)?.let(accumulator::add)
                if (accumulator.size >= OpusAudioFormat.FRAMES_PER_PACKET) {
                    onPacket(DraarlProtocol.mergeOpusFrames(accumulator.toList()))
                    accumulator.clear()
                }
            }
        } catch (error: Exception) {
            if (isActive(currentGeneration)) onError(error.message ?: "语音采集失败")
        } finally {
            onLevel(0f)
            if (generation.get() == currentGeneration) capturing.set(false)
            runCatching { recorder.stop() }
            releaseRecorder(recorder)
            synchronized(this) {
                if (captureThread === Thread.currentThread()) captureThread = null
            }
        }
    }

    private fun readFrame(recorder: AudioRecord, currentGeneration: Int): ShortArray? {
        val frame = ShortArray(OpusAudioFormat.FRAME_SAMPLES)
        var offset = 0
        while (offset < frame.size && isActive(currentGeneration)) {
            val read = recorder.read(frame, offset, frame.size - offset, AudioRecord.READ_BLOCKING)
            if (read <= 0) return null
            offset += read
        }
        return frame.takeIf { offset == frame.size }
    }

    private fun isActive(expectedGeneration: Int): Boolean =
        capturing.get() && generation.get() == expectedGeneration && !released.get()

    private fun releaseRecorder(recorder: AudioRecord) {
        runCatching { recorder.release() }
        synchronized(this) {
            if (audioRecord === recorder) audioRecord = null
        }
    }
}
