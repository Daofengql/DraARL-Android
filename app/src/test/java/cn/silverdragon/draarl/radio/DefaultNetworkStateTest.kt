package cn.silverdragon.draarl.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNetworkStateTest {
    @Test
    fun `first callback establishes missing startup baseline without reporting a switch`() {
        val state = DefaultNetworkState<String>(initialNetwork = null)

        assertFalse(state.onAvailable("wifi"))
        assertFalse(state.isUnavailable())
    }

    @Test
    fun `same startup network is not reported as a switch`() {
        val state = DefaultNetworkState(initialNetwork = "wifi")

        assertFalse(state.onAvailable("wifi"))
    }

    @Test
    fun `replacement default network is reported after baseline`() {
        val state = DefaultNetworkState(initialNetwork = "wifi")

        assertTrue(state.onAvailable("cellular"))
    }

    @Test
    fun `loss and recovery on a replacement network are reported`() {
        val state = DefaultNetworkState(initialNetwork = "wifi")

        assertTrue(state.onLost("wifi"))
        assertTrue(state.isUnavailable())
        assertTrue(state.onAvailable("cellular"))
    }

    @Test
    fun `brief loss and recovery of the same network are not reported as a switch`() {
        val state = DefaultNetworkState(initialNetwork = "wifi")

        assertTrue(state.onLost("wifi"))
        assertFalse(state.onAvailable("wifi"))
        assertFalse(state.isUnavailable())
    }

    @Test
    fun `loss from a stale network is ignored`() {
        val state = DefaultNetworkState(initialNetwork = "cellular")

        assertFalse(state.onLost("wifi"))
        assertFalse(state.isUnavailable())
    }
}
