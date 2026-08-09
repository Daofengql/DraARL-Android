package cn.silverdragon.draarl.radio

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

internal interface UdpTransport {
    val localPort: Int
    val isClosed: Boolean
    var receiveTimeoutMillis: Int

    fun send(payload: ByteArray)
    fun receive(buffer: ByteArray): Int?
    fun close()
}

internal fun interface UdpTransportFactory {
    fun open(host: String, port: Int, preferredLocalPort: Int, receiveTimeoutMillis: Int): UdpTransport
}

internal class DatagramUdpTransportFactory : UdpTransportFactory {
    override fun open(host: String, port: Int, preferredLocalPort: Int, receiveTimeoutMillis: Int): UdpTransport {
        val socket = createSocket(preferredLocalPort)
        return runCatching {
            socket.soTimeout = receiveTimeoutMillis
            socket.connect(InetSocketAddress(host, port))
            DatagramUdpTransport(socket)
        }.getOrElse { error ->
            socket.close()
            throw error
        }
    }

    private fun createSocket(preferredLocalPort: Int): DatagramSocket {
        val reusable = preferredLocalPort.takeIf { it > 0 }?.let(::bindReusableSocket)
        return reusable ?: DatagramSocket()
    }

    private fun bindReusableSocket(port: Int): DatagramSocket? {
        val socket = DatagramSocket(null)
        return runCatching {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port))
            socket
        }.getOrElse {
            socket.close()
            null
        }
    }
}

private class DatagramUdpTransport(private val socket: DatagramSocket) : UdpTransport {
    override val localPort: Int get() = socket.localPort
    override val isClosed: Boolean get() = socket.isClosed
    override var receiveTimeoutMillis: Int
        get() = socket.soTimeout
        set(value) {
            socket.soTimeout = value
        }

    override fun send(payload: ByteArray) {
        socket.send(DatagramPacket(payload, payload.size))
    }

    override fun receive(buffer: ByteArray): Int? = try {
        DatagramPacket(buffer, buffer.size).also(socket::receive).length
    } catch (_: SocketTimeoutException) {
        null
    }

    override fun close() = socket.close()
}
