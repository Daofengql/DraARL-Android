package cn.silverdragon.draarl.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDistanceTest {
    @Test
    fun `distance between Guangzhou and Shenzhen is plausible`() {
        val meters = MapDistance.metersBetween(
            GeoCoordinate(23.1291, 113.2644),
            GeoCoordinate(22.5431, 114.0579),
        )

        assertTrue(meters in 100_000.0..110_000.0)
    }

    @Test
    fun `polyline distance sums adjacent segments`() {
        val first = GeoCoordinate(0.0, 0.0)
        val second = GeoCoordinate(0.0, 1.0)
        val third = GeoCoordinate(1.0, 1.0)

        assertEquals(
            MapDistance.metersBetween(first, second) + MapDistance.metersBetween(second, third),
            MapDistance.totalMeters(listOf(first, second, third)),
            0.001,
        )
    }
}
