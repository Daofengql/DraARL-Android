package cn.silverdragon.draarl.maps

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.poisearch.PoiSearchV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AmapPlace(
    val id: String = "",
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
) {
    val stableKey: String
        get() = if (id.isNotBlank()) {
            "poi:$id"
        } else {
            "fallback:${name.length}:$name:${address.length}:$address:${latitude.toBits()}:${longitude.toBits()}"
        }
}

class AmapPlaceService(context: Context) {
    private val appContext = context.applicationContext

    suspend fun reverse(latitude: Double, longitude: Double): AmapPlace = withContext(Dispatchers.IO) {
        val result = GeocodeSearch(appContext).getFromLocation(
            RegeocodeQuery(
                LatLonPoint(latitude, longitude),
                REVERSE_GEOCODE_RADIUS_METERS,
                GeocodeSearch.AMAP
            ).apply { extensions = GeocodeSearch.EXTENSIONS_ALL }
        )
        val nearest = result.pois.orEmpty().minByOrNull { it.distance }
        AmapPlace(
            id = nearest?.poiId.orEmpty(),
            name = nearest?.displayName().orEmpty().ifBlank { result.formatAddress.orEmpty() },
            address = result.formatAddress.orEmpty(),
            latitude = latitude,
            longitude = longitude
        )
    }

    suspend fun search(keyword: String, latitude: Double?, longitude: Double?): List<AmapPlace> =
        withContext(Dispatchers.IO) {
            val normalized = keyword.trim()
            if (normalized.isBlank()) return@withContext emptyList()
            val query = PoiSearchV2.Query(normalized, "", "").apply {
                pageNum = 1
                pageSize = SEARCH_PAGE_SIZE
                setDistanceSort(true)
                if (latitude != null && longitude != null) {
                    location = LatLonPoint(latitude, longitude)
                }
            }
            PoiSearchV2(appContext, query).searchPOI().pois.orEmpty().mapNotNull { poi ->
                val point = poi.latLonPoint ?: return@mapNotNull null
                AmapPlace(
                    id = poi.poiId.orEmpty(),
                    name = poi.displayName(),
                    address = poi.snippet.orEmpty(),
                    latitude = point.latitude,
                    longitude = point.longitude
                )
            }.distinctBy(AmapPlace::stableKey)
        }

    private fun PoiItem.displayName(): String {
        val parts = listOf(provinceName, cityName, adName, title)
            .map(String?::orEmpty)
            .map(String::trim)
            .filter(String::isNotBlank)
            .fold(mutableListOf<String>()) { result, part ->
                if (result.lastOrNull() != part) result += part
                result
            }
        return parts.joinToString("").ifBlank { title.orEmpty().ifBlank { snippet.orEmpty() } }
    }

    private fun PoiItemV2.displayName(): String {
        val parts = listOf(provinceName, cityName, adName, title)
            .map(String?::orEmpty)
            .map(String::trim)
            .filter(String::isNotBlank)
            .fold(mutableListOf<String>()) { result, part ->
                if (result.lastOrNull() != part) result += part
                result
            }
        return parts.joinToString("").ifBlank { title.orEmpty().ifBlank { snippet.orEmpty() } }
    }

    private companion object {
        const val REVERSE_GEOCODE_RADIUS_METERS = 200f
        const val SEARCH_PAGE_SIZE = 20
    }
}
