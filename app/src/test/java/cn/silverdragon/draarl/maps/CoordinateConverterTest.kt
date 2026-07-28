package cn.silverdragon.draarl.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateConverterTest {
    @Test
    fun `gcj and wgs conversion round trips inside china`() {
        val wgs84 = GeoCoordinate(39.908823, 116.39747)
        val gcj02 = CoordinateConverter.wgs84ToGcj02(wgs84)
        val restored = CoordinateConverter.gcj02ToWgs84(gcj02)
        assertEquals(wgs84.latitude, restored.latitude, 0.000001)
        assertEquals(wgs84.longitude, restored.longitude, 0.000001)
    }

    @Test
    fun `coordinates outside china are unchanged`() {
        val coordinate = GeoCoordinate(35.681236, 139.767125)
        assertEquals(coordinate, CoordinateConverter.wgs84ToGcj02(coordinate))
        assertEquals(coordinate, CoordinateConverter.gcj02ToWgs84(coordinate))
    }
}
