package cn.silverdragon.draarl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelMessageMapperTest {
    @Test
    fun `maps structured sender and real source group`() {
        val mapped = ChannelMessageMapper.toRadioMessage(
            message = channelMessage(sourceGroupId = 1002),
            accountUser = User(id = 9, username = "other"),
            timestamp = 1234L,
        )

        assertEquals("BG7XYZ", mapped.senderCallsign)
        assertEquals("operator", mapped.senderUsername)
        assertEquals(101, mapped.senderSsid)
        assertEquals(1002, mapped.groupId)
        assertFalse(mapped.mine)
    }

    @Test
    fun `marks this accounts android ghost history as mine`() {
        val mapped = ChannelMessageMapper.toRadioMessage(
            message = channelMessage(sourceGroupId = 1001),
            accountUser = User(id = 9, username = "operator"),
            timestamp = 1234L,
        )

        assertTrue(mapped.mine)
        assertTrue(mapped.played)
    }

    private fun channelMessage(sourceGroupId: Int) = ChannelMessage(
        id = 42,
        messageType = "text",
        sourceGroupId = sourceGroupId,
        sourceGroupName = "频道",
        requestedGroupId = 1001,
        sender = ChannelMessageSender(
            userId = 9,
            username = "operator",
            callsign = "BG7XYZ",
            nickname = "Operator",
            ssid = 101,
            model = 101,
            ghost = true,
        ),
        sentAt = "2026-08-05T12:00:00Z",
        endTime = "",
        durationMs = 0,
        text = "测试消息",
        audioUrl = "",
        status = 2,
    )
}
