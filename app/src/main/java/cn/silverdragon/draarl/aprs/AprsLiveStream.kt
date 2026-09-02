@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package cn.silverdragon.draarl.aprs

import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class AprsLiveFilter(
    val latitude: Double,
    val longitude: Double,
    val radiusKilometers: Int
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "过滤中心纬度超出范围" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "过滤中心经度超出范围" }
        require(radiusKilometers in MIN_RADIUS_KILOMETERS..MAX_RADIUS_KILOMETERS) {
            "过滤范围需在 $MIN_RADIUS_KILOMETERS 到 $MAX_RADIUS_KILOMETERS km 之间"
        }
    }

    fun asAprsIsFilter(): String = String.format(
        Locale.US,
        "r/%.5f/%.5f/%d",
        latitude,
        longitude,
        ceil(radiusKilometers / KILOMETERS_PER_MILE).toInt().coerceAtLeast(1)
    )

    private companion object {
        const val MIN_RADIUS_KILOMETERS = 5
        const val MAX_RADIUS_KILOMETERS = 500
        const val KILOMETERS_PER_MILE = 1.609344
    }
}

data class AprsLivePacket(
    val source: String,
    val destination: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeFeet: Int?,
    val comment: String,
    val symbolTable: Char,
    val symbolCode: Char,
    val rawPacket: String,
    val receivedAtMillis: Long
) {
    val stationKey: String
        get() = source.uppercase(Locale.US)
}

fun parseAprsLivePacket(rawPacket: String, receivedAtMillis: Long = System.currentTimeMillis()): AprsLivePacket? =
    runCatching {
        val decoded = AprsPacketCodec.decode(rawPacket)
        AprsLivePacket(
            source = decoded.source,
            destination = decoded.destination,
            latitude = decoded.latitude,
            longitude = decoded.longitude,
            altitudeFeet = decoded.altitudeFeet,
            comment = decoded.comment,
            symbolTable = decoded.symbolTable,
            symbolCode = decoded.symbolCode,
            rawPacket = rawPacket,
            receivedAtMillis = receivedAtMillis
        )
    }.getOrNull()

internal fun interface AprsPacketStream {
    suspend fun stream(
        config: AprsConfig,
        filter: AprsLiveFilter,
        onPacket: suspend (String) -> Unit
    )
}

internal class AprsIsStreamClient(
    private val socketFactory: () -> Socket = ::Socket
) : AprsPacketStream {
    override suspend fun stream(
        config: AprsConfig,
        filter: AprsLiveFilter,
        onPacket: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        require(config.server.isNotBlank()) { "APRS 服务器不能为空" }
        val socket = socketFactory()
        val closeOnCancellation = currentCoroutineContext()[Job]?.invokeOnCompletion {
            runCatching { socket.close() }
        }
        try {
            socket.soTimeout = 0
            socket.connect(
                InetSocketAddress(config.server.trim(), config.port.coerceIn(1, 65_535)),
                CONNECT_TIMEOUT_MS
            )
            val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
            val writer = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
            val callsign = config.callsign.trim().ifBlank { "N0CALL" }
            writer.write(
                "user ${callsign.uppercase(Locale.US)} pass -1 vers DraARL 2.0 " +
                    "filter ${filter.asAprsIsFilter()}\r\n"
            )
            writer.flush()
            while (coroutineContext.isActive) {
                val line = reader.readLine() ?: break
                if (line.isNotBlank() && !line.startsWith("#")) {
                    onPacket(line)
                }
            }
        } finally {
            closeOnCancellation?.dispose()
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
    }
}
