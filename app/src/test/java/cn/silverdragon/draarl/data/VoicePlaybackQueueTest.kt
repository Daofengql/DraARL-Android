package cn.silverdragon.draarl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlaybackQueueTest {
    @Test
    fun `selects the oldest playable unheard incoming voice`() {
        val messages = listOf(
            voice("mine", mine = true),
            voice("played", played = true),
            voice("unavailable", playable = false),
            voice("first"),
            voice("second"),
        )

        assertEquals("first", VoicePlaybackQueue.nextUnplayed(messages)?.id)
        assertEquals("second", VoicePlaybackQueue.nextUnplayed(messages, setOf("first"))?.id)
    }

    @Test
    fun `does not treat text or outgoing voice as unheard audio`() {
        val text = voice("text").copy(type = RadioMessageType.TEXT)
        val mine = voice("mine", mine = true)

        assertFalse(VoicePlaybackQueue.isUnplayed(text))
        assertFalse(VoicePlaybackQueue.isUnplayed(mine))
        assertNull(VoicePlaybackQueue.nextUnplayed(listOf(text, mine)))
    }

    @Test
    fun `server record without a current url can still be queued`() {
        val message = voice("server", playable = false).copy(serverRecordId = 12)

        assertTrue(VoicePlaybackQueue.isPlayable(message))
        assertTrue(VoicePlaybackQueue.isUnplayed(message))
    }

    private fun voice(
        id: String,
        mine: Boolean = false,
        played: Boolean = false,
        playable: Boolean = true,
    ) = RadioMessage(
        id = id,
        type = RadioMessageType.VOICE,
        senderCallsign = "BG7XYZ",
        senderSsid = 101,
        content = "语音",
        timestamp = 1_000L,
        mine = mine,
        audioUrl = if (playable) "https://example.test/$id.raw" else "",
        played = played,
    )
}
