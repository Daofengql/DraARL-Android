package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.AccessPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpConnectionStateMachineTest {
    @Test
    fun `connect authentication and online form an ordered session`() {
        val machine = UdpConnectionStateMachine()

        val attempt = requireNotNull(machine.connect(config()))

        assertEquals(UdpConnectionStage.CONNECTING, machine.snapshot().stage)
        assertTrue(machine.dispatch(UdpConnectionEvent.AuthenticationStarted(attempt.generation)))
        assertEquals(UdpConnectionStage.AUTHENTICATING, machine.snapshot().stage)
        assertTrue(machine.dispatch(UdpConnectionEvent.Authenticated(attempt.generation)))
        assertEquals(UdpConnectionStage.ONLINE, machine.snapshot().stage)
        assertTrue(machine.isActive(attempt.generation))
    }

    @Test
    fun `out of order and stale connection events are rejected`() {
        val machine = UdpConnectionStateMachine()
        val first = requireNotNull(machine.connect(config("first")))

        assertFalse(machine.dispatch(UdpConnectionEvent.Authenticated(first.generation)))
        machine.dispatch(UdpConnectionEvent.Disconnect)
        val second = requireNotNull(machine.connect(config("second")))

        assertFalse(machine.dispatch(UdpConnectionEvent.AuthenticationStarted(first.generation)))
        assertEquals(UdpConnectionStage.CONNECTING, machine.snapshot().stage)
        assertEquals(second.generation, machine.snapshot().generation)
    }

    @Test
    fun `duplicate connect does not replace an active attempt`() {
        val machine = UdpConnectionStateMachine()
        val config = config()
        val first = requireNotNull(machine.connect(config))

        assertNull(machine.connect(config))

        assertEquals(first.generation, machine.generation())
    }

    @Test
    fun `only one reconnect can be scheduled for a generation`() {
        val machine = onlineMachine()
        val onlineGeneration = machine.generation()

        val reconnectGeneration = requireNotNull(machine.scheduleReconnect(onlineGeneration))

        assertNull(machine.scheduleReconnect(onlineGeneration))
        assertNull(machine.scheduleReconnect(reconnectGeneration))
        assertTrue(machine.isWaitingToReconnect(reconnectGeneration))
    }

    @Test
    fun `reconnect claims the latest token and routing`() {
        val machine = onlineMachine()
        val reconnectGeneration = requireNotNull(machine.scheduleReconnect(machine.generation()))
        machine.dispatch(UdpConnectionEvent.AccessTokenChanged("fresh-token"))
        machine.dispatch(UdpConnectionEvent.RoutingChanged(42))

        val retry = requireNotNull(machine.startReconnect(reconnectGeneration))

        assertTrue(retry.reconnecting)
        assertEquals("fresh-token", retry.config.accessToken)
        assertEquals(42, retry.config.groupId)
        assertEquals(UdpConnectionStage.RECONNECTING, machine.snapshot().stage)
    }

    @Test
    fun `reconnect scheduling failure moves the session to error`() {
        val machine = onlineMachine()
        val reconnectGeneration = requireNotNull(machine.scheduleReconnect(machine.generation()))

        assertTrue(machine.dispatch(UdpConnectionEvent.ReconnectFailed(reconnectGeneration)))

        assertEquals(UdpConnectionStage.ERROR, machine.snapshot().stage)
        assertFalse(machine.isWaitingToReconnect(reconnectGeneration))
    }

    @Test
    fun `authentication failure is terminal until an explicit connect`() {
        val machine = UdpConnectionStateMachine()
        val attempt = requireNotNull(machine.connect(config()))
        assertTrue(machine.dispatch(UdpConnectionEvent.AuthenticationStarted(attempt.generation)))

        assertTrue(machine.dispatch(UdpConnectionEvent.AuthenticationFailed(attempt.generation)))
        assertEquals(UdpConnectionStage.ERROR, machine.snapshot().stage)

        val restarted = requireNotNull(machine.connect(config()))
        assertTrue(restarted.generation > attempt.generation)
        assertEquals(UdpConnectionStage.CONNECTING, machine.snapshot().stage)
    }

    @Test
    fun `disconnect invalidates pending work and clears desired config`() {
        val machine = onlineMachine()
        val activeGeneration = machine.generation()

        machine.dispatch(UdpConnectionEvent.Disconnect)

        assertFalse(machine.isActive(activeGeneration))
        assertEquals(UdpConnectionStage.DISCONNECTED, machine.snapshot().stage)
        assertNull(machine.snapshot().config)
    }

    @Test
    fun `close is terminal and cannot be reopened`() {
        val machine = UdpConnectionStateMachine()
        machine.connect(config())

        machine.dispatch(UdpConnectionEvent.Close)

        assertEquals(UdpConnectionStage.CLOSED, machine.snapshot().stage)
        assertNull(machine.connect(config("late")))
        machine.dispatch(UdpConnectionEvent.Disconnect)
        assertEquals(UdpConnectionStage.CLOSED, machine.snapshot().stage)
    }

    private fun onlineMachine(): UdpConnectionStateMachine = UdpConnectionStateMachine().apply {
        val attempt = requireNotNull(connect(config()))
        check(dispatch(UdpConnectionEvent.AuthenticationStarted(attempt.generation)))
        check(dispatch(UdpConnectionEvent.Authenticated(attempt.generation)))
    }

    private fun config(id: String = "edge") = RadioConnectionConfig(
        accessPoint = AccessPoint(
            id = id,
            displayName = id,
            host = "$id.example.com",
            port = 60_050
        ),
        accessToken = "token",
        clientInstanceId = "client-instance",
        groupId = 7
    )
}
