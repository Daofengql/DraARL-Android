package cn.silverdragon.draarl.tools

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class LogbookDraftMapperTest {
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `maps all optional station fields and converts local time to utc`() {
        val entry = LogbookDraft(
            myCallsign = "bg7mine",
            callsign = "bg7their",
            localTime = "2026-07-27 20:00:00",
            txFrequency = "439.500",
            rxFrequency = "438.500",
            cqZone = "24",
            ituZone = "44",
            theirPower = "10",
            theirQth = "Guangzhou",
            theirRadio = "HT",
            theirAntenna = "Whip",
            myPower = "25",
        ).toLogbookEntry()

        assertEquals("BG7MINE", entry.myCallsign)
        assertEquals("BG7THEIR", entry.callsign)
        assertEquals("2026-07-27 12:00:00", entry.timeUtc)
        assertEquals(24, entry.cqZone)
        assertEquals(44, entry.ituZone)
        assertEquals(10, entry.theirPower)
        assertEquals("HT", entry.theirRadio)
    }

    @Test
    fun `rejects invalid optional numeric values instead of silently dropping them`() {
        assertThrows(IllegalArgumentException::class.java) {
            LogbookDraft(
                myCallsign = "BG7MINE",
                callsign = "BG7THEIR",
                localTime = "2026-07-27 20:00:00",
                txFrequency = "439.500",
                theirPower = "ten",
            ).toLogbookEntry()
        }
    }
}
