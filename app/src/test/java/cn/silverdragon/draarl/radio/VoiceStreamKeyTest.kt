package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoiceStreamKeyTest {
    @Test
    fun `same sender on different source groups has isolated playback state`() {
        val first = VoiceStreamKey(1001, "BG7XYZ", 101)
        val second = VoiceStreamKey(1002, "BG7XYZ", 101)

        assertNotEquals(first, second)
        assertNotEquals(first.playbackKey, second.playbackKey)
    }

    @Test
    fun `sender identity is normalized for playback`() {
        val first = VoiceStreamKey(1001, "BG7XYZ", 101)
        val second = VoiceStreamKey(1001, " bg7xyz ", 101)

        assertEquals(first.playbackKey, second.playbackKey)
    }
}
