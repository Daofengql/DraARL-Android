package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioMessageDispatcherTest {
    @Test
    fun queuesBeforeBindingAndDrainsInOrderOnBinding() {
        val dispatcher = RadioMessageDispatcher(capacity = 3)
        val first = message("first")
        val second = message("second")
        val listener = RecordingListener()

        assertNull(dispatcher.dispatch(first))
        assertNull(dispatcher.dispatch(second))
        assertEquals(listOf(first, second), dispatcher.setListener(listener))
        assertSame(listener, dispatcher.listener)
    }

    @Test
    fun dispatchReturnsCurrentListenerAfterBinding() {
        val dispatcher = RadioMessageDispatcher(capacity = 2)
        val listener = RecordingListener()
        val message = message("live")

        dispatcher.setListener(listener)

        assertSame(listener, dispatcher.dispatch(message))
    }

    @Test
    fun unbindsAndClearsQueuedMessagesOnClose() {
        val dispatcher = RadioMessageDispatcher(capacity = 2)
        val listener = RecordingListener()

        dispatcher.setListener(listener)
        dispatcher.setListener(null)
        dispatcher.dispatch(message("stale"))
        dispatcher.clear()

        assertNull(dispatcher.listener)
        assertTrue(dispatcher.setListener(listener).isEmpty())
    }

    private fun message(id: String) = RadioMessage(
        id = id,
        type = RadioMessageType.TEXT,
        senderCallsign = "BG7TEST",
        senderSsid = 1,
        content = id,
        timestamp = 1L,
        mine = false
    )
}

private class RecordingListener : RadioServiceListener {
    override fun onRadioStatus(status: cn.silverdragon.draarl.data.RadioStatus) = Unit
    override fun onRadioMessage(message: RadioMessage) = Unit
    override fun onPlaybackState(messageId: String?) = Unit
    override fun onPlaybackLevel(level: Float) = Unit
    override fun onTransmitLevel(level: Float) = Unit
    override fun onCwPreviewState(active: Boolean) = Unit
}
