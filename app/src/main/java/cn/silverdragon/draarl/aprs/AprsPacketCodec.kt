@file:Suppress("MagicNumber")

package cn.silverdragon.draarl.aprs

data class AprsDecodedPacket(
    val source: String,
    val destination: String,
    val latitude: Double,
    val longitude: Double,
    val symbolTable: Char,
    val symbolCode: Char,
    val altitudeFeet: Int?,
    val comment: String,
    val packetType: Char = '!'
)

object AprsPacketCodec {
    fun encode(
        callsign: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double?,
        comment: String
    ): String = AprsPacketFormatter.positionPacket(
        AprsConfig(callsign = callsign, comment = comment),
        AprsPosition(latitude, longitude, altitudeMeters)
    )

    fun decode(packet: String): AprsDecodedPacket {
        val normalized = packet.trim()
        val header = HEADER_REGEX.find(normalized) ?: throw IllegalArgumentException("未识别 APRS 头部")
        val body = BODY_REGEX.find(normalized) ?: throw IllegalArgumentException("未识别 APRS 位置字段")
        val latitude = coordinate(body.groupValues[1], body.groupValues[2])
        val longitude = coordinate(body.groupValues[4], body.groupValues[5])
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "APRS 坐标超出范围" }
        val altitude = ALTITUDE_REGEX.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        val bodyEnd = body.range.last + 1
        val commentStart = ALTITUDE_REGEX.find(normalized)?.range?.last?.plus(1)
            ?: bodyEnd
        val comment = normalized.substring(commentStart).trim().removePrefix(" ")
        return AprsDecodedPacket(
            source = header.groupValues[1],
            destination = header.groupValues[2],
            latitude = latitude,
            longitude = longitude,
            symbolTable = body.groupValues[3].single(),
            symbolCode = body.groupValues[6].single(),
            altitudeFeet = altitude,
            comment = comment,
            packetType = body.value.first()
        )
    }

    private fun coordinate(value: String, direction: String): Double {
        val degreesLength = if (direction == "N" || direction == "S") 2 else 3
        val degrees = value.substring(0, degreesLength).toDouble()
        val minutes = value.substring(degreesLength).toDouble()
        require(minutes in 0.0..<60.0) { "APRS 分钟字段超出范围" }
        val result = degrees + minutes / MINUTES_PER_DEGREE
        return if (direction == "S" || direction == "W") -result else result
    }

    private val HEADER_REGEX = Regex("""^([A-Za-z0-9-]+)>([^:,\s]+)""")
    private val BODY_REGEX = Regex(
        """[!=/@:](\d{2}\d{2}\.\d{2})([NS])(.)(\d{3}\d{2}\.\d{2})([EW])(.)"""
    )
    private val ALTITUDE_REGEX = Regex("""/A=(-?\d{5,6})(?:\s|$)""")
    private const val MINUTES_PER_DEGREE = 60.0
}
