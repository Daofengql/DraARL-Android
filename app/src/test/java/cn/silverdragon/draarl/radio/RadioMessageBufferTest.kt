package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioMessageBufferTest {
    @Test
    fun keepsFifoOrderAndDropsOldestWhenFull() {
        val buffer = RadioMessageBuffer(capacity = 2)

        buffer.offer(message("one"))
        buffer.offer(message("two"))
        buffer.offer(message("three"))

        assertEquals(listOf("two", "three"), buffer.drain().map(RadioMessage::id))
        assertTrue(buffer.drain().isEmpty())
    }

    @Test
    fun clearDiscardsMessagesBeforeTheNextBinding() {
        val buffer = RadioMessageBuffer(capacity = 2)
        buffer.offer(message("stale"))

        buffer.clear()

        assertTrue(buffer.drain().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveCapacity() {
        RadioMessageBuffer(capacity = 0)
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
