package cn.silverdragon.draarl.maps

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoCoordinate(val latitude: Double, val longitude: Double)

object CoordinateConverter {
    fun wgs84ToGcj02(coordinate: GeoCoordinate): GeoCoordinate {
        if (outsideChina(coordinate)) return coordinate
        val delta = offset(coordinate.latitude, coordinate.longitude)
        return GeoCoordinate(coordinate.latitude + delta.latitude, coordinate.longitude + delta.longitude)
    }

    fun gcj02ToWgs84(coordinate: GeoCoordinate): GeoCoordinate {
        if (outsideChina(coordinate)) return coordinate
        var latitude = coordinate.latitude
        var longitude = coordinate.longitude
        repeat(12) {
            val converted = wgs84ToGcj02(GeoCoordinate(latitude, longitude))
            latitude -= converted.latitude - coordinate.latitude
            longitude -= converted.longitude - coordinate.longitude
        }
        return GeoCoordinate(latitude, longitude)
    }

    private fun offset(latitude: Double, longitude: Double): GeoCoordinate {
        val x = longitude - 105.0
        val y = latitude - 35.0
        var latitudeOffset = transformLatitude(x, y)
        var longitudeOffset = transformLongitude(x, y)
        val radians = latitude / 180.0 * PI
        var magic = sin(radians)
        magic = 1 - ECCENTRICITY * magic * magic
        val sqrtMagic = sqrt(magic)
        latitudeOffset = latitudeOffset * 180.0 / ((EARTH_RADIUS * (1 - ECCENTRICITY)) / (magic * sqrtMagic) * PI)
        longitudeOffset = longitudeOffset * 180.0 / (EARTH_RADIUS / sqrtMagic * kotlin.math.cos(radians) * PI)
        return GeoCoordinate(latitudeOffset, longitudeOffset)
    }

    private fun transformLatitude(x: Double, y: Double): Double =
        -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x)) +
            (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0 +
            (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0 +
            (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0

    private fun transformLongitude(x: Double, y: Double): Double =
        300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x)) +
            (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0 +
            (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0 +
            (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0

    private fun outsideChina(coordinate: GeoCoordinate): Boolean =
        coordinate.longitude !in 72.004..137.8347 || coordinate.latitude !in 0.8293..55.8271

    private const val EARTH_RADIUS = 6378245.0
    private const val ECCENTRICITY = 0.006693421622965943
}
