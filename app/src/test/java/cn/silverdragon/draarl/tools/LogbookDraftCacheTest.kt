package cn.silverdragon.draarl.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LogbookDraftCacheTest {
    @Test
    fun `draft cache keys are isolated per user`() {
        assertNotEquals(logbookDraftCacheKey(7), logbookDraftCacheKey(8))
    }

    @Test
    fun `draft json round trip preserves all editable fields`() {
        val draft = LogbookDraft(
            editingId = 42,
            myCallsign = "BG7MINE",
            localTime = "2026-07-27 20:00:00",
            txFrequency = "439.500",
            rxFrequency = "438.500",
            cqZone = "24",
            ituZone = "44",
            callsign = "BG7THEIR",
            theirPower = "10",
            theirQth = "Guangzhou",
            theirRadio = "HT",
            theirAntenna = "Whip",
            myPower = "25",
            myQth = "Shenzhen",
            myRadio = "Mobile",
            myAntenna = "Vertical",
            notes = "test",
        )

        assertEquals(draft, LogbookDraftJson.decode(LogbookDraftJson.encode(draft)))
    }

    @Test
    fun `relay cache round trip preserves fields needed for offline display`() {
        val relays = listOf(
            RelayStation(
                id = 7,
                name = "广州中继",
                uplinkFrequency = "439.500",
                downlinkFrequency = "438.500",
                transmitTone = "88.5",
                receiveTone = "88.5",
                ownerCallsign = "BG7TEST",
                location = "广东省 广州市",
                status = 1,
                note = "测试",
            ),
        )

        assertEquals(relays, RelayCacheJson.decode(RelayCacheJson.encode(relays)))
    }
}
