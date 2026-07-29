package cn.silverdragon.draarl.aprs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class AprsIsClient {
    suspend fun sendPosition(config: AprsConfig, position: AprsPosition) = withContext(Dispatchers.IO) {
        require(config.server.isNotBlank()) { "APRS 服务器不能为空" }
        require(config.callsign.isNotBlank()) { "请先设置 APRS 呼号" }
        val password = config.passcode.trim().ifBlank { AprsPacketFormatter.passcode(config.callsign).toString() }
        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            socket.connect(InetSocketAddress(config.server.trim(), config.port.coerceIn(1, 65535)), SOCKET_TIMEOUT_MS)
            val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
            val writer = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
            writer.write("user ${config.callsign.substringBefore('-').uppercase()} pass $password vers DraARL 1.0\r\n")
            writer.flush()
            var verified = false
            val deadline = System.currentTimeMillis() + SOCKET_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val line = reader.readLine() ?: break
                if (line.startsWith("# logresp", ignoreCase = true)) {
                    verified = line.contains(Regex("\\bverified\\b", RegexOption.IGNORE_CASE)) &&
                        !line.contains("unverified", ignoreCase = true)
                    if (!verified) error("APRS-IS 认证失败")
                    break
                }
            }
            if (!verified) error("APRS-IS 未返回认证结果")
            writer.write(AprsPacketFormatter.positionPacket(config, position))
            writer.write("\r\n")
            writer.flush()
        }
    }

    private companion object {
        const val SOCKET_TIMEOUT_MS = 10_000
    }
}
