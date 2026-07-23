package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.protocol.DraarlProtocol
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
}

class UdpRadioClient(
    private val listener: UdpRadioListener,
) {
    private val audioEngine = OpusAudioEngine()
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
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var desiredConfig: RadioConnectionConfig? = null
    @Volatile private var manualDisconnect = true
    @Volatile private var lastServerPacketAt = 0L
    @Volatile private var lastVoicePacketAt = 0L
    @Volatile private var lastSpeakerKey = ""
    @Volatile private var pttStartedAt = 0L
    @Volatile private var status = RadioStatus()
    private var heartbeatTask: ScheduledFuture<*>? = null
    private var watchdogTask: ScheduledFuture<*>? = null

    fun connect(config: RadioConnectionConfig) {
        desiredConfig = config
        manualDisconnect = false
        reconnectPending.set(false)
        val currentGeneration = generation.incrementAndGet()
        stopTasks()
        audioEngine.stopCapture()
        closeSocket()
        connectionExecutor.execute { establish(config, reconnecting = false, currentGeneration) }
    }

    fun updateAccessToken(token: String) {
        desiredConfig = desiredConfig?.copy(accessToken = token)
    }

    fun disconnect() {
        manualDisconnect = true
        desiredConfig = null
        reconnectPending.set(false)
        generation.incrementAndGet()
        stopTasks()
        audioEngine.stopCapture()
        closeSocket()
        synchronized(statusLock) {
            updateStatus(RadioStatus(phase = RadioConnectionPhase.DISCONNECTED))
        }
    }

    fun sendText(text: String): Boolean {
        val normalized = text.trim()
        if (!status.connected || normalized.isEmpty()) return false
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
                    ),
                )
            }
        }
    }

    fun startPtt(): Boolean {
        if (!status.connected || status.transmitting) return false
        val currentGeneration = generation.get()
        audioEngine.resetDecoder()
        val started = audioEngine.startCapture(
            onPacket = { opus ->
                if (status.transmitting) send(DraarlProtocol.voice(opus), currentGeneration)
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
        return true
    }

    fun stopPtt() {
        if (!status.transmitting) return
        val currentGeneration = generation.get()
        val statusUpdated = updateStatusIfActive(currentGeneration) { it.copy(transmitting = false) }
        audioEngine.stopCapture()
        if (!statusUpdated) return
        val duration = (System.currentTimeMillis() - pttStartedAt).coerceAtLeast(0L)
        if (duration > 100L) {
            listener.onMessage(
                RadioMessage(
                    id = UUID.randomUUID().toString(),
                    type = RadioMessageType.VOICE,
                    senderCallsign = status.callsign,
                    senderSsid = status.ssid,
                    content = "语音 ${formatDuration(duration)}",
                    timestamp = pttStartedAt,
                    mine = true,
                    durationMs = duration,
                ),
            )
        }
    }

    fun setMuted(muted: Boolean) = audioEngine.setMuted(muted)

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
            val newSocket = DatagramSocket().apply {
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
                if (authStatus == 6 && reconnecting) throw IllegalStateException(message)
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
                    DraarlProtocol.TYPE_TEXT -> handleText(packet.callsign, packet.ssid, packet.data)
                    DraarlProtocol.TYPE_OPUS_16K -> handleVoice(
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

    private fun handleText(callsign: String, ssid: Int, payload: ByteArray) {
        if (payload.isEmpty()) return
        listener.onMessage(
            RadioMessage(
                id = UUID.randomUUID().toString(),
                type = RadioMessageType.TEXT,
                senderCallsign = callsign.ifBlank { "SSID-$ssid" },
                senderSsid = ssid,
                content = payload.toString(Charsets.UTF_8),
                timestamp = System.currentTimeMillis(),
                mine = false,
            ),
        )
    }

    private fun handleVoice(callsign: String, ssid: Int, payload: ByteArray, currentGeneration: Int) {
        if (payload.isEmpty() || status.transmitting) return
        val speaker = callsign.ifBlank { "SSID-$ssid" }
        val speakerKey = "$speaker-$ssid"
        val now = System.currentTimeMillis()
        if (speakerKey != lastSpeakerKey || now - lastVoicePacketAt > VOICE_END_TIMEOUT_MS) {
            audioEngine.resetDecoder()
            listener.onMessage(
                RadioMessage(
                    id = UUID.randomUUID().toString(),
                    type = RadioMessageType.VOICE,
                    senderCallsign = speaker,
                    senderSsid = ssid,
                    content = "语音通话",
                    timestamp = now,
                    mine = false,
                ),
            )
        }
        lastSpeakerKey = speakerKey
        lastVoicePacketAt = now
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
                    if (status.speaker.isNotBlank() && now - lastVoicePacketAt > VOICE_END_TIMEOUT_MS) {
                        lastSpeakerKey = ""
                        updateStatusIfActive(currentGeneration) { it.copy(speaker = "") }
                    }
                    if (status.connected && now - lastServerPacketAt > SERVER_SILENCE_TIMEOUT_MS) {
                        scheduleReconnect("服务器心跳超时", currentGeneration)
                    }
                },
                1,
                1,
                TimeUnit.SECONDS,
            )
        }
    }

    private fun scheduleReconnect(reason: String, expectedGeneration: Int = generation.get()) {
        if (!isActive(expectedGeneration) || !reconnectPending.compareAndSet(false, true)) return
        val reconnectGeneration = generation.incrementAndGet()
        stopTasks()
        audioEngine.stopCapture()
        closeSocket()
        if (!updateStatusIfActive(reconnectGeneration) {
            it.copy(phase = RadioConnectionPhase.RECONNECTING, error = reason, transmitting = false)
        }) {
            reconnectPending.set(false)
            return
        }
        scheduler.schedule(
            {
                if (!reconnectPending.compareAndSet(true, false) || !isActive(reconnectGeneration)) {
                    return@schedule
                }
                val config = desiredConfig
                if (!manualDisconnect && config != null) {
                    connectionExecutor.execute { establish(config, reconnecting = true, reconnectGeneration) }
                }
            },
            RECONNECT_DELAY_MS,
            TimeUnit.MILLISECONDS,
        )
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
    }

    private fun stopTasks() {
        synchronized(taskLock) {
            heartbeatTask?.cancel(true)
            watchdogTask?.cancel(true)
            heartbeatTask = null
            watchdogTask = null
        }
    }

    @Synchronized
    private fun closeSocket(expected: DatagramSocket? = null) {
        val activeSocket = socket
        if (expected != null && activeSocket !== expected) {
            runCatching { expected.close() }
            return
        }
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
        updateStatus(transform(status))
        true
    }

    private fun reportNonFatal(message: String, expectedGeneration: Int) {
        updateStatusIfActive(expectedGeneration) { it.copy(error = message) }
    }

    private class RadioAuthException(message: String) : Exception(message)

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
        private const val RECONNECT_DELAY_MS = 3_000L
    }
}
