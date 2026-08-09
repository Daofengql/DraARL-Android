package cn.silverdragon.draarl.radio

import android.content.Context

interface RadioAudioStore {
    fun get(key: String): ByteArray?
    fun put(key: String, bytes: ByteArray)
    fun contains(key: String): Boolean
    fun clear()
    fun sizeBytes(): Long
}

interface RadioAudioCapture {
    fun start(onPacket: (ByteArray) -> Unit, onError: (String) -> Unit): Boolean
    fun stop()
}

interface RadioAudioPlayback {
    fun playLocal(mergedFrames: ByteArray, onError: (String) -> Unit)
    fun playStream(streamKey: String, mergedFrames: ByteArray, onError: (String) -> Unit)
    fun endStream(streamKey: String)
    fun playRecording(
        audioCacheKey: String,
        audioUrl: String,
        onFinished: () -> Unit,
        onError: (String) -> Unit
    ): Boolean
    fun stopRecording()
    fun setMuted(value: Boolean)
    fun setDenoiseEnabled(value: Boolean)
    fun setDenoiseWetMix(value: Float)
    fun resetDecoder()
}

interface RadioAudioEngine {
    val capture: RadioAudioCapture
    val playback: RadioAudioPlayback
    fun release()
}

internal data class RadioAudioRuntime(val store: RadioAudioStore, val engine: RadioAudioEngine)

internal object AndroidRadioAudioRuntime {
    fun create(context: Context, listener: UdpRadioListener): RadioAudioRuntime {
        val store = RadioAudioCache(context.applicationContext.filesDir.resolve("radio_audio"))
        return RadioAudioRuntime(
            store = store,
            engine = OpusRadioAudioEngine(
                OpusAudioEngine(
                    audioCache = store,
                    onPlaybackLevel = listener::onPlaybackLevel,
                    onCaptureLevel = listener::onTransmitLevel
                )
            )
        )
    }
}

private class OpusRadioAudioEngine(private val delegate: OpusAudioEngine) : RadioAudioEngine {
    override val capture = object : RadioAudioCapture {
        override fun start(onPacket: (ByteArray) -> Unit, onError: (String) -> Unit): Boolean =
            delegate.startCapture(onPacket, onError)

        override fun stop() = delegate.stopCapture()
    }

    override val playback = object : RadioAudioPlayback {
        override fun playLocal(mergedFrames: ByteArray, onError: (String) -> Unit) =
            delegate.play(mergedFrames, onError)

        override fun playStream(streamKey: String, mergedFrames: ByteArray, onError: (String) -> Unit) =
            delegate.play(streamKey, mergedFrames, onError)

        override fun endStream(streamKey: String) = delegate.endLiveStream(streamKey)

        override fun playRecording(
            audioCacheKey: String,
            audioUrl: String,
            onFinished: () -> Unit,
            onError: (String) -> Unit
        ): Boolean = delegate.playRecording(audioCacheKey, audioUrl, onFinished, onError)

        override fun stopRecording() = delegate.stopRecordingPlayback()

        override fun setMuted(value: Boolean) = delegate.setMuted(value)

        override fun setDenoiseEnabled(value: Boolean) = delegate.setDenoiseEnabled(value)

        override fun setDenoiseWetMix(value: Float) = delegate.setDenoiseWetMix(value)

        override fun resetDecoder() = delegate.resetDecoder()
    }

    override fun release() = delegate.release()
}
