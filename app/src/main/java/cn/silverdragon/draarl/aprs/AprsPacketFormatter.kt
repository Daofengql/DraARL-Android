package cn.silverdragon.draarl.aprs

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object AprsPacketFormatter {
    fun passcode(callsign: String): Int {
        val base = callsign.substringBefore('-').trim().uppercase(Locale.US)
        require(base.isNotBlank()) { "APRS 呼号不能为空" }
        var hash = 0x73e2
        for (index in base.indices step 2) {
            hash = hash xor (base[index].code shl 8)
            if (index + 1 < base.length) hash = hash xor base[index + 1].code
        }
        return hash and 0x7fff
    }

    fun positionPacket(config: AprsConfig, position: AprsPosition): String {
        require(position.latitude in -90.0..90.0) { "纬度超出范围" }
        require(position.longitude in -180.0..180.0) { "经度超出范围" }
        val call = config.callsign.trim().uppercase(Locale.US).ifBlank { error("APRS 呼号不能为空") }
        val latitude = formatLatitude(position.latitude)
        val longitude = formatLongitude(position.longitude)
        val altitude = position.altitudeMeters
            ?.takeIf { it.isFinite() }
            ?.let { "/A=${formatAltitude(it * 3.28084)}" }
            .orEmpty()
        val comment = config.comment.replace(Regex("[\\r\\n|]"), " ").trim().take(43)
        return "$call>APDRA1,TCPIP*:!$latitude${config.symbolTable}$longitude${config.symbolCode}$altitude${if (comment.isBlank()) "" else " $comment"}"
    }

    private fun formatLatitude(value: Double): String {
        val direction = if (value < 0) 'S' else 'N'
        val absolute = abs(value)
        val coordinate = roundedCoordinate(absolute, 90)
        val degrees = coordinate.first
        val minutes = coordinate.second
        return String.format(Locale.US, "%02d%05.2f%c", degrees, minutes, direction)
    }

    private fun formatLongitude(value: Double): String {
        val direction = if (value < 0) 'W' else 'E'
        val absolute = abs(value)
        val coordinate = roundedCoordinate(absolute, 180)
        val degrees = coordinate.first
        val minutes = coordinate.second
        return String.format(Locale.US, "%03d%05.2f%c", degrees, minutes, direction)
    }

    private fun roundedCoordinate(absolute: Double, maximumDegrees: Int): Pair<Int, Double> {
        var degrees = absolute.toInt().coerceAtMost(maximumDegrees)
        var minutes = (((absolute - degrees) * MINUTES_PER_DEGREE) * MINUTE_HUNDREDTHS).roundToInt() /
            MINUTE_HUNDREDTHS
        if (minutes >= MINUTES_PER_DEGREE) {
            if (degrees < maximumDegrees) {
                degrees++
                minutes = ZERO_MINUTES
            } else {
                minutes = MAX_MINUTES_AT_BOUNDARY
            }
        }
        return degrees to minutes
    }

    private fun formatAltitude(feet: Double): String {
        val rounded = feet.roundToInt()
        return if (rounded < 0) {
            "-${(-rounded).coerceAtMost(MAX_NEGATIVE_ALTITUDE_FEET).toString().padStart(NEGATIVE_ALTITUDE_WIDTH, '0')}"
        } else {
            rounded.coerceAtMost(MAX_POSITIVE_ALTITUDE_FEET).toString().padStart(POSITIVE_ALTITUDE_WIDTH, '0')
        }
    }

    private const val MINUTES_PER_DEGREE = 60.0
    private const val MINUTE_HUNDREDTHS = 100.0
    private const val ZERO_MINUTES = 0.0
    private const val MAX_MINUTES_AT_BOUNDARY = 59.99
    private const val MAX_NEGATIVE_ALTITUDE_FEET = 99_999
    private const val MAX_POSITIVE_ALTITUDE_FEET = 999_999
    private const val NEGATIVE_ALTITUDE_WIDTH = 5
    private const val POSITIVE_ALTITUDE_WIDTH = 6
}
