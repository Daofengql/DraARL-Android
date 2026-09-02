package cn.silverdragon.draarl.maps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmateurRadioZonesTest {
    @Test
    fun `polygon detects points inside and outside its bounds`() {
        val polygon = ZonePolygon(
            zone = 24,
            coordinates = doubleArrayOf(
                110.0, 20.0,
                120.0, 20.0,
                120.0, 30.0,
                110.0, 30.0,
                110.0, 20.0
            ),
            south = 20.0,
            west = 110.0,
            north = 30.0,
            east = 120.0
        )

        assertTrue(polygon.contains(latitude = 23.1291, longitude = 113.2644))
        assertFalse(polygon.contains(latitude = 31.2304, longitude = 121.4737))
    }

    @Test
    fun `polygon handles concave boundaries`() {
        val polygon = ZonePolygon(
            zone = 44,
            coordinates = doubleArrayOf(
                0.0, 0.0,
                4.0, 0.0,
                2.0, 2.0,
                4.0, 4.0,
                0.0, 4.0,
                0.0, 0.0
            ),
            south = 0.0,
            west = 0.0,
            north = 4.0,
            east = 4.0
        )

        assertTrue(polygon.contains(latitude = 2.0, longitude = 1.0))
        assertFalse(polygon.contains(latitude = 2.0, longitude = 3.0))
    }

    @Test
    fun `zone lookup handles unwrapped longitude across date line`() {
        val polygon = ZonePolygon(
            zone = 1,
            coordinates = doubleArrayOf(
                -205.0, 50.0,
                -170.0, 50.0,
                -170.0, 70.0,
                -205.0, 70.0,
                -205.0, 50.0
            ),
            south = 50.0,
            west = -205.0,
            north = 70.0,
            east = -170.0
        )

        assertTrue(listOf(polygon).findZone(latitude = 60.0, longitude = 170.0) == 1)
    }
}
