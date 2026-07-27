package cn.silverdragon.draarl.radio

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder

internal class OpusFrameEncoder {
    private val encoder = OpusEncoder(
        OpusAudioFormat.SAMPLE_RATE,
        OpusAudioFormat.CHANNELS,
        OpusApplication.OPUS_APPLICATION_VOIP,
    ).apply {
        bitrate = OpusAudioFormat.BIT_RATE
        complexity = 5
    }

    fun encode(pcm: ShortArray): ByteArray? {
        require(pcm.size == OpusAudioFormat.FRAME_SAMPLES) { "Opus PCM frame length is invalid" }
        val encoded = ByteArray(OpusAudioFormat.MAX_ENCODED_FRAME)
        val encodedLength = encoder.encode(
            pcm,
            0,
            OpusAudioFormat.FRAME_SAMPLES,
            encoded,
            0,
            encoded.size,
        )
        return encoded.takeIf { encodedLength > 0 }?.copyOf(encodedLength)
    }
}

internal class OpusFrameDecoder {
    private val decoder = OpusDecoder(OpusAudioFormat.SAMPLE_RATE, OpusAudioFormat.CHANNELS)

    fun decode(frame: ByteArray): ShortArray {
        val pcm = ShortArray(OpusAudioFormat.MAX_DECODE_SAMPLES)
        val decodedSamples = decoder.decode(
            frame,
            0,
            frame.size,
            pcm,
            0,
            OpusAudioFormat.MAX_DECODE_SAMPLES,
            false,
        )
        return if (decodedSamples > 0) pcm.copyOf(decodedSamples) else ShortArray(0)
    }

    fun reset() {
        decoder.resetState()
    }
}
