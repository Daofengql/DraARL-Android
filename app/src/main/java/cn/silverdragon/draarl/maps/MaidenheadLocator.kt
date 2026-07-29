package cn.silverdragon.draarl.maps

import kotlin.math.floor

data class MaidenheadCell(
    val locator: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val center: GeoCoordinate
        get() = GeoCoordinate(
            latitude = (south + north) / 2.0,
            longitude = (west + east) / 2.0,
        )
}

object MaidenheadLocator {
    fun encode(latitude: Double, longitude: Double, pairs: Int = 3): String {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "经纬度超出有效范围" }
        require(pairs in 1..4) { "网格精度应为 2、4、6 或 8 位" }

        var adjustedLongitude = longitude.coerceAtMost(180.0 - COORDINATE_EPSILON) + 180.0
        var adjustedLatitude = latitude.coerceAtMost(90.0 - COORDINATE_EPSILON) + 90.0
        val result = StringBuilder(pairs * 2)

        repeat(pairs) { index ->
            val longitudeStep = LONGITUDE_STEPS[index]
            val latitudeStep = LATITUDE_STEPS[index]
            val longitudeIndex = floor(adjustedLongitude / longitudeStep).toInt()
            val latitudeIndex = floor(adjustedLatitude / latitudeStep).toInt()
            if (index % 2 == 0) {
                result.append(('A'.code + longitudeIndex).toChar())
                result.append(('A'.code + latitudeIndex).toChar())
            } else {
                result.append(('0'.code + longitudeIndex).toChar())
                result.append(('0'.code + latitudeIndex).toChar())
            }
            adjustedLongitude -= longitudeIndex * longitudeStep
            adjustedLatitude -= latitudeIndex * latitudeStep
        }
        return result.toString()
    }

    fun decode(locator: String): MaidenheadCell {
        val normalized = locator.trim().uppercase()
        require(normalized.length in 2..8 && normalized.length % 2 == 0) { "请输入 2、4、6 或 8 位网格" }

        var west = -180.0
        var south = -90.0

        normalized.chunked(2).forEachIndexed { index, pair ->
            val longitudeStep = LONGITUDE_STEPS[index]
            val latitudeStep = LATITUDE_STEPS[index]
            val maxIndex = when {
                index == 0 -> 18
                index % 2 == 0 -> 24
                else -> 10
            }
            val firstIndex = pair[0].locatorIndex(index)
            val secondIndex = pair[1].locatorIndex(index)
            require(firstIndex in 0 until maxIndex && secondIndex in 0 until maxIndex) {
                "网格字符不符合梅登海德格式"
            }
            west += firstIndex * longitudeStep
            south += secondIndex * latitudeStep
        }

        val finalPairIndex = normalized.length / 2 - 1
        val longitudeStep = LONGITUDE_STEPS[finalPairIndex]
        val latitudeStep = LATITUDE_STEPS[finalPairIndex]

        return MaidenheadCell(
            locator = normalized,
            south = south,
            west = west,
            north = south + latitudeStep,
            east = west + longitudeStep,
        )
    }

    private fun Char.locatorIndex(pairIndex: Int): Int = if (pairIndex % 2 == 0) {
        code - 'A'.code
    } else {
        code - '0'.code
    }

    private val LONGITUDE_STEPS = doubleArrayOf(20.0, 2.0, 2.0 / 24.0, 2.0 / 240.0)
    private val LATITUDE_STEPS = doubleArrayOf(10.0, 1.0, 1.0 / 24.0, 1.0 / 240.0)
    private const val COORDINATE_EPSILON = 1e-12
}
