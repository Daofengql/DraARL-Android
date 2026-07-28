package cn.silverdragon.draarl.data

import org.json.JSONArray

enum class LocationMessageKind(val wireValue: String) {
    CURRENT("current"),
    PINNED("point"),
}

data class Wgs84LocationMessage(
    val kind: LocationMessageKind,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
)

fun encodeLocationMessage(location: Wgs84LocationMessage): String {
    require(location.latitude in -90.0..90.0) { "Latitude is out of range" }
    require(location.longitude in -180.0..180.0) { "Longitude is out of range" }
    val tuple = JSONArray()
        .put(location.kind.wireValue)
        .put(LOCATION_DATUM)
        .put(location.latitude)
        .put(location.longitude)
        .put(location.altitudeMeters ?: org.json.JSONObject.NULL)
    return LOCATION_MESSAGE_PREFIX + tuple.toString()
}

fun decodeLocationMessage(content: String): Wgs84LocationMessage? = runCatching {
    if (!content.startsWith(LOCATION_MESSAGE_PREFIX)) return null
    val tuple = JSONArray(content.removePrefix(LOCATION_MESSAGE_PREFIX))
    if (tuple.length() != LOCATION_TUPLE_SIZE || tuple.optString(1) != LOCATION_DATUM) return null
    val kind = LocationMessageKind.entries.firstOrNull { it.wireValue == tuple.optString(0) } ?: return null
    val latitude = tuple.getDouble(2)
    val longitude = tuple.getDouble(3)
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    Wgs84LocationMessage(
        kind = kind,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (tuple.isNull(4)) null else tuple.getDouble(4),
    )
}.getOrNull()

const val LOCATION_DATUM = "WGS-84"
private const val LOCATION_MESSAGE_PREFIX = "DRAARL_LOCATION:"
private const val LOCATION_TUPLE_SIZE = 5
