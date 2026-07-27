package cn.silverdragon.draarl.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileOverviewTest {
    @Test
    fun `duration remains compact as totals grow`() {
        assertEquals("0秒", formatCompactDuration(0))
        assertEquals("45秒", formatCompactDuration(45_000))
        assertEquals("12分", formatCompactDuration(12 * 60_000L))
        assertEquals("3时 8分", formatCompactDuration((3 * 60 + 8) * 60_000L))
        assertEquals("2天 5时", formatCompactDuration((2 * 24 + 5) * 3_600_000L))
    }
}
