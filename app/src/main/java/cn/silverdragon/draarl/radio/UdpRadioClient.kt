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
    val clientInstanceId: String,
    val groupId: Int,
)

interface UdpRadioListener {
    fun onStatus(status: RadioStatus)
    fun onMessage(message: RadioMessage)
    fun onPlaybackState(messageId: String?)
    fun onPlaybackLevel(level: Float)
    fun onTransmitLevel(level: Float)
    fun onCwPreviewState(active: Boolean)
}

class UdpRadioClient(
    context: Context,
    private val listener: UdpRadioListener,
) {
    private val audioCache = RadioAudioCache(context.applicationContext.filesDir.resolve("radio_audio"))
    private val audioEngine = OpusAudioEngine(
        audioCache = audioCache,
        onPlaybackLevel = listener::onPlaybackLevel,
        onCaptureLevel = listener::onTransmitLevel,
    )
    private val connectionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "draarl-udp-connect")
    }
    private val scheduler = Executors.newScheduledThreadPool(2) { runnable ->
        Thread(runnable, "draarl-udp-scheduler")
    }
    private val generation = AtomicInteger(0)
    private val reconnectPending = AtomicBoolean(false)
    private val cwTransmitActive = AtomicBoolean(false)
    private val cwPreviewActive = AtomicBoolean(false)
    private val finishingPtt = AtomicBoolean(false)
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
    @Volatile private var pttStartedAt = 0L
    @Volatile private var transmitTimeoutSeconds = DEFAULT_TRANSMIT_TIMEOUT_SECONDS
    @Volatile private var transmitTailTone = TransmitTailTone.OFF
    @Volatile private var transmitTailToneToRemoteEnabled = true
    @Volatile private var receiveTailToneEnabled = false
    @Volatile private var playingMessageId: String? = null
    @Volatile private var sessionTag = 0L
    @Volatile private var sessionUsername = ""
    @Volatile private var sessionSsid = DraarlProtocol.SSID_ANDROID
    @Volatile private var status = RadioStatus()
    private val incomingVoiceStreams = LinkedHashMap<VoiceStreamKey, IncomingVoiceStream>()
    private val voicePlaybackQueue = VoiceStreamPlaybackQueue()
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
        cwTransmitActive.set(false)
        cwPreviewActive.set(false)
        listener.onCwPreviewState(false)
        finishingPtt.set(false)
        listener.onTransmitLevel(0f)
        val currentGeneration = generation.incrementAndGet()
        stopTasks()
        audioEngine.stopCapture()
        stopPlayback()
        finishAllIncomingVoices().forEach(listener::onMessage)
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
        cwTransmitActive.set(false)
        cwPreviewActive.set(false)
        listener.onCwPreviewState(false)
        finishingPtt.set(false)
        listener.onTransmitLevel(0f)
        generation.incrementAndGet()
        stopTasks()
        audioEngine.stopCapture()
        stopPlayback()
        finishAllIncomingVoices().forEach(listener::onMessage)
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
        return send(sessionText(normalized)).also { sent ->
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

    fun sendCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean {
        if (!status.connected || status.transmitting || status.speaker.isNotBlank()) return false
        stopCwPreview()
        val tone = runCatching { CwToneGenerator.generate(text, wordsPerMinute, toneHz) }
            .getOrElse { error ->
                reportNonFatal(error.message ?: "CW 内容无效", generation.get())
                return false
            }
        val tailSamples = TransmitTailToneGenerator.generate(transmitTailTone)
        val transmitDurationMs = (tone.samples.size + tailSamples.size) * 1_000L / OpusAudioFormat.SAMPLE_RATE
        if (transmitDurationMs > transmitTimeoutSeconds * 1_000L) {
            reportNonFatal("CW 音频超过当前发射限时，请缩短内容或提高速度", generation.get())
            return false
        }
        if (!cwTransmitActive.compareAndSet(false, true)) return false

        stopPlayback()
        audioEngine.resetDecoder()
        synchronized(outgoingVoiceLock) { outgoingVoiceBuffer = ByteArrayOutputStream() }
        val currentGeneration = generation.get()
        val startedAt = System.currentTimeMillis()
        val statusUpdated = updateStatusIfActive(currentGeneration) { current ->
            if (!current.connected) current else current.copy(transmitting = true, speaker = "", error = "")
        }
        if (!statusUpdated || !status.transmitting) {
            cwTransmitActive.set(false)
            return false
        }
        pttStartedAt = startedAt
        return runCatching {
            scheduler.execute {
                transmitCw(
                    text = tone.normalizedText,
                    toneSamples = tone.samples,
                    tailSamples = tailSamples,
                    currentGeneration = currentGeneration,
                    startedAt = startedAt,
                )
            }
            true
        }.getOrElse { error ->
            cwTransmitActive.set(false)
            updateStatusIfActive(currentGeneration) { it.copy(transmitting = false) }
            reportNonFatal(error.message ?: "无法启动 CW 发送", currentGeneration)
            false
        }
    }

    fun stopCw(): Boolean {
        if (!cwTransmitActive.compareAndSet(true, false)) return false
        audioEngine.stopRecordingPlayback()
        listener.onTransmitLevel(0f)
        return true
    }

    fun previewCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean {
        if (status.transmitting || status.speaker.isNotBlank()) return false
        val tone = runCatching { CwToneGenerator.generate(text, wordsPerMinute, toneHz) }
            .getOrElse { error ->
                reportNonFatal(error.message ?: "CW 内容无效", generation.get())
                return false
            }
        val tailSamples = TransmitTailToneGenerator.generate(transmitTailTone)
        if (!cwPreviewActive.compareAndSet(false, true)) return false
        stopPlayback()
        audioEngine.resetDecoder()
        listener.onCwPreviewState(true)
        val currentGeneration = generation.get()
        return runCatching {
            scheduler.execute { previewCwAudio(tone.samples, tailSamples, currentGeneration) }
            true
        }.getOrElse { error ->
            cwPreviewActive.set(false)
            listener.onCwPreviewState(false)
            reportNonFatal(error.message ?: "无法试听 CW", currentGeneration)
            false
        }
    }

    fun stopCwPreview(): Boolean {
        if (!cwPreviewActive.compareAndSet(true, false)) return false
        audioEngine.stopRecordingPlayback()
        listener.onCwPreviewState(false)
        return true
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
                if (status.transmitting && send(sessionVoice(opus), currentGeneration)) {
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
        if (!status.transmitting || cwTransmitActive.get()) return
        if (!finishingPtt.compareAndSet(false, true)) return
        cancelPttTimeout()
        val currentGeneration = generation.get()
        audioEngine.stopCapture()
        val tailSamples = TransmitTailToneGenerator.generate(transmitTailTone)
        if (tailSamples.isEmpty()) {
            finishPttTransmission(currentGeneration)
            return
        }
        audioEngine.resetDecoder()
        runCatching {
            scheduler.execute {
                try {
                    runCatching {
                        streamPcmPackets(
                            samples = tailSamples,
                            currentGeneration = currentGeneration,
                            shouldContinue = { finishingPtt.get() },
                            sendToRemote = transmitTailToneToRemoteEnabled,
                            monitorLocally = true,
                            reportTransmitLevel = true,
                            requireActive = true,
                        )
                    }.onFailure { error ->
                        reportNonFatal(error.message ?: "发射尾音发送失败", currentGeneration)
                    }
                } finally {
                    finishPttTransmission(currentGeneration)
                }
            }
        }.onFailure {
            finishPttTransmission(currentGeneration)
        }
    }

    @Synchronized
    private fun finishPttTransmission(currentGeneration: Int) {
        if (!finishingPtt.compareAndSet(true, false)) return
        val statusUpdated = updateStatusIfActive(currentGeneration) { it.copy(transmitting = false) }
        if (!statusUpdated) return
        val networkPayload = synchronized(outgoingVoiceLock) {
            outgoingVoiceBuffer.toByteArray().also { outgoingVoiceBuffer = ByteArrayOutputStream() }
        }
        val duration = networkPayloadDurationMs(networkPayload)
        if (duration > 100L && networkPayload.isNotEmpty()) {
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

    fun setPlaybackDenoiseEnabled(enabled: Boolean) = audioEngine.setDenoiseEnabled(enabled)

    fun setPlaybackDenoiseWetMix(value: Float) = audioEngine.setDenoiseWetMix(value)

    @Synchronized
    fun setTransmitTimeoutSeconds(seconds: Int) {
        transmitTimeoutSeconds = seconds.coerceIn(MIN_TRANSMIT_TIMEOUT_SECONDS, MAX_TRANSMIT_TIMEOUT_SECONDS)
        if (status.transmitting) schedulePttTimeout(generation.get())
    }

    fun setTransmitTailTone(tone: TransmitTailTone) {
        transmitTailTone = tone
    }

    fun setTransmitTailToneToRemoteEnabled(enabled: Boolean) {
        transmitTailToneToRemoteEnabled = enabled
    }

    fun setReceiveTailToneEnabled(enabled: Boolean) {
        receiveTailToneEnabled = enabled
    }

    fun transmitTimeoutSeconds(): Int = transmitTimeoutSeconds

    fun audioCacheSizeBytes(): Long = audioCache.sizeBytes()

    fun hasAudioCacheKey(key: String): Boolean = audioCache.contains(key)

    fun clearAudioCache() {
        stopPlayback()
        audioCache.clear()
    }

    fun setRouting(groupId: Int, receiveGroupIds: Collection<Int>) {
        desiredConfig = desiredConfig?.copy(groupId = groupId)
        updateStatus(status.copy(groupId = groupId, receiveGroupIds = receiveGroupIds.distinct()))
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
            sendRaw(newSocket, DraarlProtocol.ghostAuth(config.accessToken, config.clientInstanceId))
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
                throw RadioAuthException(message)
            }
            val session = runCatching { DraarlProtocol.parseGhostAuthSuccess(authResponse) }
                .getOrElse { throw RadioAuthException("UDP 认证响应无效：${it.message.orEmpty()}") }
            if (!session.clientInstanceId.equals(config.clientInstanceId, ignoreCase = true)) {
                throw RadioAuthException("UDP 认证响应的客户端实例不匹配")
            }
            sessionTag = session.sessionTag
            sessionUsername = authResponse.username
            sessionSsid = authResponse.ssid.takeIf { it > 0 } ?: DraarlProtocol.SSID_ANDROID
            newSocket.soTimeout = RECEIVE_SOCKET_TIMEOUT_MS
            lastServerPacketAt = System.currentTimeMillis()
            reconnectPending.set(false)
            if (!updateStatusIfActive(currentGeneration) { current ->
                current.copy(
                    phase = RadioConnectionPhase.CONNECTED,
                    callsign = authResponse.callsign,
                    ssid = sessionSsid,
                    groupId = session.txGroupId,
                    sessionId = session.sessionId,
                    clientInstanceId = session.clientInstanceId,
                    receiveGroupIds = session.rxGroupIds,
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
                        sourceGroupId(packet.reserved),
                        packet.data,
                    )
                    DraarlProtocol.TYPE_OPUS_16K -> handleVoice(
                        packet.username,
                        packet.callsign,
                        packet.ssid,
                        sourceGroupId(packet.reserved),
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

    private fun handleText(username: String, callsign: String, ssid: Int, groupId: Int, payload: ByteArray) {
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
                groupId = groupId,
            ),
        )
    }

    private fun handleVoice(
        username: String,
        callsign: String,
        ssid: Int,
        groupId: Int,
        payload: ByteArray,
        currentGeneration: Int,
    ) {
        if (payload.isEmpty() || status.transmitting) return
        val identity = callsign.ifBlank { username }
        val speaker = formatRadioIdentity(identity, ssid)
        val speakerKey = VoiceStreamKey(groupId, identity, ssid)
        val now = System.currentTimeMillis()
        var startingSession = false
        var playImmediately = false
        var evictedMessage: RadioMessage? = null
        synchronized(voiceSessionLock) {
            var stream = incomingVoiceStreams[speakerKey]
            if (stream == null) {
                if (incomingVoiceStreams.size >= MAX_INCOMING_VOICE_STREAMS) {
                    val oldest = voicePlaybackQueue.evictOldestPending()
                        ?: incomingVoiceStreams.minByOrNull { it.value.lastPacketAt }?.key
                    oldest?.let { key ->
                        evictedMessage = finishIncomingVoiceLocked(key)
                    }
                }
                stream = IncomingVoiceStream(
                    username = username,
                    callsign = callsign,
                    ssid = ssid,
                    groupId = groupId,
                    startedAt = now,
                    lastPacketAt = now,
                )
                incomingVoiceStreams[speakerKey] = stream
                startingSession = voicePlaybackQueue.active == null
                voicePlaybackQueue.onStream(speakerKey)
            }
            stream.lastPacketAt = now
            appendVoiceData(stream.buffer, payload)
            playImmediately = voicePlaybackQueue.active == speakerKey
        }
        evictedMessage?.let(listener::onMessage)
        if (startingSession) {
            stopPlayback()
        }
        if (playImmediately) {
            updateStatusIfActive(currentGeneration) { it.copy(speaker = speaker) }
            audioEngine.play(speakerKey.playbackKey, payload) { message -> reportNonFatal(message, currentGeneration) }
        }
    }

    private fun previewCwAudio(toneSamples: ShortArray, tailSamples: ShortArray, currentGeneration: Int) {
        try {
            streamPcmPackets(
                samples = toneSamples,
                currentGeneration = currentGeneration,
                shouldContinue = { cwPreviewActive.get() },
                sendToRemote = false,
                monitorLocally = true,
                reportTransmitLevel = false,
                requireActive = false,
            )
            if (tailSamples.isNotEmpty() && cwPreviewActive.get()) {
                streamPcmPackets(
                    samples = tailSamples,
                    currentGeneration = currentGeneration,
                    shouldContinue = { cwPreviewActive.get() },
                    sendToRemote = false,
                    monitorLocally = true,
                    reportTransmitLevel = false,
                    requireActive = false,
                )
            }
        } catch (error: Exception) {
            reportNonFatal(error.message ?: "CW 试听失败", currentGeneration)
        } finally {
            cwPreviewActive.set(false)
            listener.onCwPreviewState(false)
            if (status.speaker.isBlank()) listener.onPlaybackLevel(0f)
        }
    }

    private fun transmitCw(
        text: String,
        toneSamples: ShortArray,
        tailSamples: ShortArray,
        currentGeneration: Int,
        startedAt: Long,
    ) {
        try {
            streamPcmPackets(
                samples = toneSamples,
                currentGeneration = currentGeneration,
                shouldContinue = { cwTransmitActive.get() },
                sendToRemote = true,
                monitorLocally = true,
                reportTransmitLevel = true,
                requireActive = true,
            )
            if (tailSamples.isNotEmpty() && cwTransmitActive.get()) {
                streamPcmPackets(
                    samples = tailSamples,
                    currentGeneration = currentGeneration,
                    shouldContinue = { cwTransmitActive.get() },
                    sendToRemote = transmitTailToneToRemoteEnabled,
                    monitorLocally = true,
                    reportTransmitLevel = true,
                    requireActive = true,
                )
            }
        } catch (error: Exception) {
            reportNonFatal(error.message ?: "CW 音频发送失败", currentGeneration)
        } finally {
            cwTransmitActive.set(false)
            listener.onTransmitLevel(0f)
            finishCwTransmission(text, currentGeneration, startedAt)
        }
    }

    private fun streamPcmPackets(
        samples: ShortArray,
        currentGeneration: Int,
        shouldContinue: () -> Boolean,
        sendToRemote: Boolean,
        monitorLocally: Boolean,
        reportTransmitLevel: Boolean,
        requireActive: Boolean,
    ) {
        val encoder = OpusFrameEncoder()
        val packetSamples = OpusAudioFormat.FRAME_SAMPLES * OpusAudioFormat.FRAMES_PER_PACKET
        var packetOffset = 0
        while (
            packetOffset < samples.size &&
            shouldContinue() &&
            (!requireActive || isActive(currentGeneration)) &&
            (!sendToRemote || status.transmitting)
        ) {
            val encodedFrames = ArrayList<ByteArray>(OpusAudioFormat.FRAMES_PER_PACKET)
            var packetLevel = 0f
            repeat(OpusAudioFormat.FRAMES_PER_PACKET) { frameIndex ->
                val offset = packetOffset + frameIndex * OpusAudioFormat.FRAME_SAMPLES
                val frame = samples.copyOfRange(offset, offset + OpusAudioFormat.FRAME_SAMPLES)
                packetLevel = maxOf(packetLevel, normalizedPcmLevel(frame))
                encoder.encode(frame)?.let(encodedFrames::add)
            }
            if (encodedFrames.size != OpusAudioFormat.FRAMES_PER_PACKET) return
            val payload = DraarlProtocol.mergeOpusFrames(encodedFrames)
            if (reportTransmitLevel) listener.onTransmitLevel(packetLevel)
            if (sendToRemote) {
                if (!send(sessionVoice(payload), currentGeneration)) return
                synchronized(outgoingVoiceLock) { appendVoiceData(outgoingVoiceBuffer, payload) }
            }
            if (monitorLocally) {
                audioEngine.play(payload) { message ->
                    reportNonFatal("本地音频播放失败：$message", currentGeneration)
                }
            }
            packetOffset += packetSamples
            Thread.sleep(VOICE_PACKET_DURATION_MS)
        }
    }

    @Synchronized
    private fun finishCwTransmission(text: String, currentGeneration: Int, startedAt: Long) {
        if (!isActive(currentGeneration) || !status.transmitting) return
        updateStatusIfActive(currentGeneration) { it.copy(transmitting = false) }
        val networkPayload = synchronized(outgoingVoiceLock) {
            outgoingVoiceBuffer.toByteArray().also { outgoingVoiceBuffer = ByteArrayOutputStream() }
        }
        if (networkPayload.isEmpty()) return
        val messageId = UUID.randomUUID().toString()
        listener.onMessage(
            RadioMessage(
                id = messageId,
                type = RadioMessageType.VOICE,
                senderCallsign = status.callsign,
                senderSsid = status.ssid,
                content = "CW: $text",
                timestamp = startedAt,
                mine = true,
                durationMs = networkPayloadDurationMs(networkPayload),
                audioCacheKey = cacheNetworkRecording(messageId, networkPayload),
                groupId = status.groupId,
            ),
        )
    }

    private fun startTasks(currentGeneration: Int) {
        synchronized(taskLock) {
            if (!isActive(currentGeneration)) return
            heartbeatTask = scheduler.scheduleWithFixedDelay(
                {
                    if (!manualDisconnect && currentGeneration == generation.get() && status.connected) {
                        send(sessionHeartbeat(), currentGeneration)
                    }
                },
                0,
                HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS,
            )
            watchdogTask = scheduler.scheduleWithFixedDelay(
                {
                    val now = System.currentTimeMillis()
                    val completedMessages = finishExpiredAndAdvanceVoiceQueue(now, currentGeneration)
                    completedMessages.forEach(listener::onMessage)
                    if (completedMessages.isNotEmpty()) {
                        val activeSpeaker = activeIncomingSpeaker()
                        updateStatusIfActive(currentGeneration) { it.copy(speaker = activeSpeaker) }
                        if (activeSpeaker.isBlank()) playReceiveTailTone(currentGeneration)
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
        cwTransmitActive.set(false)
        cwPreviewActive.set(false)
        listener.onCwPreviewState(false)
        finishingPtt.set(false)
        listener.onTransmitLevel(0f)
        stopTasks()
        audioEngine.stopCapture()
        finishAllIncomingVoices().forEach(listener::onMessage)
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

    private fun sessionHeartbeat(): ByteArray = DraarlProtocol.heartbeat(
        username = sessionUsername,
        ssid = sessionSsid,
        sessionTag = sessionTag,
    )

    private fun sessionText(text: String): ByteArray = DraarlProtocol.text(
        message = text,
        username = sessionUsername,
        ssid = sessionSsid,
        sessionTag = sessionTag,
    )

    private fun sessionVoice(payload: ByteArray): ByteArray = DraarlProtocol.voice(
        mergedOpusFrames = payload,
        username = sessionUsername,
        ssid = sessionSsid,
        sessionTag = sessionTag,
    )

    private fun sourceGroupId(reserved: Long): Int =
        reserved.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: status.groupId

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
        sessionTag = 0
        sessionUsername = ""
        sessionSsid = DraarlProtocol.SSID_ANDROID
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

    private fun playReceiveTailTone(currentGeneration: Int) {
        if (!receiveTailToneEnabled || status.transmitting || status.speaker.isNotBlank()) return
        val samples = TransmitTailToneGenerator.generate(transmitTailTone)
        if (samples.isEmpty()) return
        audioEngine.resetDecoder()
        try {
            streamPcmPackets(
                samples = samples,
                currentGeneration = currentGeneration,
                shouldContinue = {
                    receiveTailToneEnabled &&
                        !hasIncomingVoice() &&
                        !status.transmitting &&
                        status.speaker.isBlank()
                },
                sendToRemote = false,
                monitorLocally = true,
                reportTransmitLevel = false,
                requireActive = true,
            )
        } catch (error: Exception) {
            reportNonFatal(error.message ?: "接收尾音播放失败", currentGeneration)
        } finally {
            if (status.speaker.isBlank()) listener.onPlaybackLevel(0f)
        }
    }

    private fun finishExpiredAndAdvanceVoiceQueue(now: Long, currentGeneration: Int): List<RadioMessage> =
        synchronized(voiceSessionLock) {
            val completed = mutableListOf<RadioMessage>()
            val activeKey = voicePlaybackQueue.active
            val active = activeKey?.let(incomingVoiceStreams::get)
            if (activeKey != null && (active == null || now - active.lastPacketAt > VOICE_END_TIMEOUT_MS)) {
                finishIncomingVoiceLocked(activeKey)?.let(completed::add)
            }

            var nextKey = voicePlaybackQueue.advance()
            while (nextKey != null) {
                val next = incomingVoiceStreams[nextKey]
                if (next == null) {
                    voicePlaybackQueue.remove(nextKey)
                    nextKey = voicePlaybackQueue.advance()
                    continue
                }
                val backlog = next.buffer.toByteArray()
                if (backlog.isNotEmpty()) {
                    audioEngine.play(nextKey.playbackKey, backlog) { message ->
                        reportNonFatal(message, currentGeneration)
                    }
                }
                if (now - next.lastPacketAt > VOICE_END_TIMEOUT_MS) {
                    finishIncomingVoiceLocked(nextKey)?.let(completed::add)
                    nextKey = voicePlaybackQueue.advance()
                } else {
                    break
                }
            }
            completed
        }
    }

    private fun finishAllIncomingVoices(): List<RadioMessage> = synchronized(voiceSessionLock) {
        val messages = incomingVoiceStreams.keys.toList().mapNotNull(::finishIncomingVoiceLocked)
        voicePlaybackQueue.clear()
        messages
    }

    private fun finishIncomingVoiceLocked(key: VoiceStreamKey): RadioMessage? {
        val stream = incomingVoiceStreams.remove(key) ?: return null
        voicePlaybackQueue.remove(key)
        audioEngine.endLiveStream(key.playbackKey)
        val networkPayload = stream.buffer.toByteArray()
        val endedAt = stream.lastPacketAt + VOICE_PACKET_DURATION_MS
        val messageId = UUID.randomUUID().toString()
        val message = RadioMessage(
            id = messageId,
            type = RadioMessageType.VOICE,
            senderCallsign = stream.callsign.ifBlank { stream.username },
            senderSsid = stream.ssid,
            senderUsername = stream.username,
            content = "语音",
            timestamp = stream.startedAt,
            mine = false,
            durationMs = (endedAt - stream.startedAt).coerceAtLeast(VOICE_PACKET_DURATION_MS),
            audioCacheKey = cacheNetworkRecording(messageId, networkPayload),
            groupId = stream.groupId,
        )
        return message
    }

    private fun hasIncomingVoice(): Boolean = synchronized(voiceSessionLock) { incomingVoiceStreams.isNotEmpty() }

    private fun activeIncomingSpeaker(): String = synchronized(voiceSessionLock) {
        val stream = voicePlaybackQueue.active?.let(incomingVoiceStreams::get)
            ?: return@synchronized ""
        formatRadioIdentity(stream.callsign.ifBlank { stream.username }, stream.ssid)
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

    private fun networkPayloadDurationMs(payload: ByteArray): Long =
        DraarlProtocol.splitOpusFrames(payload).size.toLong() *
            OpusAudioFormat.FRAME_SAMPLES * 1_000L / OpusAudioFormat.SAMPLE_RATE

    private fun completePlayback(messageId: String) {
        if (playingMessageId == messageId) {
            playingMessageId = null
            listener.onPlaybackState(null)
        }
    }

    private data class IncomingVoiceStream(
        val username: String,
        val callsign: String,
        val ssid: Int,
        val groupId: Int,
        val startedAt: Long,
        var lastPacketAt: Long,
        val buffer: ByteArrayOutputStream = ByteArrayOutputStream(),
    )

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
        private const val MAX_INCOMING_VOICE_STREAMS = 32
        private const val MIN_TRANSMIT_TIMEOUT_SECONDS = 10
        private const val DEFAULT_TRANSMIT_TIMEOUT_SECONDS = 120
        private const val MAX_TRANSMIT_TIMEOUT_SECONDS = 600
    }
}
