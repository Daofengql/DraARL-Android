package cn.silverdragon.draarl.radio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class CwPreviewPlayer(ioDispatcher: CoroutineDispatcher) {
    @Suppress("InjectDispatcher")
    constructor() : this(Dispatchers.IO)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val lock = Any()
    private val generation = AtomicInteger()
    private val released = AtomicBoolean(false)
    private var track: AudioTrack? = null

    fun play(text: String, wordsPerMinute: Int, toneHz: Int, onComplete: () -> Unit): Boolean {
        val normalized = CwToneGenerator.normalize(text)
        if (released.get() || normalized.isBlank() || normalized.length > CwToneGenerator.MAX_TEXT_LENGTH) return false
        val currentGeneration = generation.incrementAndGet()
        releaseActiveTrack()
        return runCatching {
            scope.launch { playOnWorker(normalized, wordsPerMinute, toneHz, currentGeneration, onComplete) }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        generation.incrementAndGet()
        releaseActiveTrack()
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        stop()
        scope.cancel()
    }

    private fun playOnWorker(
        text: String,
        wordsPerMinute: Int,
        toneHz: Int,
        currentGeneration: Int,
        onComplete: () -> Unit
    ) {
        var audioTrack: AudioTrack? = null
        val completed = AtomicBoolean(false)
        fun complete() {
            if (!completed.compareAndSet(false, true)) return
            audioTrack?.let(::releaseTrack)
            synchronized(lock) {
                if (track === audioTrack) track = null
            }
            mainHandler.post {
                if (isActive(currentGeneration)) onComplete()
            }
        }
        try {
            val tone = CwToneGenerator.generate(text, wordsPerMinute, toneHz)
            if (!isActive(currentGeneration)) return
            audioTrack = buildTrack(tone.samples.size) { complete() }
            synchronized(lock) {
                if (!isActive(currentGeneration)) return
                track = audioTrack
            }
            check(audioTrack.write(tone.samples, 0, tone.samples.size, AudioTrack.WRITE_BLOCKING) > 0)
            if (isActive(currentGeneration)) audioTrack.play()
        } catch (_: Exception) {
            complete()
        } finally {
            if (!isActive(currentGeneration)) audioTrack?.let(::releaseTrack)
        }
    }

    private fun buildTrack(sampleCount: Int, onComplete: () -> Unit): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(OpusAudioFormat.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(sampleCount * Short.SIZE_BYTES)
        .build()
        .also { audioTrack ->
            audioTrack.setNotificationMarkerPosition(sampleCount)
            audioTrack.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack) = onComplete()

                    override fun onPeriodicNotification(track: AudioTrack) = Unit
                },
                mainHandler
            )
        }

    private fun isActive(currentGeneration: Int): Boolean = !released.get() && generation.get() == currentGeneration

    private fun releaseActiveTrack() {
        val activeTrack = synchronized(lock) {
            track.also { track = null }
        }
        activeTrack?.let(::releaseTrack)
    }

    private fun releaseTrack(audioTrack: AudioTrack) {
        runCatching { audioTrack.stop() }
        runCatching { audioTrack.release() }
    }
}
