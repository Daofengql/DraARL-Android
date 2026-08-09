package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioStatus
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpSessionStateContextTest {
    @Test
    fun `authentication transition commits connection status and identity together`() {
        val published = mutableListOf<RadioStatus>()
        val context = UdpSessionStateContext(published::add)
        val attempt = requireNotNull(context.connect(CONFIG))
        assertTrue(
            context.transition(
                UdpConnectionEvent.AuthenticationStarted(attempt.generation),
                attempt.generation
            ) { it.copy(phase = RadioConnectionPhase.AUTHENTICATING) }
        )

        assertTrue(
            context.transition(
                event = UdpConnectionEvent.Authenticated(attempt.generation),
                expectedGeneration = attempt.generation,
                authenticatedIdentity = IDENTITY
            ) { it.copy(phase = RadioConnectionPhase.CONNECTED, sessionId = "session-1") }
        )

        val snapshot = context.snapshot()
        assertEquals(UdpConnectionStage.ONLINE, snapshot.connection.stage)
        assertEquals(RadioConnectionPhase.CONNECTED, snapshot.status.phase)
        assertEquals("session-1", snapshot.status.sessionId)
        assertEquals(IDENTITY, snapshot.identity)
        assertEquals(
            listOf(RadioConnectionPhase.AUTHENTICATING, RadioConnectionPhase.CONNECTED),
            published.map(RadioStatus::phase)
        )
    }

    @Test
    fun `disconnect rejects stale generation update and clears identity`() {
        val context = UdpSessionStateContext {}
        val attempt = requireNotNull(context.connect(CONFIG))
        context.transition(
            UdpConnectionEvent.AuthenticationStarted(attempt.generation),
            attempt.generation
        ) { it.copy(phase = RadioConnectionPhase.AUTHENTICATING) }
        context.transition(
            UdpConnectionEvent.Authenticated(attempt.generation),
            attempt.generation,
            IDENTITY
        ) { it.copy(phase = RadioConnectionPhase.CONNECTED) }

        context.dispatch(UdpConnectionEvent.Disconnect)
        val updated = context.updateStatusIfActive(attempt.generation) { it.copy(error = "stale") }

        assertFalse(updated)
        assertEquals("", context.snapshot().status.error)
        assertEquals(UdpSessionIdentity(), context.snapshot().identity)
    }

    @Test
    fun `blocked listener does not hold state lock and queued notifications stay ordered`() {
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondReturned = CountDownLatch(1)
        val published = CopyOnWriteArrayList<String>()
        val context = UdpSessionStateContext { status ->
            published += status.error
            if (status.error == "first") {
                firstEntered.countDown()
                assertTrue(releaseFirst.await(2, TimeUnit.SECONDS))
            }
        }
        val firstThread = Thread({ context.updateStatus { it.copy(error = "first") } }, "state-first")
        firstThread.start()
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val secondThread = Thread({
            context.updateStatus { it.copy(error = "second") }
            secondReturned.countDown()
        }, "state-second")
        secondThread.start()
        val returnedWhileListenerBlocked = secondReturned.await(1, TimeUnit.SECONDS)
        releaseFirst.countDown()
        firstThread.join(2_000L)
        secondThread.join(2_000L)

        assertTrue(returnedWhileListenerBlocked)
        assertEquals("second", context.snapshot().status.error)
        assertEquals(listOf("first", "second"), published)
    }

    @Test
    fun `listener failure releases publisher for later status`() {
        var calls = 0
        val context = UdpSessionStateContext { status ->
            calls++
            if (status.error == "first") error("listener failure")
        }

        assertThrows(IllegalStateException::class.java) {
            context.updateStatus { it.copy(error = "first") }
        }
        context.updateStatus { it.copy(error = "second") }

        assertEquals(2, calls)
        assertEquals("second", context.snapshot().status.error)
    }

    private companion object {
        val CONFIG = RadioConnectionConfig(
            accessPoint = AccessPoint("edge", "edge", "127.0.0.1", 60_050),
            accessToken = "token",
            clientInstanceId = "client-instance",
            groupId = 7
        )
        val IDENTITY = UdpSessionIdentity(sessionTag = 42L, username = "operator", ssid = 101)
    }
}
