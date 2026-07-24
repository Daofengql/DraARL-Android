package cn.silverdragon.draarl.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioIdentityTest {
    @Test
    fun `formats callsign or username with ssid once`() {
        assertEquals("BG7ABC-101", formatRadioIdentity("BG7ABC", 101))
        assertEquals("admin-105", formatRadioIdentity("admin", 105))
    }

    @Test
    fun `falls back to a single ssid label`() {
        assertEquals("SSID-105", formatRadioIdentity("", 105))
        assertEquals("未知台站", formatRadioIdentity("", 0))
    }
}
