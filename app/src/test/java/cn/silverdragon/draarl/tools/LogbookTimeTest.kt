package cn.silverdragon.draarl.tools

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LogbookTimeTest {
    private lateinit var original: TimeZone

    @Before
    fun setUp() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(original)
    }

    @Test
    fun `converts between China local time and UTC`() {
        assertEquals("2026-07-27 04:30:00", LogbookTime.localToUtc("2026-07-27 12:30:00"))
        assertEquals("2026-07-27 12:30:00", LogbookTime.utcToLocal("2026-07-27 04:30:00"))
    }
}
