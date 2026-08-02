package cn.silverdragon.draarl.maps

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object MapDistance {
    fun metersBetween(first: GeoCoordinate, second: GeoCoordinate): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2).let { it * it }
        val normalized = haversine.coerceIn(0.0, 1.0)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(normalized), sqrt(1 - normalized))
    }

    fun totalMeters(points: List<GeoCoordinate>): Double = points
        .zipWithNext(::metersBetween)
        .sum()

    fun format(meters: Double): String = if (meters < 1_000.0) {
        String.format(Locale.CHINA, "%.0f 米", meters)
    } else {
        String.format(Locale.CHINA, "%.2f 公里", meters / 1_000.0)
    }

    private const val EARTH_RADIUS_METERS = 6_371_008.8
}
