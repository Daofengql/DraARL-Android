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
            ?.let { "/A=${(it * 3.28084).roundToInt().coerceIn(-999_999, 9_999_999).toString().padStart(6, '0')}" }
            .orEmpty()
        val comment = config.comment.replace(Regex("[\\r\\n|]"), " ").trim().take(43)
        return "$call>APDRA1,TCPIP*:!$latitude${config.symbolTable}$longitude${config.symbolCode}$altitude${if (comment.isBlank()) "" else " $comment"}"
    }

    private fun formatLatitude(value: Double): String {
        val direction = if (value < 0) 'S' else 'N'
        val absolute = abs(value)
        val degrees = absolute.toInt().coerceAtMost(90)
        val minutes = (absolute - degrees) * 60.0
        return String.format(Locale.US, "%02d%05.2f%c", degrees, minutes, direction)
    }

    private fun formatLongitude(value: Double): String {
        val direction = if (value < 0) 'W' else 'E'
        val absolute = abs(value)
        val degrees = absolute.toInt().coerceAtMost(180)
        val minutes = (absolute - degrees) * 60.0
        return String.format(Locale.US, "%03d%05.2f%c", degrees, minutes, direction)
    }
}
