package cn.silverdragon.draarl.radio

import android.content.Context
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.data.formatRadioIdentity
import cn.silverdragon.draarl.protocol.DraarlProtocol
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class RadioConnectionConfig(
    val accessPoint: AccessPoint,
    val accessToken: String,
    val groupId: Int,
)

interface UdpRadioListener {
    fun onStatus(status: RadioStatus)
    fun onMessage(message: RadioMessage)
    fun onPlaybackState(messageId: String?)
}

class UdpRadioClient(
    context: Context,
    private val listener: UdpRadioListener,
) {
    private val audioCache = RadioAudioCache(context.applicationContext.filesDir.resolve("radio_audio"))
    private val audioEngine = OpusAudioEngine(audioCache)
    private val connectionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "draarl-udp-connect")
    }
    private val scheduler = Executors.newScheduledThreadPool(2) { runnable ->
        Thread(runnable, "draarl-udp-scheduler")
    }
    private val generation = AtomicInteger(0)
    private val reconnectPending = AtomicBoolean(false)
    private val sendLock = Any()
    private val statusLock = Any()
    private val taskLock = Any()
    private val voiceSessionLock = Any()
    private val outgoingVoiceLock = Any()
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var desiredConfig: RadioConnectionConfig? = null
    @Volatile private var manualDisconnect = true
    @Volatile private var lastServerPacketAt = 0L
    @Volatile private var lastPacketSentAt = 0L
    @Volatile private var preferredLocalPort = 0
    @Volatile private var lastVoicePacketAt = 0L
    @Volatile private var lastSpeakerKey = ""
    @Volatile private var pttStartedAt = 0L
    @Volatile private var transmitTimeoutSeconds = DEFAULT_TRANSMIT_TIMEOUT_SECONDS
    @Volatile private var playingMessageId: String? = null
    @Volatile private var status = RadioStatus()
    private var incomingVoiceStartedAt = 0L
    private var incomingVoiceUsername = ""
    private var incomingVoiceCallsign = ""
    private var incomingVoiceSsid = 0
    private var incomingVoiceGroupId = 0
    private var incomingVoiceBuffer = ByteArrayOutputStream()
    private var outgoingVoiceBuffer = ByteArrayOutputStream()
    private var heartbeatTask: ScheduledFuture<*>? = null
    private var watchdogTask: ScheduledFuture<*>? = null
    private var reconnectTask: ScheduledFuture<*>? = null
    private var pttTimeoutTask: ScheduledFuture<*>? = null

    @Synchronized
    fun connect(config: RadioConnectionConfig) {
        if (
            !manualDisconnect &&
            desiredConfig == config &&
            status.phase in setOf(
                RadioConnectionPhase.CONNECTING,
                RadioConnectionPhase.AUTHENTICATING,
                RadioConnectionPhase.CONNECTED,
                RadioConnectionPhase.RECONNECTING,
            )
        ) return
        desiredConfig = config
        manualDisconnect = false
        cancelReconnectTask()
        reconnectPending.set(false)
        val currentGeneration = generation.incrementAndGet()
        stopTasks()
        audioEngine.stopCapture()
        stopPlayback()
        finishIncomingVoice()?.let(listener::onMessage)
        closeSocket()
        connectionExecutor.execute { establish(config, reconnecting = false, currentGeneration) }
    }

    fun updateAccessToken(token: String) {
        desiredConfig = desiredConfig?.copy(accessToken = token)
    }

    fun disconnect() {
        manualDisconnect = true
        desiredConfig = null
        cancelReconnectTask()
        reconnectPending.set(false)
        generation.incrementAndGet()
        stopTasks()
        audioEngine.stopCapture()
        stopPlayback()
        finishIncomingVoice()?.let(listener::onMessage)
        closeSocket()
        synchronized(statusLock) {
            updateStatus(RadioStatus(phase = RadioConnectionPhase.DISCONNECTED))
        }
    }

    fun sendText(text: String): Boolean {
        val normalized = text.trim()
        if (!status.connected || status.transmitting || status.speaker.isNotBlank() || normalized.isEmpty()) return false
        val payload = normalized.toByteArray(Charsets.UTF_8)
        if (payload.size > DraarlProtocol.MAX_PACKET_SIZE - DraarlProtocol.HEADER_SIZE) {
            reportNonFatal("消息过长，UTF-8 编码后不能超过 710 字节", generation.get())
            return false
        }
        return send(DraarlProtocol.text(normalized)).also { sent ->
            if (sent) {
                listener.onMessage(
                    RadioMessage(
                        id = UUID.randomUUID().toString(),
                        type = RadioMessageType.TEXT,
                        senderCallsign = status.callsign,
                        senderSsid = status.ssid,
                        content = normalized,
                        timestamp = System.currentTimeMillis(),
                        mine = true,
                        groupId = status.groupId,
                    ),
                )
            }
        }
    }

    @Synchronized
    fun startPtt(): Boolean {
        if (!status.connected || status.transmitting || status.speaker.isNotBlank()) return false
        stopPlayback()
        synchronized(outgoingVoiceLock) { outgoingVoiceBuffer = ByteArrayOutputStream() }
        val currentGeneration = generation.get()
        audioEngine.resetDecoder()
        val started = audioEngine.startCapture(
            onPacket = { opus ->
                if (status.transmitting && send(DraarlProtocol.voice(opus), currentGeneration)) {
                    synchronized(outgoingVoiceLock) { appendVoiceData(outgoingVoiceBuffer, opus) }
                }
            },
            onError = { message -> reportNonFatal(message, currentGeneration) },
        )
        if (!started) return false
        val statusUpdated = updateStatusIfActive(currentGeneration) { current ->
            if (!current.connected) current else current.copy(transmitting = true, speaker = "")
        }
        if (!statusUpdated || !status.transmitting) {
            audioEngine.stopCapture()
            return false
        }
        pttStartedAt = System.currentTimeMillis()
        schedulePttTimeout(currentGeneration)
        return true
    }

    @Synchronized
    fun stopPtt() {
        if (!status.transmitting) return
        cancelPttTimeout()
        val currentGeneration = generation.get()
        val statusUpdated = updateStatusIfActive(currentGeneration) { it.copy(transmitting = false) }
        audioEngine.stopCapture()
        if (!statusUpdated) return
        val duration = (System.currentTimeMillis() - pttStartedAt).coerceAtLeast(0L)
        if (duration > 100L) {
            val networkPayload = synchronized(outgoingVoiceLock) {
                outgoingVoiceBuffer.toByteArray().also { outgoingVoiceBuffer = ByteArrayOutputStream() }
            }
            val messageId = UUID.randomUUID().toString()
            listener.onMessage(
                RadioMessage(
                    id = messageId,
                    type = RadioMessageType.VOICE,
                    senderCallsign = status.callsign,
                    senderSsid = status.ssid,
                    content = "语音",
                    timestamp = pttStartedAt,
                    mine = true,
                    durationMs = duration,
                    audioCacheKey = cacheNetworkRecording(messageId, networkPayload),
                    groupId = status.groupId,
                ),
            )
        }
    }

    fun togglePlayback(message: RadioMessage): Boolean {
        if (message.type != RadioMessageType.VOICE) return false
        if (playingMessageId == message.id) {
            stopPlayback()
            return true
        }
        stopPlayback()
        playingMessageId = message.id
        listener.onPlaybackState(message.id)
        val started = audioEngine.playRecording(
            audioCacheKey = message.audioCacheKey,
            audioUrl = message.audioUrl,
            onFinished = { completePlayback(message.id) },
            onError = { error ->
                reportNonFatal(error, generation.get())
                completePlayback(message.id)
            },
        )
        if (!started) completePlayback(message.id)
        return started
    }

    fun stopPlayback() {
        audioEngine.stopRecordingPlayback()
        if (playingMessageId != null) {
            playingMessageId = null
            listener.onPlaybackState(null)
        }
    }

    fun setMuted(muted: Boolean) = audioEngine.setMuted(muted)

    @Synchronized
    fun setTransmitTimeoutSeconds(seconds: Int) {
        transmitTimeoutSeconds = seconds.coerceIn(MIN_TRANSMIT_TIMEOUT_SECONDS, MAX_TRANSMIT_TIMEOUT_SECONDS)
        if (status.transmitting) schedulePttTimeout(generation.get())
    }

    fun transmitTimeoutSeconds(): Int = transmitTimeoutSeconds

    fun audioCacheSizeBytes(): Long = audioCache.sizeBytes()

    fun clearAudioCache() {
        stopPlayback()
        audioCache.clear()
    }

    fun setGroup(groupId: Int) {
        desiredConfig = desiredConfig?.copy(groupId = groupId)
        updateStatus(status.copy(groupId = groupId))
    }

    fun snapshot(): RadioStatus = status

    fun release() {
        disconnect()
        audioEngine.release()
        connectionExecutor.shutdownNow()
        scheduler.shutdownNow()
    }

    private fun establish(config: RadioConnectionConfig, reconnecting: Boolean, currentGeneration: Int) {
        if (!isActive(currentGeneration)) return
        stopTasks()
        audioEngine.stopCapture()
        closeSocket()
        if (!isActive(currentGeneration)) return
        if (!updateStatusIfActive(currentGeneration) { current ->
            current.copy(
                phase = if (reconnecting) RadioConnectionPhase.RECONNECTING else RadioConnectionPhase.CONNECTING,
                endpoint = config.accessPoint.address,
                groupId = config.groupId,
                transmitting = false,
                speaker = "",
                error = "",
            )
        }) return
        try {
            val remote = InetSocketAddress(config.accessPoint.host, config.accessPoint.port)
            val newSocket = createSocket().apply {
                soTimeout = AUTH_SOCKET_TIMEOUT_MS
                connect(remote)
            }
            if (!isActive(currentGeneration)) {
                newSocket.close()
                return
            }
            socket = newSocket
            if (!isActive(currentGeneration)) {
                closeSocket(newSocket)
                return
            }
            if (!updateStatusIfActive(currentGeneration) { it.copy(phase = RadioConnectionPhase.AUTHENTICATING) }) {
                closeSocket(newSocket)
                return
            }
            sendRaw(newSocket, DraarlProtocol.jwtAuth(config.accessToken))
            val authResponse = awaitAuthResponse(newSocket)
            if (!isActive(currentGeneration)) {
                closeSocket(newSocket)
                return
            }
            if (authResponse.data.isEmpty()) throw RadioAuthException("UDP 认证响应缺少状态码")
            val authStatus = authResponse.data[0].toInt() and 0xff
            if (authStatus != 0) {
                val detail = authResponse.data.copyOfRange(1, authResponse.data.size).toString(Charsets.UTF_8)
                val message = DraarlProtocol.authError(authStatus, detail)
                if (authStatus == 6) throw RetryableRadioException(message)
                throw RadioAuthException(message)
            }
            newSocket.soTimeout = RECEIVE_SOCKET_TIMEOUT_MS
            lastServerPacketAt = System.currentTimeMillis()
            reconnectPending.set(false)
            if (!updateStatusIfActive(currentGeneration) { current ->
                current.copy(
                    phase = RadioConnectionPhase.CONNECTED,
                    callsign = authResponse.callsign,
                    ssid = authResponse.ssid.takeIf { it > 0 } ?: DraarlProtocol.SSID_ANDROID,
                    error = "",
                )
            }) {
                closeSocket(newSocket)
                return
            }
            startTasks(currentGeneration)
            if (!isActive(currentGeneration)) {
                stopTasks()
                closeSocket(newSocket)
                return
            }
            startReceiver(newSocket, currentGeneration)
        } catch (error: RadioAuthException) {
            closeSocket()
            if (isActive(currentGeneration)) {
                updateStatusIfActive(currentGeneration) {
                    it.copy(phase = RadioConnectionPhase.ERROR, error = error.message.orEmpty())
                }
            }
        } catch (error: RetryableRadioException) {
            closeSocket()
            scheduleReconnect("${error.message.orEmpty()}，将在安全间隔后自动重试", currentGeneration)
        } catch (error: Exception) {
            closeSocket()
            scheduleReconnect(error.message ?: "UDP 连接失败", currentGeneration)
        }
    }

    private fun awaitAuthResponse(activeSocket: DatagramSocket): cn.silverdragon.draarl.protocol.DraarlPacket {
        val deadline = System.currentTimeMillis() + AUTH_TOTAL_TIMEOUT_MS
        val buffer = ByteArray(4_096)
        while (System.currentTimeMillis() < deadline) {
            try {
                val datagram = DatagramPacket(buffer, buffer.size)
                activeSocket.receive(datagram)
                val decoded = DraarlProtocol.decode(datagram.data, datagram.length) ?: continue
                if (decoded.type == DraarlProtocol.TYPE_JWT_AUTH) return decoded
            } catch (_: SocketTimeoutException) {
                // Continue until the overall authentication deadline.
            }
        }
        throw IllegalStateException("UDP 认证超时")
    }

    private fun startReceiver(activeSocket: DatagramSocket, currentGeneration: Int) {
        Thread({ receiveLoop(activeSocket, currentGeneration) }, "draarl-udp-receiver").start()
    }

    private fun receiveLoop(activeSocket: DatagramSocket, currentGeneration: Int) {
        val buffer = ByteArray(4_096)
        while (!manualDisconnect && currentGeneration == generation.get() && !activeSocket.isClosed) {
            try {
                val datagram = DatagramPacket(buffer, buffer.size)
                activeSocket.receive(datagram)
                val packet = DraarlProtocol.decode(datagram.data, datagram.length) ?: continue
                if (!isActive(currentGeneration)) return
                lastServerPacketAt = System.currentTimeMillis()
                when (packet.type) {
                    DraarlProtocol.TYPE_TEXT -> handleText(
                        packet.username,
                        packet.callsign,
                        packet.ssid,
                        packet.data,
                    )
                    DraarlProtocol.TYPE_OPUS_16K -> handleVoice(
                        packet.username,
                        packet.callsign,
                        packet.ssid,
                        packet.data,
                        currentGeneration,
                    )
                }
            } catch (_: SocketTimeoutException) {
                // The watchdog owns timeout and reconnect decisions.
            } catch (error: Exception) {
                if (!manualDisconnect && currentGeneration == generation.get()) {
                    scheduleReconnect(error.message ?: "UDP 接收中断")
                }
                return
            }
        }
    }

    private fun handleText(username: String, callsign: String, ssid: Int, payload: ByteArray) {
        if (payload.isEmpty()) return
        listener.onMessage(
            RadioMessage(
                id = UUID.randomUUID().toString(),
                type = RadioMessageType.TEXT,
                senderCallsign = callsign.ifBlank { username },
                senderSsid = ssid,
                senderUsername = username,
                content = payload.toString(Charsets.UTF_8),
                timestamp = System.currentTimeMillis(),
                mine = false,
                groupId = status.groupId,
            ),
        )
    }

    private fun handleVoice(
        username: String,
        callsign: String,
        ssid: Int,
        payload: ByteArray,
        currentGeneration: Int,
    ) {
        if (payload.isEmpty() || status.transmitting) return
        val identity = callsign.ifBlank { username }
        val speaker = formatRadioIdentity(identity, ssid)
        val speakerKey = "$identity\u0000$ssid"
        val now = System.currentTimeMillis()
        var completedMessage: RadioMessage? = null
        var startingSession = false
        synchronized(voiceSessionLock) {
            if (lastSpeakerKey.isNotBlank() && (speakerKey != lastSpeakerKey || now - lastVoicePacketAt > VOICE_END_TIMEOUT_MS)) {
                completedMessage = finishIncomingVoiceLocked()
            }
            if (lastSpeakerKey.isBlank()) {
                startingSession = true
                incomingVoiceStartedAt = now
                incomingVoiceUsername = username
                incomingVoiceCallsign = callsign
                incomingVoiceSsid = ssid
                incomingVoiceGroupId = status.groupId
                incomingVoiceBuffer = ByteArrayOutputStream()
            }
            lastSpeakerKey = speakerKey
            lastVoicePacketAt = now
            appendVoiceData(incomingVoiceBuffer, payload)
        }
        completedMessage?.let(listener::onMessage)
        if (startingSession) {
            stopPlayback()
            audioEngine.resetDecoder()
        }
        updateStatusIfActive(currentGeneration) { it.copy(speaker = speaker) }
        audioEngine.play(payload) { message -> reportNonFatal(message, currentGeneration) }
    }

    private fun startTasks(currentGeneration: Int) {
        synchronized(taskLock) {
            if (!isActive(currentGeneration)) return
            heartbeatTask = scheduler.scheduleWithFixedDelay(
                {
                    if (!manualDisconnect && currentGeneration == generation.get() && status.connected) {
                        send(DraarlProtocol.heartbeat(), currentGeneration)
                    }
                },
                0,
                HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS,
            )
            watchdogTask = scheduler.scheduleWithFixedDelay(
                {
                    val now = System.currentTimeMillis()
                    if (lastSpeakerKey.isNotBlank() && now - lastVoicePacketAt > VOICE_END_TIMEOUT_MS) {
                        finishIncomingVoice()?.let(listener::onMessage)
                        updateStatusIfActive(currentGeneration) { it.copy(speaker = "") }
                    }
                    if (status.connected && now - lastServerPacketAt > SERVER_SILENCE_TIMEOUT_MS) {
                        scheduleReconnect("服务器心跳超时", currentGeneration)
                    }
                },
                WATCHDOG_INTERVAL_MS,
                WATCHDOG_INTERVAL_MS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun scheduleReconnect(reason: String, expectedGeneration: Int = generation.get()) {
        if (!isActive(expectedGeneration) || !reconnectPending.compareAndSet(false, true)) return
        val reconnectGeneration = generation.incrementAndGet()
        stopTasks()
        audioEngine.stopCapture()
        finishIncomingVoice()?.let(listener::onMessage)
        closeSocket()
        if (!updateStatusIfActive(reconnectGeneration) {
            it.copy(phase = RadioConnectionPhase.RECONNECTING, error = reason, transmitting = false)
        }) {
            reconnectPending.set(false)
            return
        }
        val retryDelay = RadioReconnectPolicy.retryDelayMillis(
            lastPacketSentAt = lastPacketSentAt,
            now = System.currentTimeMillis(),
        )
        val scheduled = runCatching {
            scheduler.schedule(
                {
                    if (!isActive(reconnectGeneration)) {
                        return@schedule
                    }
                    if (!reconnectPending.compareAndSet(true, false)) return@schedule
                    synchronized(taskLock) { reconnectTask = null }
                    val config = desiredConfig
                    if (!manualDisconnect && config != null) {
                        connectionExecutor.execute { establish(config, reconnecting = true, reconnectGeneration) }
                    }
                },
                retryDelay,
                TimeUnit.MILLISECONDS,
            )
        }.getOrElse { error ->
            reconnectPending.set(false)
            updateStatusIfActive(reconnectGeneration) {
                it.copy(phase = RadioConnectionPhase.ERROR, error = error.message ?: "无法安排自动重连")
            }
            return
        }
        synchronized(taskLock) {
            if (isActive(reconnectGeneration) && reconnectPending.get()) {
                reconnectTask = scheduled
            } else {
                scheduled.cancel(false)
            }
        }
    }

    private fun send(bytes: ByteArray, expectedGeneration: Int = generation.get()): Boolean {
        if (!isActive(expectedGeneration)) return false
        val activeSocket = socket ?: return false
        if (!status.connected || activeSocket.isClosed) return false
        return runCatching {
            synchronized(sendLock) { sendRaw(activeSocket, bytes) }
            true
        }.getOrElse {
            scheduleReconnect(it.message ?: "UDP 发送失败", expectedGeneration)
            false
        }
    }

    private fun sendRaw(activeSocket: DatagramSocket, bytes: ByteArray) {
        activeSocket.send(DatagramPacket(bytes, bytes.size))
        lastPacketSentAt = System.currentTimeMillis()
    }

    private fun createSocket(): DatagramSocket {
        val reusablePort = preferredLocalPort
        if (reusablePort > 0) {
            val reusableSocket = DatagramSocket(null)
            try {
                return reusableSocket.apply {
                    reuseAddress = true
                    bind(InetSocketAddress(reusablePort))
                }
            } catch (_: Exception) {
                runCatching { reusableSocket.close() }
            }
        }
        return DatagramSocket()
    }

    private fun stopTasks() {
        synchronized(taskLock) {
            heartbeatTask?.cancel(false)
            watchdogTask?.cancel(false)
            pttTimeoutTask?.cancel(false)
            heartbeatTask = null
            watchdogTask = null
            pttTimeoutTask = null
        }
    }

    private fun schedulePttTimeout(expectedGeneration: Int) {
        synchronized(taskLock) {
            pttTimeoutTask?.cancel(false)
            pttTimeoutTask = null
            if (!isActive(expectedGeneration) || !status.transmitting) return
            val remaining = transmitTimeoutSeconds * 1_000L -
                (System.currentTimeMillis() - pttStartedAt)
            pttTimeoutTask = scheduler.schedule(
                {
                    if (isActive(expectedGeneration) && status.transmitting) stopPtt()
                },
                remaining.coerceAtLeast(0L),
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun cancelPttTimeout() {
        synchronized(taskLock) {
            pttTimeoutTask?.cancel(false)
            pttTimeoutTask = null
        }
    }

    private fun cancelReconnectTask() {
        synchronized(taskLock) {
            reconnectTask?.cancel(false)
            reconnectTask = null
        }
    }

    @Synchronized
    private fun closeSocket(expected: DatagramSocket? = null) {
        val activeSocket = socket
        if (expected != null && activeSocket !== expected) {
            runCatching { expected.close() }
            return
        }
        activeSocket?.localPort?.takeIf { it > 0 }?.let { preferredLocalPort = it }
        socket = null
        runCatching { activeSocket?.close() }
    }

    private fun isActive(expectedGeneration: Int): Boolean =
        !manualDisconnect && generation.get() == expectedGeneration

    private fun updateStatus(newStatus: RadioStatus) {
        status = newStatus
        listener.onStatus(newStatus)
    }

    private fun updateStatusIfActive(
        expectedGeneration: Int,
        transform: (RadioStatus) -> RadioStatus,
    ): Boolean = synchronized(statusLock) {
        if (!isActive(expectedGeneration)) return@synchronized false
        val updated = transform(status)
        if (updated != status) updateStatus(updated)
        true
    }

    private fun reportNonFatal(message: String, expectedGeneration: Int) {
        updateStatusIfActive(expectedGeneration) { it.copy(error = message) }
    }

    private fun finishIncomingVoice(): RadioMessage? = synchronized(voiceSessionLock) {
        finishIncomingVoiceLocked()
    }

    private fun finishIncomingVoiceLocked(): RadioMessage? {
        if (lastSpeakerKey.isBlank() || incomingVoiceStartedAt <= 0L) return null
        val networkPayload = incomingVoiceBuffer.toByteArray()
        val endedAt = lastVoicePacketAt + VOICE_PACKET_DURATION_MS
        val messageId = UUID.randomUUID().toString()
        val message = RadioMessage(
            id = messageId,
            type = RadioMessageType.VOICE,
            senderCallsign = incomingVoiceCallsign.ifBlank { incomingVoiceUsername },
            senderSsid = incomingVoiceSsid,
            senderUsername = incomingVoiceUsername,
            content = "语音",
            timestamp = incomingVoiceStartedAt,
            mine = false,
            durationMs = (endedAt - incomingVoiceStartedAt).coerceAtLeast(VOICE_PACKET_DURATION_MS),
            audioCacheKey = cacheNetworkRecording(messageId, networkPayload),
            groupId = incomingVoiceGroupId,
        )
        lastSpeakerKey = ""
        incomingVoiceStartedAt = 0L
        incomingVoiceUsername = ""
        incomingVoiceCallsign = ""
        incomingVoiceSsid = 0
        incomingVoiceGroupId = 0
        incomingVoiceBuffer = ByteArrayOutputStream()
        return message
    }

    private fun appendVoiceData(buffer: ByteArrayOutputStream, payload: ByteArray) {
        val remaining = MAX_CACHED_VOICE_BYTES - buffer.size()
        if (payload.size <= remaining) buffer.write(payload)
    }

    private fun cacheNetworkRecording(messageId: String, payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val cacheKey = "message:$messageId"
        return runCatching {
            audioCache.put(cacheKey, RawOpusRecording.fromNetworkPayload(payload))
            cacheKey
        }.onFailure {
            reportNonFatal(it.message ?: "语音缓存失败", generation.get())
        }.getOrDefault("")
    }

    private fun completePlayback(messageId: String) {
        if (playingMessageId == messageId) {
            playingMessageId = null
            listener.onPlaybackState(null)
        }
    }

    private class RadioAuthException(message: String) : Exception(message)
    private class RetryableRadioException(message: String) : Exception(message)

    private fun formatDuration(milliseconds: Long): String {
        val seconds = (milliseconds / 1_000.0).coerceAtLeast(0.1)
        return String.format(java.util.Locale.CHINA, "%.1f 秒", seconds)
    }

    companion object {
        private const val AUTH_SOCKET_TIMEOUT_MS = 1_000
        private const val AUTH_TOTAL_TIMEOUT_MS = 5_000L
        private const val RECEIVE_SOCKET_TIMEOUT_MS = 1_000
        private const val HEARTBEAT_INTERVAL_MS = 2_000L
        private const val SERVER_SILENCE_TIMEOUT_MS = 8_000L
        private const val VOICE_END_TIMEOUT_MS = 700L
        private const val VOICE_PACKET_DURATION_MS = 120L
        private const val WATCHDOG_INTERVAL_MS = 250L
        private const val MAX_CACHED_VOICE_BYTES = 2 * 1024 * 1024
        private const val MIN_TRANSMIT_TIMEOUT_SECONDS = 10
        private const val DEFAULT_TRANSMIT_TIMEOUT_SECONDS = 120
        private const val MAX_TRANSMIT_TIMEOUT_SECONDS = 600
    }
}
