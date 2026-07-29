package cn.silverdragon.draarl.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun followSystemUsesCurrentSystemMode() {
        assertFalse(AppThemeMode.FOLLOW_SYSTEM.isDark(false))
        assertTrue(AppThemeMode.FOLLOW_SYSTEM.isDark(true))
    }

    @Test
    fun explicitModesIgnoreSystemMode() {
        assertFalse(AppThemeMode.LIGHT.isDark(true))
        assertTrue(AppThemeMode.DARK.isDark(false))
    }
}
