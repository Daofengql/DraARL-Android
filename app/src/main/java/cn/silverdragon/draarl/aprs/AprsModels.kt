package cn.silverdragon.draarl.aprs

data class AprsConfig(
    val enabled: Boolean = false,
    val server: String = "rotate.aprs2.net",
    val port: Int = 14580,
    val callsign: String = "",
    val passcode: String = "",
    val comment: String = "DraARL",
    val symbolTable: Char = '/',
    val symbolCode: Char = '>',
    val autoReport: Boolean = false,
    val movingIntervalSeconds: Int = 120,
    val stationaryIntervalSeconds: Int = 600,
)

data class AprsPosition(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)

enum class AprsConnectionState {
    IDLE,
    CONNECTING,
    VERIFIED,
    SENDING,
    SENT,
    ERROR,
}

data class AprsStatus(
    val state: AprsConnectionState = AprsConnectionState.IDLE,
    val message: String = "",
    val lastSentAt: Long? = null,
)
