package cn.silverdragon.draarl.maps

import android.content.Context
import android.location.Location
import androidx.core.content.edit

data class CachedMapLocation(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val timestampMillis: Long,
)

class LastMapLocationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): CachedMapLocation? {
        if (!preferences.contains(KEY_LATITUDE) || !preferences.contains(KEY_LONGITUDE)) return null
        val latitude = Double.fromBits(preferences.getLong(KEY_LATITUDE, 0L))
        val longitude = Double.fromBits(preferences.getLong(KEY_LONGITUDE, 0L))
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return CachedMapLocation(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = preferences.getLong(KEY_ALTITUDE, NO_ALTITUDE_BITS)
                .takeUnless { it == NO_ALTITUDE_BITS }
                ?.let(Double::fromBits),
            timestampMillis = preferences.getLong(KEY_TIMESTAMP, 0L),
        )
    }

    fun save(location: Location) {
        preferences.edit {
            putLong(KEY_LATITUDE, location.latitude.toBits())
            putLong(KEY_LONGITUDE, location.longitude.toBits())
            putLong(KEY_ALTITUDE, if (location.hasAltitude()) location.altitude.toBits() else NO_ALTITUDE_BITS)
            putLong(KEY_TIMESTAMP, location.time.takeIf { it > 0L } ?: System.currentTimeMillis())
        }
    }

    private companion object {
        const val PREFERENCES = "draarl_map_location"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_ALTITUDE = "altitude"
        const val KEY_TIMESTAMP = "timestamp"
        const val NO_ALTITUDE_BITS = Long.MIN_VALUE
    }
}
