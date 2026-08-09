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
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class RadioConnectionConfig(
    val accessPoint: AccessPoint,
    val accessToken: String,
    val clientInstanceId: String,
    val groupId: Int
)

interface UdpRadioListener {
    fun onStatus(status: RadioStatus)
    fun onMessage(message: RadioMessage)
    fun onPlaybackState(messageId: String?)
    fun onPlaybackLevel(level: Float)
    fun onTransmitLevel(level: Float)
    fun onCwPreviewState(active: Boolean)
}

class UdpRadioClient internal constructor(
    private val listener: UdpRadioListener,
    audioRuntime: RadioAudioRuntime,
    private val transportFactory: UdpTransportFactory,
    private val clock: RadioClock,
    scheduler: RadioScheduler
) {
    constructor(context: Context, listener: UdpRadioListener) : this(
        listener = listener,
        audioRuntime = AndroidRadioAudioRuntime.create(context, listener),
        transportFactory = DatagramUdpTransportFactory(),
        clock = SystemRadioClock,
        scheduler = ExecutorRadioScheduler(threadCount = 2, threadName = "draarl-udp-scheduler")
    )

    private val audioCache = audioRuntime.store
    private val audioEngine = audioRuntime.engine
    private val connectionExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "draarl-udp-connect")
    }
    private val sessionMonitor = UdpSessionMonitor(
        scheduler = scheduler,
        clock = clock,
        heartbeatIntervalMillis = HEARTBEAT_INTERVAL_MS,
        watchdogIntervalMillis = WATCHDOG_INTERVAL_MS,
        serverSilenceTimeoutMillis = SERVER_SILENCE_TIMEOUT_MS
    )
    private val sessionTasks = UdpSessionTaskCoordinator(scheduler)
    private val connectionState = UdpConnectionStateMachine()
    private val authenticationReceiver = UdpAuthenticationReceiver(AUTH_TOTAL_TIMEOUT_MS, clock::nowMillis)
    private val cwTransmitActive = AtomicBoolean(false)
    private val cwPreviewActive = AtomicBoolean(false)
    private val finishingPtt = AtomicBoolean(false)
    private val sendLock = Any()
    private val statusLock = Any()
    private val voiceSessionLock = Any()
    private val outgoingVoiceLock = Any()

    @Volatile private var transport: UdpTransport? = null

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

    @Synchronized
    fun connect(config: RadioConnectionConfig) {
        val attempt = connectionState.connect(config) ?: return
        sessionTasks.cancelReconnect()
        cwTransmitActive.set(false)
        cwPreviewActive.set(false)
        listener.onCwPreviewState(false)
        finishingPtt.set(false)
        listener.onTransmitLevel(0f)
        stopSessionTasks()
        audioEngine.capture.stop()
        stopPlayback()
        finishAllIncomingVoices().forEach(listener::onMessage)
        closeTransport()
        connectionExecutor.execute { establish(attempt) }
    }

    fun updateAccessToken(token: String) {
        connectionState.dispatch(UdpConnectionEvent.AccessTokenChanged(token))
    }

    fun disconnect() {
        connectionState.dispatch(UdpConnectionEvent.Disconnect)
        sessionTasks.cancelReconnect()
        cwTransmitActive.set(false)
        cwPreviewActive.set(false)
        listener.onCwPreviewState(false)
        finishingPtt.set(false)
        listener.onTransmitLevel(0f)
        stopSessionTasks()
        audioEngine.capture.stop()
        stopPlayback()
        finishAllIncomingVoices().forEach(listener::onMessage)
        closeTransport()
        synchronized(statusLock) {
            updateStatus(RadioStatus(phase = RadioConnectionPhase.DISCONNECTED))
        }
    }

    fun sendText(text: String): Boolean {
        val normalized = text.trim()
        if (!status.connected || status.transmitting || status.speaker.isNotBlank() ||
            normalized.isEmpty()
        ) {
            return false
        }
        val payload = normalized.toByteArray(Charsets.UTF_8)
        if (payload.size > DraarlProtocol.MAX_PACKET_SIZE - DraarlProtocol.HEADER_SIZE) {
            reportNonFatal("消息过长，UTF-8 编码后不能超过 710 字节", connectionState.generation())
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
                        timestamp = clock.nowMillis(),
                        mine = true,
                        groupId = status.groupId
                    )
                )
            }
        }
    }

    fun sendCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean {
        if (!status.connected || status.transmitting || status.speaker.isNotBlank()) return false
        stopCwPreview()
        val tone = runCatching { CwToneGenerator.generate(text, wordsPerMinute, toneHz) }
            .getOrElse { error ->
                reportNonFatal(error.message ?: "CW 内容无效", connectionState.generation())
                return false
            }
        val tailSamples = TransmitTailToneGenerator.generate(transmitTailTone)
        val transmitDurationMs = (tone.samples.size + tailSamples.size) * 1_000L / OpusAudioFormat.SAMPLE_RATE
        if (transmitDurationMs > transmitTimeoutSeconds * 1_000L) {
            reportNonFatal("CW 音频超过当前发射限时，请缩短内容或提高速度", connectionState.generation())
            return false
        }
        if (!cwTransmitActive.compareAndSet(false, true)) return false

        stopPlayback()
        audioEngine.playback.resetDecoder()
        synchronized(outgoingVoiceLock) { outgoingVoiceBuffer = ByteArrayOutputStream() }
        val currentGeneration = connectionState.generation()
        val startedAt = clock.nowMillis()
        val statusUpdated = updateStatusIfActive(currentGeneration) { current ->
            if (!current.connected) current else current.copy(transmitting = true, speaker = "", error = "")
        }
        if (!statusUpdated || !status.transmitting) {
            cwTransmitActive.set(false)
            return false
        }
        pttStartedAt = startedAt
        return runCatching {
            sessionTasks.execute {
                transmitCw(
                    text = tone.normalizedText,
                    toneSamples = tone.samples,
                    tailSamples = tailSamples,
                    currentGeneration = currentGeneration,
                    startedAt = startedAt
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
        audioEngine.playback.stopRecording()
        listener.onTransmitLevel(0f)
        return true
    }

    fun previewCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean {
        if (status.transmitting || status.speaker.isNotBlank()) return false
        val tone = runCatching { CwToneGenerator.generate(text, wordsPerMinute, toneHz) }
            .getOrElse { error ->
                reportNonFatal(error.message ?: "CW 内容无效", connectionState.generation())
                return false
            }
        val tailSamples = TransmitTailToneGenerator.generate(transmitTailTone)
        if (!cwPreviewActive.compareAndSet(false, true)) return false
        stopPlayback()
        audioEngine.playback.resetDecoder()
        listener.onCwPreviewState(true)
        val currentGeneration = connectionState.generation()
        return runCatching {
            sessionTasks.execute { previewCwAudio(tone.samples, tailSamples, currentGeneration) }
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
        audioEngine.playback.stopRecording()
        listener.onCwPreviewState(false)
        return true
    }

    @Synchronized
    fun startPtt(): Boolean {
        if (!status.connected || status.transmitting || status.speaker.isNotBlank()) return false
        stopPlayback()
        synchronized(outgoingVoiceLock) { outgoingVoiceBuffer = ByteArrayOutputStream() }
        val currentGeneration = connectionState.generation()
        audioEngine.playback.resetDecoder()
        val started = audioEngine.capture.start(
            onPacket = { opus ->
                if (status.transmitting && send(sessionVoice(opus), currentGeneration)) {
                    synchronized(outgoingVoiceLock) { appendVoiceData(outgoingVoiceBuffer, opus) }
                }
            },
            onError = { message -> reportNonFatal(message, currentGeneration) }
        )
        if (!started) return false
        val statusUpdated = updateStatusIfActive(currentGeneration) { current ->
            if (!current.connected) current else current.copy(transmitting = true, speaker = "")
        }
        if (!statusUpdated || !status.transmitting) {
            audioEngine.capture.stop()
            return false
        }
        pttStartedAt = clock.nowMillis()
        schedulePttTimeout(currentGeneration)
        return true
    }

    @Synchronized
    fun stopPtt() {
        if (!status.transmitting || cwTransmitActive.get()) return
        if (!finishingPtt.compareAndSet(false, true)) return
        sessionTasks.cancelPttTimeout()
        val currentGeneration = connectionState.generation()
        audioEngine.capture.stop()
        val tailSamples = TransmitTailToneGenerator.generate(transmitTailTone)
        if (tailSamples.isEmpty()) {
            finishPttTransmission(currentGeneration)
            return
        }
        audioEngine.playback.resetDecoder()
        runCatching {
            sessionTasks.execute {
                try {
                    runCatching {
                        streamPcmPackets(
                            samples = tailSamples,
                            currentGeneration = currentGeneration,
                            shouldContinue = { finishingPtt.get() },
                            sendToRemote = transmitTailToneToRemoteEnabled,
                            monitorLocally = true,
                            reportTransmitLevel = true,
                            requireActive = true
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
                    groupId = status.groupId
                )
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
        val started = audioEngine.playback.playRecording(
            audioCacheKey = message.audioCacheKey,
            audioUrl = message.audioUrl,
            onFinished = { completePlayback(message.id) },
            onError = { error ->
                reportNonFatal(error, connectionState.generation())
                completePlayback(message.id)
            }
        )
        if (!started) completePlayback(message.id)
        return started
    }

    fun stopPlayback() {
        audioEngine.playback.stopRecording()
        if (playingMessageId != null) {
            playingMessageId = null
            listener.onPlaybackState(null)
        }
    }

    fun setMuted(muted: Boolean) = audioEngine.playback.setMuted(muted)

    fun setPlaybackDenoiseEnabled(enabled: Boolean) = audioEngine.playback.setDenoiseEnabled(enabled)

    fun setPlaybackDenoiseWetMix(value: Float) = audioEngine.playback.setDenoiseWetMix(value)

    @Synchronized
    fun setTransmitTimeoutSeconds(seconds: Int) {
        transmitTimeoutSeconds = seconds.coerceIn(MIN_TRANSMIT_TIMEOUT_SECONDS, MAX_TRANSMIT_TIMEOUT_SECONDS)
        if (status.transmitting) schedulePttTimeout(connectionState.generation())
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
        connectionState.dispatch(UdpConnectionEvent.RoutingChanged(groupId))
        updateStatus(status.copy(groupId = groupId, receiveGroupIds = receiveGroupIds.distinct()))
    }

    fun snapshot(): RadioStatus = status

    @Synchronized
    fun release() {
        if (connectionState.isClosed()) return
        disconnect()
        connectionState.dispatch(UdpConnectionEvent.Close)
        audioEngine.release()
        connectionExecutor.shutdownNow()
        sessionTasks.close()
    }

    private fun establish(attempt: UdpConnectionAttempt) {
        val config = attempt.config
        val currentGeneration = attempt.generation
        if (!prepareAttempt(attempt)) return
        try {
            openTransport(config, currentGeneration)?.let { newTransport ->
                authenticate(newTransport, config, currentGeneration)?.let { authenticated ->
                    if (activateSession(newTransport, authenticated, currentGeneration)) {
                        startOnlineSession(newTransport, currentGeneration)
                    }
                }
            }
        } catch (error: UdpAuthenticationException) {
            closeTransport()
            if (connectionState.dispatch(UdpConnectionEvent.AuthenticationFailed(currentGeneration))) {
                updateStatusIfActive(currentGeneration) {
                    it.copy(phase = RadioConnectionPhase.ERROR, error = error.message.orEmpty())
                }
            }
        } catch (error: Exception) {
            closeTransport()
            scheduleReconnect(error.message ?: "UDP 连接失败", currentGeneration)
        }
    }

    private fun startOnlineSession(activeTransport: UdpTransport, currentGeneration: Int) {
        startTasks(currentGeneration)
        if (isActive(currentGeneration)) {
            startReceiver(activeTransport, currentGeneration)
        } else {
            stopSessionTasks()
            closeTransport(activeTransport)
        }
    }

    private fun prepareAttempt(attempt: UdpConnectionAttempt): Boolean {
        val activeBeforeCleanup = isActive(attempt.generation)
        if (activeBeforeCleanup) {
            stopSessionTasks()
            audioEngine.capture.stop()
            closeTransport()
        }
        return activeBeforeCleanup &&
            isActive(attempt.generation) &&
            updateStatusIfActive(attempt.generation) { current ->
                current.copy(
                    phase = if (attempt.reconnecting) {
                        RadioConnectionPhase.RECONNECTING
                    } else {
                        RadioConnectionPhase.CONNECTING
                    },
                    endpoint = attempt.config.accessPoint.address,
                    groupId = attempt.config.groupId,
                    transmitting = false,
                    speaker = "",
                    error = ""
                )
            }
    }

    private fun openTransport(config: RadioConnectionConfig, currentGeneration: Int): UdpTransport? {
        val newTransport = transportFactory.open(
            host = config.accessPoint.host,
            port = config.accessPoint.port,
            preferredLocalPort = preferredLocalPort,
            receiveTimeoutMillis = AUTH_SOCKET_TIMEOUT_MS
        )
        var openedTransport: UdpTransport? = null
        if (isActive(currentGeneration)) {
            transport = newTransport
            if (isActive(currentGeneration)) {
                openedTransport = newTransport
            } else {
                closeTransport(newTransport)
            }
        } else {
            newTransport.close()
        }
        return openedTransport
    }

    private fun authenticate(
        activeTransport: UdpTransport,
        config: RadioConnectionConfig,
        currentGeneration: Int
    ): UdpAuthenticatedSession? {
        val started =
            connectionState.dispatch(UdpConnectionEvent.AuthenticationStarted(currentGeneration)) &&
                updateStatusIfActive(currentGeneration) { it.copy(phase = RadioConnectionPhase.AUTHENTICATING) }
        if (!started) {
            closeTransport(activeTransport)
        }
        val response = if (started) {
            sendRaw(activeTransport, DraarlProtocol.ghostAuth(config.accessToken, config.clientInstanceId))
            awaitAuthResponse(activeTransport)
        } else {
            null
        }
        val activeResponse = response?.takeIf { isActive(currentGeneration) }
        if (response != null && activeResponse == null) {
            closeTransport(activeTransport)
        }
        return activeResponse?.let { UdpAuthentication.parse(it, config.clientInstanceId) }
    }

    private fun activateSession(
        activeTransport: UdpTransport,
        authenticated: UdpAuthenticatedSession,
        currentGeneration: Int
    ): Boolean {
        val session = authenticated.session
        val response = authenticated.packet
        sessionTag = session.sessionTag
        sessionUsername = response.username
        sessionSsid = authenticated.ssid
        activeTransport.receiveTimeoutMillis = RECEIVE_SOCKET_TIMEOUT_MS
        sessionMonitor.recordServerPacket()
        val activated = connectionState.dispatch(UdpConnectionEvent.Authenticated(currentGeneration)) &&
            updateStatusIfActive(currentGeneration) { current ->
                current.copy(
                    phase = RadioConnectionPhase.CONNECTED,
                    callsign = response.callsign,
                    ssid = authenticated.ssid,
                    groupId = session.txGroupId,
                    sessionId = session.sessionId,
                    clientInstanceId = session.clientInstanceId,
                    receiveGroupIds = session.rxGroupIds,
                    error = ""
                )
            }
        if (!activated) closeTransport(activeTransport)
        return activated
    }

    private fun awaitAuthResponse(activeTransport: UdpTransport): cn.silverdragon.draarl.protocol.DraarlPacket {
        val buffer = ByteArray(4_096)
        return authenticationReceiver.await {
            activeTransport.receive(buffer)?.let { length -> DraarlProtocol.decode(buffer, length) }
        }
    }

    private fun startReceiver(activeTransport: UdpTransport, currentGeneration: Int) {
        Thread({ receiveLoop(activeTransport, currentGeneration) }, "draarl-udp-receiver").start()
    }

    private fun receiveLoop(activeTransport: UdpTransport, currentGeneration: Int) {
        val buffer = ByteArray(4_096)
        while (isActive(currentGeneration) && !activeTransport.isClosed) {
            try {
                val length = activeTransport.receive(buffer) ?: continue
                val packet = DraarlProtocol.decode(buffer, length) ?: continue
                if (!isActive(currentGeneration)) return
                sessionMonitor.recordServerPacket()
                when (packet.type) {
                    DraarlProtocol.TYPE_TEXT -> handleText(
                        packet.username,
                        packet.callsign,
                        packet.ssid,
                        sourceGroupId(packet.reserved),
                        packet.data
                    )

                    DraarlProtocol.TYPE_OPUS_16K -> handleVoice(
                        packet.username,
                        packet.callsign,
                        packet.ssid,
                        sourceGroupId(packet.reserved),
                        packet.data,
                        currentGeneration
                    )
                }
            } catch (error: Exception) {
                if (isActive(currentGeneration)) {
                    scheduleReconnect(error.message ?: "UDP 接收中断", currentGeneration)
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
                timestamp = clock.nowMillis(),
                mine = false,
                groupId = groupId
            )
        )
    }

    private fun handleVoice(
        username: String,
        callsign: String,
        ssid: Int,
        groupId: Int,
        payload: ByteArray,
        currentGeneration: Int
    ) {
        if (payload.isEmpty() || status.transmitting) return
        val identity = callsign.ifBlank { username }
        val speaker = formatRadioIdentity(identity, ssid)
        val speakerKey = VoiceStreamKey(groupId, identity, ssid)
        val now = clock.nowMillis()
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
                    lastPacketAt = now
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
            audioEngine.playback.playStream(speakerKey.playbackKey, payload) { message ->
                reportNonFatal(message, currentGeneration)
            }
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
                requireActive = false
            )
            if (tailSamples.isNotEmpty() && cwPreviewActive.get()) {
                streamPcmPackets(
                    samples = tailSamples,
                    currentGeneration = currentGeneration,
                    shouldContinue = { cwPreviewActive.get() },
                    sendToRemote = false,
                    monitorLocally = true,
                    reportTransmitLevel = false,
                    requireActive = false
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
        startedAt: Long
    ) {
        try {
            streamPcmPackets(
                samples = toneSamples,
                currentGeneration = currentGeneration,
                shouldContinue = { cwTransmitActive.get() },
                sendToRemote = true,
                monitorLocally = true,
                reportTransmitLevel = true,
                requireActive = true
            )
            if (tailSamples.isNotEmpty() && cwTransmitActive.get()) {
                streamPcmPackets(
                    samples = tailSamples,
                    currentGeneration = currentGeneration,
                    shouldContinue = { cwTransmitActive.get() },
                    sendToRemote = transmitTailToneToRemoteEnabled,
                    monitorLocally = true,
                    reportTransmitLevel = true,
                    requireActive = true
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
        requireActive: Boolean
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
                audioEngine.playback.playLocal(payload) { message ->
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
                groupId = status.groupId
            )
        )
    }

    private fun startTasks(currentGeneration: Int) {
        if (!isActive(currentGeneration)) return
        sessionMonitor.start(
            heartbeat = {
                if (isActive(currentGeneration) && status.connected) {
                    send(sessionHeartbeat(), currentGeneration)
                }
            },
            watchdog = { now -> expireIncomingVoice(now, currentGeneration) },
            onServerSilence = { scheduleReconnect("服务器心跳超时", currentGeneration) }
        )
    }

    private fun expireIncomingVoice(now: Long, currentGeneration: Int) {
        val completedMessages = finishExpiredAndAdvanceVoiceQueue(now, currentGeneration)
        completedMessages.forEach(listener::onMessage)
        if (completedMessages.isNotEmpty()) {
            val activeSpeaker = activeIncomingSpeaker()
            updateStatusIfActive(currentGeneration) { it.copy(speaker = activeSpeaker) }
            if (activeSpeaker.isBlank()) playReceiveTailTone(currentGeneration)
        }
    }

    private fun stopSessionTasks() {
        sessionMonitor.stop()
        sessionTasks.cancelPttTimeout()
    }

    private fun scheduleReconnect(reason: String, expectedGeneration: Int = connectionState.generation()) {
        val reconnectGeneration = connectionState.scheduleReconnect(expectedGeneration) ?: return
        cwTransmitActive.set(false)
        cwPreviewActive.set(false)
        listener.onCwPreviewState(false)
        finishingPtt.set(false)
        listener.onTransmitLevel(0f)
        stopSessionTasks()
        audioEngine.capture.stop()
        finishAllIncomingVoices().forEach(listener::onMessage)
        closeTransport()
        if (!updateStatusIfActive(reconnectGeneration) {
                it.copy(phase = RadioConnectionPhase.RECONNECTING, error = reason, transmitting = false)
            }
        ) {
            return
        }
        val retryDelay = RadioReconnectPolicy.retryDelayMillis(
            lastPacketSentAt = sessionMonitor.lastPacketSentAt(),
            now = clock.nowMillis()
        )
        runCatching {
            sessionTasks.scheduleReconnect(
                delayMillis = retryDelay,
                shouldKeep = { connectionState.isWaitingToReconnect(reconnectGeneration) },
                reconnect = reconnect@{
                    val attempt = connectionState.startReconnect(reconnectGeneration) ?: return@reconnect
                    connectionExecutor.execute { establish(attempt) }
                }
            )
        }.getOrElse { error ->
            if (connectionState.dispatch(UdpConnectionEvent.ReconnectFailed(reconnectGeneration))) {
                updateStatusIfActive(reconnectGeneration) {
                    it.copy(phase = RadioConnectionPhase.ERROR, error = error.message ?: "无法安排自动重连")
                }
            }
            return
        }
    }

    private fun send(bytes: ByteArray, expectedGeneration: Int = connectionState.generation()): Boolean {
        if (!isActive(expectedGeneration)) return false
        val activeTransport = transport ?: return false
        if (!status.connected || activeTransport.isClosed) return false
        return runCatching {
            synchronized(sendLock) { sendRaw(activeTransport, bytes) }
            true
        }.getOrElse {
            scheduleReconnect(it.message ?: "UDP 发送失败", expectedGeneration)
            false
        }
    }

    private fun sessionHeartbeat(): ByteArray = DraarlProtocol.heartbeat(
        username = sessionUsername,
        ssid = sessionSsid,
        sessionTag = sessionTag
    )

    private fun sessionText(text: String): ByteArray = DraarlProtocol.text(
        message = text,
        username = sessionUsername,
        ssid = sessionSsid,
        sessionTag = sessionTag
    )

    private fun sessionVoice(payload: ByteArray): ByteArray = DraarlProtocol.voice(
        mergedOpusFrames = payload,
        username = sessionUsername,
        ssid = sessionSsid,
        sessionTag = sessionTag
    )

    private fun sourceGroupId(reserved: Long): Int =
        reserved.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: status.groupId

    private fun sendRaw(activeTransport: UdpTransport, bytes: ByteArray) {
        activeTransport.send(bytes)
        sessionMonitor.recordPacketSent()
    }

    private fun schedulePttTimeout(expectedGeneration: Int) {
        sessionTasks.cancelPttTimeout()
        if (!isActive(expectedGeneration) || !status.transmitting) return
        val remaining = transmitTimeoutSeconds * 1_000L -
            (clock.nowMillis() - pttStartedAt)
        sessionTasks.schedulePttTimeout(remaining.coerceAtLeast(0L)) {
            if (isActive(expectedGeneration) && status.transmitting) stopPtt()
        }
    }

    @Synchronized
    private fun closeTransport(expected: UdpTransport? = null) {
        val activeTransport = transport
        if (expected != null && activeTransport !== expected) {
            runCatching { expected.close() }
            return
        }
        activeTransport?.localPort?.takeIf { it > 0 }?.let { preferredLocalPort = it }
        transport = null
        sessionTag = 0
        sessionUsername = ""
        sessionSsid = DraarlProtocol.SSID_ANDROID
        runCatching { activeTransport?.close() }
    }

    private fun isActive(expectedGeneration: Int): Boolean = connectionState.isActive(expectedGeneration)

    private fun updateStatus(newStatus: RadioStatus) {
        status = newStatus
        listener.onStatus(newStatus)
    }

    private fun updateStatusIfActive(expectedGeneration: Int, transform: (RadioStatus) -> RadioStatus): Boolean =
        synchronized(statusLock) {
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
        audioEngine.playback.resetDecoder()
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
                requireActive = true
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
                    audioEngine.playback.playStream(nextKey.playbackKey, backlog) { message ->
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

    private fun finishAllIncomingVoices(): List<RadioMessage> = synchronized(voiceSessionLock) {
        val messages = incomingVoiceStreams.keys.toList().mapNotNull(::finishIncomingVoiceLocked)
        voicePlaybackQueue.clear()
        messages
    }

    private fun finishIncomingVoiceLocked(key: VoiceStreamKey): RadioMessage? {
        val stream = incomingVoiceStreams.remove(key) ?: return null
        voicePlaybackQueue.remove(key)
        audioEngine.playback.endStream(key.playbackKey)
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
            groupId = stream.groupId
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
            reportNonFatal(it.message ?: "语音缓存失败", connectionState.generation())
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
        val buffer: ByteArrayOutputStream = ByteArrayOutputStream()
    )

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
