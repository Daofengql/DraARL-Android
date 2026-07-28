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
        assertTrue(AppPage.RADIO_PRESETS in APPROVAL_REQUIRED_PAGES)
    }

    @Test
    fun `page positions follow visible navigation order`() {
        assertEquals(MAIN_NAVIGATION_PAGES.indices.toList(), MAIN_NAVIGATION_PAGES.map(::pagePosition))
        assertTrue(pagePosition(AppPage.EDIT_PROFILE) > pagePosition(AppPage.PROFILE))
        assertTrue(pagePosition(AppPage.RADIO_PRESETS) > pagePosition(AppPage.PROFILE))
        assertTrue(pagePosition(AppPage.SETTINGS) > pagePosition(AppPage.PROFILE))
        assertTrue(pagePosition(AppPage.SYSTEM_SETTINGS) > pagePosition(AppPage.SETTINGS))
        assertTrue(pagePosition(AppPage.LOCATION_MAP) > pagePosition(AppPage.ACCOUNT_SECURITY))
    }
}
