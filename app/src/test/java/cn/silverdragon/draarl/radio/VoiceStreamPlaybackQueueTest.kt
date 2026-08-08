package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStreamPlaybackQueueTest {
    @Test
    fun `keeps one active stream and advances pending streams in arrival order`() {
        val first = VoiceStreamKey(1001, "A", 101)
        val second = VoiceStreamKey(1002, "B", 101)
        val third = VoiceStreamKey(1003, "C", 101)
        val queue = VoiceStreamPlaybackQueue()

        assertTrue(queue.onStream(first))
        assertFalse(queue.onStream(second))
        assertFalse(queue.onStream(third))
        assertEquals(null, queue.advance())
        queue.remove(first)
        assertEquals(second, queue.advance())
        assertEquals(null, queue.advance())
        queue.remove(second)
        assertEquals(third, queue.advance())
        assertEquals(null, queue.advance())
    }

    @Test
    fun `does not enqueue duplicate packets from one pending stream`() {
        val first = VoiceStreamKey(1001, "A", 101)
        val second = VoiceStreamKey(1002, "B", 101)
        val queue = VoiceStreamPlaybackQueue()

        queue.onStream(first)
        queue.onStream(second)
        queue.onStream(second)
        queue.remove(first)
        assertEquals(second, queue.advance())
        queue.remove(second)
        assertEquals(null, queue.advance())
    }
}
