package cn.silverdragon.draarl.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object DraarlProtocol {
    const val VERSION = "DraA"
    const val HEADER_SIZE = 90
    const val MAX_PACKET_SIZE = 800
    const val DEVICE_MODEL_ANDROID = 101
    const val SSID_ANDROID = 101

    const val TYPE_JWT_AUTH = 1
    const val TYPE_HEARTBEAT = 2
    const val TYPE_CONFIG = 3
    const val TYPE_TEXT = 4
    const val TYPE_OPUS_16K = 5

    fun encode(
        type: Int,
        data: ByteArray = byteArrayOf(),
        username: String = "",
        devicePassword: String = "",
        ssid: Int = SSID_ANDROID,
        deviceModel: Int = DEVICE_MODEL_ANDROID,
        dmrId: Int = 0,
        callsign: String = "",
    ): ByteArray {
        require(type in TYPE_JWT_AUTH..TYPE_OPUS_16K) { "Unsupported packet type: $type" }
        require(data.size + HEADER_SIZE <= MAX_PACKET_SIZE) { "DraARL packet exceeds $MAX_PACKET_SIZE bytes" }
        val packet = ByteArray(HEADER_SIZE + data.size)
        VERSION.toByteArray(Charsets.US_ASCII).copyInto(packet, 0)
        ByteBuffer.wrap(packet, 4, 2).order(ByteOrder.BIG_ENDIAN).putShort(packet.size.toShort())
        putFixed(packet, 6, 32, username, Charsets.UTF_8)
        putFixed(packet, 38, 10, devicePassword, Charsets.US_ASCII)
        packet[48] = type.toByte()
        packet[49] = deviceModel.toByte()
        packet[50] = ssid.toByte()
        packet[51] = (dmrId shr 16).toByte()
        packet[52] = (dmrId shr 8).toByte()
        packet[53] = dmrId.toByte()
        putFixed(packet, 54, 32, callsign, Charsets.US_ASCII)
        data.copyInto(packet, HEADER_SIZE)
        return packet
    }

    fun decode(bytes: ByteArray, size: Int = bytes.size): DraarlPacket? {
        if (size < HEADER_SIZE || size > bytes.size) return null
        if (!bytes.copyOfRange(0, 4).contentEquals(VERSION.toByteArray(Charsets.US_ASCII))) return null
        val declaredLength = ((bytes[4].toInt() and 0xff) shl 8) or (bytes[5].toInt() and 0xff)
        if (declaredLength != size || declaredLength > MAX_PACKET_SIZE) return null
        val type = bytes[48].toInt() and 0xff
        if (type !in TYPE_JWT_AUTH..TYPE_OPUS_16K) return null
        return DraarlPacket(
            username = readFixed(bytes, 6, 32, Charsets.UTF_8),
            devicePassword = readFixed(bytes, 38, 10, Charsets.US_ASCII),
            type = type,
            deviceModel = bytes[49].toInt() and 0xff,
            ssid = bytes[50].toInt() and 0xff,
            dmrId = ((bytes[51].toInt() and 0xff) shl 16) or
                ((bytes[52].toInt() and 0xff) shl 8) or (bytes[53].toInt() and 0xff),
            callsign = readFixed(bytes, 54, 32, Charsets.US_ASCII),
            data = bytes.copyOfRange(HEADER_SIZE, size),
        )
    }

    fun jwtAuth(token: String): ByteArray = encode(
        type = TYPE_JWT_AUTH,
        data = token.toByteArray(Charsets.UTF_8),
        ssid = 0,
    )

    fun heartbeat(latitude: Double = 0.0, longitude: Double = 0.0, altitude: Double = 0.0): ByteArray {
        val payload = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN)
            .putDouble(latitude)
            .putDouble(longitude)
            .putDouble(altitude)
            .array()
        return encode(type = TYPE_HEARTBEAT, data = payload)
    }

    fun text(message: String): ByteArray = encode(
        type = TYPE_TEXT,
        data = message.toByteArray(Charsets.UTF_8),
    )

    fun voice(mergedOpusFrames: ByteArray): ByteArray = encode(
        type = TYPE_OPUS_16K,
        data = mergedOpusFrames,
    )

    fun mergeOpusFrames(frames: List<ByteArray>): ByteArray {
        require(frames.isNotEmpty())
        val total = frames.sumOf { it.size + 2 }
        require(total + HEADER_SIZE <= MAX_PACKET_SIZE) { "Merged Opus payload is too large" }
        return ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN).apply {
            frames.forEach { frame ->
                require(frame.isNotEmpty() && frame.size <= 1_000)
                putShort(frame.size.toShort())
                put(frame)
            }
        }.array()
    }

    fun splitOpusFrames(data: ByteArray): List<ByteArray> {
        if (data.isEmpty()) return emptyList()
        val frames = mutableListOf<ByteArray>()
        var offset = 0
        while (offset + 2 <= data.size) {
            val length = ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)
            if (length == 0 || length > 1_000 || offset + 2 + length > data.size) {
                return if (offset == 0) listOf(data) else frames
            }
            frames += data.copyOfRange(offset + 2, offset + 2 + length)
            offset += 2 + length
        }
        return frames.ifEmpty { listOf(data) }
    }

    fun authError(status: Int, detail: String = ""): String {
        val message = when (status) {
            1 -> "登录凭证无效或已过期"
            2 -> "用户不存在"
            3 -> "账号已被禁用"
            4 -> "账号尚未审核通过"
            5 -> "客户端型号不受支持"
            6 -> "当前账号已有 Android 客户端在线"
            else -> "UDP 认证失败 ($status)"
        }
        return if (detail.isBlank()) message else "$message：$detail"
    }

    private fun putFixed(
        target: ByteArray,
        offset: Int,
        maxBytes: Int,
        value: String,
        charset: java.nio.charset.Charset,
    ) {
        val bytes = value.toByteArray(charset)
        bytes.copyInto(target, offset, endIndex = minOf(bytes.size, maxBytes))
    }

    private fun readFixed(
        source: ByteArray,
        offset: Int,
        length: Int,
        charset: java.nio.charset.Charset,
    ): String {
        var end = offset + length
        while (end > offset && source[end - 1] == 0.toByte()) end--
        return source.copyOfRange(offset, end).toString(charset)
    }
}

data class DraarlPacket(
    val username: String,
    val devicePassword: String,
    val type: Int,
    val deviceModel: Int,
    val ssid: Int,
    val dmrId: Int,
    val callsign: String,
    val data: ByteArray,
)
