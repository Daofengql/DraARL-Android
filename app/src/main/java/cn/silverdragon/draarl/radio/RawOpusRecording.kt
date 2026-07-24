package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.protocol.DraarlProtocol
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class RawOpusRecording(
    val sampleRate: Int,
    val channels: Int,
    val frameSize: Int,
    val payload: ByteArray,
    val frameCount: Int = 0,
) {
    fun splitFrames(): List<ByteArray> {
        require(frameCount > 0) { "语音记录没有帧索引" }
        val frames = ArrayList<ByteArray>(frameCount)
        var offset = 0
        repeat(frameCount) { index ->
            require(offset + FRAME_LENGTH_BYTES <= payload.size) {
                "语音记录第 ${index + 1} 帧长度不完整"
            }
            val length = (payload[offset].toInt() and 0xff) or
                ((payload[offset + 1].toInt() and 0xff) shl 8)
            offset += FRAME_LENGTH_BYTES
            require(length in 1..MAX_OPUS_FRAME_BYTES && offset + length <= payload.size) {
                "语音记录第 ${index + 1} 帧已损坏"
            }
            frames += payload.copyOfRange(offset, offset + length)
            offset += length
        }
        require(offset == payload.size) { "语音记录包含无法识别的尾部数据" }
        return frames
    }

    companion object {
        private const val HEADER_SIZE = 24
        private const val FRAME_LENGTH_BYTES = 2
        private const val MAX_OPUS_FRAME_BYTES = 1_275
        private const val VERSION = 1
        private val MAGIC = "OPUS".toByteArray(Charsets.US_ASCII)

        fun hasHeader(bytes: ByteArray): Boolean =
            bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

        fun decode(bytes: ByteArray): RawOpusRecording {
            require(bytes.size >= HEADER_SIZE) { "语音记录数据不完整" }
            require(hasHeader(bytes)) {
                "无法识别语音记录格式"
            }
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val version = header.getShort(4).toInt() and 0xffff
            require(version == VERSION) { "不支持的语音记录版本：$version" }
            val recording = RawOpusRecording(
                sampleRate = header.getInt(6).also { require(it > 0) { "语音记录采样率无效" } },
                channels = (header.getShort(10).toInt() and 0xffff).also {
                    require(it > 0) { "语音记录声道数无效" }
                },
                frameSize = (header.getShort(12).toInt() and 0xffff).also {
                    require(it > 0) { "语音记录帧大小无效" }
                },
                payload = bytes.copyOfRange(HEADER_SIZE, bytes.size),
                frameCount = header.getInt(14).also { require(it > 0) { "语音记录帧数无效" } },
            )
            recording.splitFrames()
            return recording
        }

        fun encode(
            sampleRate: Int,
            channels: Int,
            frameSize: Int,
            frames: List<ByteArray>,
        ): ByteArray {
            require(sampleRate > 0) { "语音记录采样率无效" }
            require(channels > 0) { "语音记录声道数无效" }
            require(frameSize > 0) { "语音记录帧大小无效" }
            require(frames.isNotEmpty()) { "语音记录没有可保存的数据" }
            frames.forEachIndexed { index, frame ->
                require(frame.size in 1..MAX_OPUS_FRAME_BYTES) {
                    "语音记录第 ${index + 1} 帧大小无效"
                }
            }
            val payloadSize = frames.sumOf { FRAME_LENGTH_BYTES + it.size }
            return ByteBuffer.allocate(HEADER_SIZE + payloadSize).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(MAGIC)
                putShort(VERSION.toShort())
                putInt(sampleRate)
                putShort(channels.toShort())
                putShort(frameSize.toShort())
                putInt(frames.size)
                put(ByteArray(6))
                frames.forEach { frame ->
                    putShort(frame.size.toShort())
                    put(frame)
                }
            }.array()
        }

        fun fromNetworkPayload(
            payload: ByteArray,
            sampleRate: Int = 16_000,
            channels: Int = 1,
            frameSize: Int = 960,
        ): ByteArray = encode(
            sampleRate = sampleRate,
            channels = channels,
            frameSize = frameSize,
            frames = DraarlProtocol.splitOpusFrames(payload),
        )
    }
}
