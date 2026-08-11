package cn.silverdragon.draarl.radio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object OpusRecordingWavEncoder {
    fun encode(rawBytes: ByteArray): ByteArray {
        val recording = RawOpusRecording.decode(rawBytes)
        require(
            recording.sampleRate == OpusAudioFormat.SAMPLE_RATE &&
                recording.channels == OpusAudioFormat.CHANNELS
        ) { "不支持的语音采样格式" }

        val pcm = ByteArrayOutputStream()
        val decoder = OpusFrameDecoder()
        recording.splitFrames().forEach { frame ->
            decoder.decode(frame).forEach { sample ->
                pcm.write(sample.toInt() and BYTE_MASK)
                pcm.write((sample.toInt() ushr BYTE_SHIFT) and BYTE_MASK)
            }
        }
        val pcmBytes = pcm.toByteArray()
        require(pcmBytes.isNotEmpty()) { "语音记录没有可分享的数据" }

        return ByteBuffer.allocate(WAV_HEADER_SIZE + pcmBytes.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(WAV_HEADER_SIZE - RIFF_SIZE_EXCLUDED_BYTES + pcmBytes.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(PCM_FORMAT_SIZE)
            putShort(PCM_FORMAT)
            putShort(recording.channels.toShort())
            putInt(recording.sampleRate)
            putInt(recording.sampleRate * recording.channels * BYTES_PER_SAMPLE)
            putShort((recording.channels * BYTES_PER_SAMPLE).toShort())
            putShort(BITS_PER_SAMPLE)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmBytes.size)
            put(pcmBytes)
        }.array()
    }

    private const val WAV_HEADER_SIZE = 44
    private const val RIFF_SIZE_EXCLUDED_BYTES = 8
    private const val PCM_FORMAT_SIZE = 16
    private const val PCM_FORMAT: Short = 1
    private const val BYTES_PER_SAMPLE = 2
    private const val BITS_PER_SAMPLE: Short = 16
    private const val BYTE_MASK = 0xff
    private const val BYTE_SHIFT = 8
}
