package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.protocol.DraarlPacket
import cn.silverdragon.draarl.protocol.DraarlProtocol
import cn.silverdragon.draarl.protocol.GhostAuthSession

internal data class UdpAuthenticatedSession(val packet: DraarlPacket, val session: GhostAuthSession, val ssid: Int)

internal object UdpAuthentication {
    fun parse(packet: DraarlPacket, expectedClientInstanceId: String): UdpAuthenticatedSession {
        validateStatus(packet)
        val session = parseSession(packet)
        validateClientInstance(session, expectedClientInstanceId)
        return UdpAuthenticatedSession(
            packet = packet,
            session = session,
            ssid = packet.ssid.takeIf { it > 0 } ?: DraarlProtocol.SSID_ANDROID
        )
    }

    private fun validateStatus(packet: DraarlPacket) {
        if (packet.data.isEmpty()) throw UdpAuthenticationException("UDP 认证响应缺少状态码")
        val status = packet.data[0].toInt() and 0xff
        if (status != 0) {
            val detail = packet.data.copyOfRange(1, packet.data.size).toString(Charsets.UTF_8)
            throw UdpAuthenticationException(DraarlProtocol.authError(status, detail))
        }
    }

    private fun parseSession(packet: DraarlPacket): GhostAuthSession =
        runCatching { DraarlProtocol.parseGhostAuthSuccess(packet) }
            .getOrElse { throw UdpAuthenticationException("UDP 认证响应无效：${it.message.orEmpty()}") }

    private fun validateClientInstance(session: GhostAuthSession, expectedClientInstanceId: String) {
        if (!session.clientInstanceId.equals(expectedClientInstanceId, ignoreCase = true)) {
            throw UdpAuthenticationException("UDP 认证响应的客户端实例不匹配")
        }
    }
}

internal class UdpAuthenticationReceiver(
    private val timeoutMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun await(receive: () -> DraarlPacket?): DraarlPacket {
        val deadline = nowMillis() + timeoutMillis
        var response: DraarlPacket? = null
        while (response == null && nowMillis() < deadline) {
            response = receive()?.takeIf { it.type == DraarlProtocol.TYPE_JWT_AUTH }
        }
        return response ?: throw UdpAuthenticationTimeoutException("UDP 认证超时")
    }
}

internal class UdpAuthenticationException(message: String) : Exception(message)

internal class UdpAuthenticationTimeoutException(message: String) : Exception(message)
