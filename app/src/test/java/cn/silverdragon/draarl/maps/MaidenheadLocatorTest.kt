package cn.silverdragon.draarl.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MaidenheadLocatorTest {
    @Test
    fun `encodes known coordinates at six character precision`() {
        assertEquals("IO91WM", MaidenheadLocator.encode(51.5074, -0.1278))
        assertEquals("OL63PD", MaidenheadLocator.encode(23.1291, 113.2644))
    }

    @Test
    fun `decoded cell contains original coordinate`() {
        val latitude = 22.5431
        val longitude = 114.0579
        val locator = MaidenheadLocator.encode(latitude, longitude)
        val cell = MaidenheadLocator.decode(locator.lowercase())

        assertTrue(latitude >= cell.south && latitude < cell.north)
        assertTrue(longitude >= cell.west && longitude < cell.east)
        assertEquals(locator, cell.locator)
    }

    @Test
    fun `supports field square subsquare and extended square precision`() {
        listOf(1, 2, 3, 4).forEach { pairs ->
            val locator = MaidenheadLocator.encode(31.2304, 121.4737, pairs)
            val cell = MaidenheadLocator.decode(locator)
            assertEquals(pairs * 2, locator.length)
            assertTrue(cell.center.latitude in cell.south..cell.north)
            assertTrue(cell.center.longitude in cell.west..cell.east)
        }
    }

    @Test
    fun `rejects letters outside first field range`() {
        assertThrows(IllegalArgumentException::class.java) { MaidenheadLocator.decode("SS00") }
    }
}
