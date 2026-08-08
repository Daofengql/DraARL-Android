package cn.silverdragon.draarl.aprs

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Immutable
data class AprsUiState(
    val config: AprsConfig = AprsConfig(),
    val status: AprsStatus = AprsStatus(),
    val saving: Boolean = false
) {
    val sending: Boolean
        get() = status.state == AprsConnectionState.CONNECTING || status.state == AprsConnectionState.SENDING
}

sealed interface AprsEvent {
    data class SaveConfig(val config: AprsConfig) : AprsEvent
    data class SendPosition(val position: AprsPosition) : AprsEvent
}

internal interface AprsConfigStorage {
    fun load(userId: Int): AprsConfig
    fun save(userId: Int, config: AprsConfig)
}

internal fun interface AprsPositionSender {
    suspend fun sendPosition(config: AprsConfig, position: AprsPosition)
}

internal interface AprsEffects {
    fun startBackgroundReporting(userId: Int)
    fun stopBackgroundReporting()
    fun showNotice(message: String)
    fun friendlyError(error: Throwable): String
    fun currentTimeMillis(): Long
}

class AprsController internal constructor(
    private val storage: AprsConfigStorage,
    private val sender: AprsPositionSender,
    private val effects: AprsEffects,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val storageMutex = Mutex()
    private var userId: Int? = null
    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var sendJob: Job? = null
    private var saveGeneration = 0
    private var closed = false

    var uiState by mutableStateOf(AprsUiState())
        private set

    fun onEvent(event: AprsEvent) {
        when (event) {
            is AprsEvent.SaveConfig -> saveConfig(event.config)
            is AprsEvent.SendPosition -> sendPosition(event.position)
        }
    }

    fun onUserChanged(newUserId: Int?) {
        if (closed) return
        val previousUserId = userId
        userId = newUserId
        saveGeneration++
        loadJob?.cancel()
        saveJob?.cancel()
        sendJob?.cancel()
        uiState = AprsUiState()

        if (newUserId == null) {
            effects.stopBackgroundReporting()
            return
        }
        if (previousUserId != null && previousUserId != newUserId) {
            effects.stopBackgroundReporting()
        }
        loadJob = scope.launch {
            runCatching {
                withContext(ioDispatcher) { storageMutex.withLock { storage.load(newUserId) } }
            }.onSuccess { config ->
                if (!closed && userId == newUserId) uiState = uiState.copy(config = config)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!closed && userId == newUserId) {
                    effects.showNotice("无法加载 APRS 设置：${effects.friendlyError(error)}")
                }
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        userId = null
        saveGeneration++
        loadJob?.cancel()
        saveJob?.cancel()
        sendJob?.cancel()
        effects.stopBackgroundReporting()
        uiState = AprsUiState()
    }

    private fun saveConfig(config: AprsConfig) {
        if (closed) return
        val normalized = normalizeAprsConfig(config)
        val targetUserId = userId
        val requestGeneration = ++saveGeneration
        saveJob?.cancel()
        uiState = uiState.copy(config = normalized, saving = true)
        saveJob = scope.launch {
            val result = runCatching { persistAndSync(normalized, targetUserId) }
            val error = result.exceptionOrNull()
            if (error is CancellationException) throw error
            if (!isCurrentSave(requestGeneration, targetUserId)) return@launch
            uiState = uiState.copy(saving = false)
            effects.showNotice(error?.let(::saveErrorMessage) ?: "APRS 设置已保存")
        }
    }

    private fun sendPosition(position: AprsPosition) {
        val config = uiState.config
        val rejection = when {
            !config.enabled -> "请先在设置中启用 APRS"
            sendJob?.isActive == true -> "APRS 位置正在发送"
            else -> null
        }
        if (closed || rejection != null) {
            rejection?.let(effects::showNotice)
            return
        }
        uiState = uiState.copy(status = AprsStatus(AprsConnectionState.CONNECTING, "正在连接 APRS-IS"))
        sendJob = scope.launch {
            uiState = uiState.copy(status = AprsStatus(AprsConnectionState.SENDING, "正在发送 APRS 位置"))
            publishSendResult(runCatching { sender.sendPosition(config, position) })
        }
    }

    private suspend fun persistAndSync(config: AprsConfig, targetUserId: Int?) {
        if (targetUserId != null) {
            withContext(ioDispatcher) {
                storageMutex.withLock { storage.save(targetUserId, config) }
            }
        }
        syncBackgroundReporting(config, targetUserId)
    }

    private fun publishSendResult(result: Result<Unit>) {
        val error = result.exceptionOrNull()
        if (error is CancellationException) throw error
        if (closed) return
        if (error == null) {
            uiState = uiState.copy(
                status = AprsStatus(
                    state = AprsConnectionState.SENT,
                    message = "APRS 位置已发送",
                    lastSentAt = effects.currentTimeMillis()
                )
            )
        } else {
            val message = error.message ?: "APRS 发送失败"
            uiState = uiState.copy(status = AprsStatus(AprsConnectionState.ERROR, message))
            effects.showNotice(message)
        }
    }

    private fun syncBackgroundReporting(config: AprsConfig, targetUserId: Int?) {
        if (config.enabled && config.autoReport && targetUserId != null) {
            runCatching {
                effects.startBackgroundReporting(targetUserId)
            }.getOrElse { error -> throw AprsBackgroundStartException(error) }
        } else {
            effects.stopBackgroundReporting()
        }
    }

    private fun saveErrorMessage(error: Throwable): String = if (error is AprsBackgroundStartException) {
        "无法启动 APRS 后台上报：${effects.friendlyError(error.cause ?: error)}"
    } else {
        "APRS 设置保存失败：${effects.friendlyError(error)}"
    }

    private fun isCurrentSave(generation: Int, targetUserId: Int?): Boolean =
        !closed && generation == saveGeneration && targetUserId == userId
}

internal fun normalizeAprsConfig(config: AprsConfig): AprsConfig = config.copy(
    server = config.server.trim().ifBlank { DEFAULT_APRS_SERVER },
    port = config.port.coerceIn(MIN_APRS_PORT, MAX_APRS_PORT),
    callsign = config.callsign.trim().uppercase(),
    movingIntervalSeconds = config.movingIntervalSeconds.coerceIn(
        MIN_REPORT_INTERVAL_SECONDS,
        MAX_MOVING_INTERVAL_SECONDS
    ),
    stationaryIntervalSeconds = config.stationaryIntervalSeconds.coerceIn(
        MIN_REPORT_INTERVAL_SECONDS,
        MAX_STATIONARY_INTERVAL_SECONDS
    )
)

private const val DEFAULT_APRS_SERVER = "rotate.aprs2.net"
private const val MIN_APRS_PORT = 1
private const val MAX_APRS_PORT = 65_535
private const val MIN_REPORT_INTERVAL_SECONDS = 60
private const val MAX_MOVING_INTERVAL_SECONDS = 600
private const val MAX_STATIONARY_INTERVAL_SECONDS = 3_600

private class AprsBackgroundStartException(cause: Throwable) : RuntimeException(cause)
