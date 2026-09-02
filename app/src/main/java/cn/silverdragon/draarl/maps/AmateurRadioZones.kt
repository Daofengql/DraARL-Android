package cn.silverdragon.draarl.maps

import android.content.Context
import cn.silverdragon.draarl.R
import java.util.zip.GZIPInputStream
import org.json.JSONArray
import org.json.JSONObject

data class AmateurRadioZone(val cq: Int, val itu: Int)

class AmateurRadioZoneResolver(context: Context) {
    private val appContext = context.applicationContext

    fun resolve(latitude: Double, longitude: Double): AmateurRadioZone? {
        require(latitude in MIN_LATITUDE..MAX_LATITUDE && longitude in MIN_LONGITUDE..MAX_LONGITUDE) {
            "经纬度超出有效范围"
        }
        val cq = cqZones.findZone(latitude, longitude)
        val itu = ituZones.findZone(latitude, longitude)
        return if (cq == null || itu == null) null else AmateurRadioZone(cq = cq, itu = itu)
    }

    private val cqZones: List<ZonePolygon> by lazy {
        loadZones(R.raw.cqzones_json, "cq_zone_number")
    }
    private val ituZones: List<ZonePolygon> by lazy {
        loadZones(R.raw.ituzones_json, "itu_zone_number")
    }

    private fun loadZones(resourceId: Int, propertyName: String): List<ZonePolygon> {
        val features = loadFeatures(resourceId)
        return buildList(features.length()) {
            repeat(features.length()) { featureIndex ->
                val feature = features.getJSONObject(featureIndex)
                val zone = feature.getJSONObject("properties").getInt(propertyName)
                val rings = feature.getJSONObject("geometry").getJSONArray("coordinates")
                repeat(rings.length()) { ringIndex ->
                    add(ZonePolygon.fromJson(zone, rings.getJSONArray(ringIndex)))
                }
            }
        }
    }

    private fun loadFeatures(resourceId: Int): JSONArray {
        val json = appContext.resources.openRawResource(resourceId).use { input ->
            GZIPInputStream(input).bufferedReader().use { it.readText() }
        }
        return JSONObject(json).getJSONArray("features")
    }

    private companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
}

internal data class ZonePolygon(
    val zone: Int,
    val coordinates: DoubleArray,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    fun contains(latitude: Double, longitude: Double): Boolean {
        if (latitude !in south..north || longitude !in west..east) return false
        var inside = false
        var previousIndex = coordinates.lastIndex - 1
        var currentIndex = 0
        while (currentIndex < coordinates.size) {
            val currentLongitude = coordinates[currentIndex]
            val currentLatitude = coordinates[currentIndex + 1]
            val previousLongitude = coordinates[previousIndex]
            val previousLatitude = coordinates[previousIndex + 1]
            val crossesLatitude = (currentLatitude > latitude) != (previousLatitude > latitude)
            if (crossesLatitude) {
                val boundaryLongitude = (previousLongitude - currentLongitude) *
                    (latitude - currentLatitude) / (previousLatitude - currentLatitude) + currentLongitude
                if (longitude < boundaryLongitude) inside = !inside
            }
            previousIndex = currentIndex
            currentIndex += COORDINATE_COMPONENTS
        }
        return inside
    }

    companion object {
        fun fromJson(zone: Int, coordinates: JSONArray): ZonePolygon {
            val values = DoubleArray(coordinates.length() * COORDINATE_COMPONENTS)
            var south = Double.POSITIVE_INFINITY
            var west = Double.POSITIVE_INFINITY
            var north = Double.NEGATIVE_INFINITY
            var east = Double.NEGATIVE_INFINITY
            repeat(coordinates.length()) { pointIndex ->
                val point = coordinates.getJSONArray(pointIndex)
                val longitude = point.getDouble(0)
                val latitude = point.getDouble(1)
                val valueIndex = pointIndex * COORDINATE_COMPONENTS
                values[valueIndex] = longitude
                values[valueIndex + 1] = latitude
                south = minOf(south, latitude)
                west = minOf(west, longitude)
                north = maxOf(north, latitude)
                east = maxOf(east, longitude)
            }
            return ZonePolygon(
                zone = zone,
                coordinates = values,
                south = south,
                west = west,
                north = north,
                east = east
            )
        }

        private const val COORDINATE_COMPONENTS = 2
    }
}

internal fun List<ZonePolygon>.findZone(latitude: Double, longitude: Double): Int? = firstOrNull { polygon ->
    polygon.contains(latitude, longitude) ||
        polygon.contains(latitude, longitude - FULL_LONGITUDE_SPAN) ||
        polygon.contains(latitude, longitude + FULL_LONGITUDE_SPAN)
}?.zone

private const val FULL_LONGITUDE_SPAN = 360.0
