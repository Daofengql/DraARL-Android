package cn.silverdragon.draarl.tools.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDeviceProfilesTest {
    @Test
    fun `profile keys and device models are unique`() {
        assertEquals(BleDeviceProfiles.all.size, BleDeviceProfiles.all.map(BleDeviceProfile::key).distinct().size)
        assertEquals(BleDeviceProfiles.all.size, BleDeviceProfiles.all.map(BleDeviceProfile::deviceModel).distinct().size)
    }

    @Test
    fun `unknown profile falls back to supported default`() {
        val fallback = BleDeviceProfiles.find("missing")

        assertEquals("devmodel1", fallback.key)
        assertTrue(fallback.supportsWifi)
        assertTrue(fallback.supportsDraarl)
    }
}
