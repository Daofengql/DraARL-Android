package cn.silverdragon.draarl.tools.ble

import org.json.JSONObject
import java.util.UUID

object BleProvisionProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("6d22f67d-7287-4f4e-8548-b362f9b1f001")
    val STATUS_UUID: UUID = UUID.fromString("6d22f67d-7287-4f4e-8548-b362f9b1f002")
    val AUTH_UUID: UUID = UUID.fromString("6d22f67d-7287-4f4e-8548-b362f9b1f003")
    val RPC_TX_UUID: UUID = UUID.fromString("6d22f67d-7287-4f4e-8548-b362f9b1f004")
    val RPC_RX_UUID: UUID = UUID.fromString("6d22f67d-7287-4f4e-8548-b362f9b1f005")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun rpcChunks(id: Int, command: String, data: JSONObject = JSONObject()): List<ByteArray> {
        val text = JSONObject().put("id", id).put("cmd", command).put("data", data).toString()
        return chunkPayload(text)
    }

    internal fun chunkPayload(text: String): List<ByteArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return bytes.asList().chunked(CHUNK_PAYLOAD).mapIndexed { index, chunk ->
            ByteArray(chunk.size + 1).also { frame ->
                val isFirst = index == 0
                val isLast = index == (bytes.size - 1) / CHUNK_PAYLOAD
                frame[0] = ((if (isFirst) CHUNK_START else 0) or (if (isLast) CHUNK_END else 0)).toByte()
                chunk.forEachIndexed { chunkIndex, value -> frame[chunkIndex + 1] = value }
            }
        }
    }

    fun parseStatus(text: String, previous: BleProvisionStatus): BleProvisionStatus? {
        val normalized = text.replace("\u0000", "").trim()
        if (normalized.isBlank()) return null
        val values = normalized.split(';').mapNotNull { entry ->
            entry.takeIf { it.length >= 2 }?.let { it.first() to it.drop(1) }
        }.toMap()
        if (values.keys.none { it in setOf('w', 'b', 'a', 'r') }) return null
        return previous.copy(
            wifiState = values['w']?.let(WIFI_STATES::get) ?: previous.wifiState,
            bleState = values['b']?.let(BLE_STATES::get) ?: previous.bleState,
            authenticated = values['a']?.let { it == "1" } ?: previous.authenticated,
            rssi = values['r']?.toIntOrNull() ?: previous.rssi,
        )
    }

    class FrameAssembler {
        private val buffer = ArrayList<Byte>()

        @Synchronized
        fun append(frame: ByteArray): String? {
            if (frame.isEmpty()) return null
            val flags = frame[0].toInt() and 0xff
            if (flags and CHUNK_START != 0) buffer.clear()
            for (index in 1 until frame.size) buffer += frame[index]
            if (flags and CHUNK_END == 0) return null
            return buffer.toByteArray().toString(Charsets.UTF_8).also { buffer.clear() }
        }

        @Synchronized
        fun clear() = buffer.clear()
    }

    private const val CHUNK_PAYLOAD = 19
    private const val CHUNK_START = 0x01
    private const val CHUNK_END = 0x02
    private val WIFI_STATES = mapOf("0" to "Idle", "1" to "未配置", "2" to "连接中", "3" to "已连接", "4" to "连接失败")
    private val BLE_STATES = mapOf("0" to "已禁用", "1" to "广播中", "2" to "已连接")
}
