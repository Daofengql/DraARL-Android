package cn.silverdragon.draarl.radio

import java.net.DatagramPacket
import java.net.DatagramSocket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpTransportTest {
    @Test
    fun `transport sends and receives connected datagrams`() {
        DatagramSocket(0).use { server ->
            val transport = factory.open(
                LOOPBACK,
                server.localPort,
                preferredLocalPort = 0,
                receiveTimeoutMillis = 1_000
            )
            try {
                val request = "request".toByteArray()
                transport.send(request)

                val serverBuffer = ByteArray(64)
                val received = DatagramPacket(serverBuffer, serverBuffer.size).also(server::receive)
                assertArrayEquals(request, received.data.copyOf(received.length))

                val response = "response".toByteArray()
                server.send(DatagramPacket(response, response.size, received.socketAddress))
                val clientBuffer = ByteArray(64)
                val responseSize = requireNotNull(transport.receive(clientBuffer))

                assertArrayEquals(response, clientBuffer.copyOf(responseSize))
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun `receive timeout is reported without closing transport`() {
        DatagramSocket(0).use { server ->
            val transport = factory.open(LOOPBACK, server.localPort, preferredLocalPort = 0, receiveTimeoutMillis = 25)
            try {
                assertNull(transport.receive(ByteArray(16)))
                assertFalse(transport.isClosed)
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun `occupied preferred port falls back to an available port`() {
        DatagramSocket(0).use { blocker ->
            DatagramSocket(0).use { server ->
                val transport = factory.open(
                    host = LOOPBACK,
                    port = server.localPort,
                    preferredLocalPort = blocker.localPort,
                    receiveTimeoutMillis = 1_000
                )
                try {
                    assertNotEquals(blocker.localPort, transport.localPort)
                } finally {
                    transport.close()
                }
            }
        }
    }

    @Test
    fun `close is idempotent`() {
        DatagramSocket(0).use { server ->
            val transport = factory.open(
                LOOPBACK,
                server.localPort,
                preferredLocalPort = 0,
                receiveTimeoutMillis = 1_000
            )

            transport.close()
            transport.close()

            assertTrue(transport.isClosed)
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        val factory = DatagramUdpTransportFactory()
    }
}
