package cn.silverdragon.draarl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun `main navigation keeps ptt in the center`() {
        assertEquals(
            listOf(AppPage.DEVICES, AppPage.GROUPS, AppPage.RADIO, AppPage.TOOLS, AppPage.PROFILE),
            MAIN_NAVIGATION_PAGES,
        )
        assertEquals(AppPage.RADIO, MAIN_NAVIGATION_PAGES[MAIN_NAVIGATION_PAGES.size / 2])
    }

    @Test
    fun `unapproved users start on profile`() {
        assertEquals(AppPage.PROFILE, authenticatedStartPage(isApproved = false))
        assertEquals(AppPage.RADIO, authenticatedStartPage(isApproved = true))
    }

    @Test
    fun `page positions follow visible navigation order`() {
        assertEquals(MAIN_NAVIGATION_PAGES.indices.toList(), MAIN_NAVIGATION_PAGES.map(::pagePosition))
        assertTrue(pagePosition(AppPage.SETTINGS) > pagePosition(AppPage.PROFILE))
    }
}
