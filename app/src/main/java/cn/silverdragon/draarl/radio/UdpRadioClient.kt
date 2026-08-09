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
    private val pttCoordinator = UdpPttCoordinator(
        clock = clock,
        tasks = sessionTasks,
        audio = audioEngine,
        callbacks = object : UdpPttCallbacks {
            override fun isActive(generation: Int): Boolean = this@UdpRadioClient.isActive(generation)

            override fun isTransmitting(): Boolean = status.transmitting

            override fun beginTransmission(generation: Int): Boolean = updateStatusIfActive(generation) { current ->
                if (!current.connected) current else current.copy(transmitting = true, speaker = "")
            } && status.transmitting

            override fun sendVoice(payload: ByteArray, generation: Int): Boolean =
                send(sessionVoice(payload), generation)

            override fun streamTail(request: UdpPttTailRequest) {
                streamPcmPackets(
                    samples = request.samples,
                    currentGeneration = request.generation,
                    shouldContinue = request.shouldContinue,
                    sendToRemote = request.sendToRemote,
                    monitorLocally = true,
                    reportTransmitLevel = true,
                    requireActive = true,
                    onRemotePacket = request.recordPacket
                )
            }

            override fun completeTransmission(generation: Int, transmission: CompletedPttTransmission) =
                finishPttTransmission(generation, transmission)

            override fun reportError(message: String, generation: Int) = reportNonFatal(message, generation)

            override fun timeoutTransmission(generation: Int) = stopPtt(generation)
        },
        maxCachedVoiceBytes = MAX_CACHED_VOICE_BYTES
    )
    private val incomingVoiceAssembler = IncomingVoiceAssembler(
        maxStreams = MAX_INCOMING_VOICE_STREAMS,
        maxPayloadBytes = MAX_CACHED_VOICE_BYTES,
        endTimeoutMillis = VOICE_END_TIMEOUT_MS
    )
    private val sessionState = UdpSessionStateContext(listener::onStatus)
    private val authenticationReceiver = UdpAuthenticationReceiver(AUTH_TOTAL_TIMEOUT_MS, clock::nowMillis)
    private val cwTransmitActive = AtomicBoolean(false)
    private val cwPreviewActive = AtomicBoolean(false)
    private val sendLock = Any()
    private val cwVoiceLock = Any()

    @Volatile private var transport: UdpTransport? = null

    @Volatile private var preferredLocalPort = 0

    @Volatile private var transmitTimeoutSeconds = DEFAULT_TRANSMIT_TIMEOUT_SECONDS

    @Volatile private var transmitTailTone = TransmitTailTone.OFF

    @Volatile private var transmitTailToneToRemoteEnabled = true

    @Volatile private var receiveTailToneEnabled = false

    @Volatile private var playingMessageId: String? = null

    private val status: RadioStatus get() = sessionState.snapshot().status
    private var cwVoiceBuffer = ByteArrayOutputStream()

    @Synchronized
    fun connect(config: RadioConnectionConfig) {
        val attempt = sessionState.connect(config) ?: return
        sessionTasks.cancelReconnect()
        cwTransmitActive.set(false)
        cwPreviewActive.set(false)
        listener.onCwPreviewState(false)
        listener.onTransmitLevel(0f)
        stopSessionTasks()
        pttCoordinator.cancel()
        stopPlayback()
        finishAllIncomingVoices().forEach(listener::onMessage)
        closeTransport()
        connectionExecutor.execute { establish(attempt) }
    }

    fun updateAccessToken(token: String) {
        sessionState.dispatch(UdpConnectionEvent.AccessTokenChanged(token))
    }

    @Synchronized
    fun disconnect() {
        sessionState.dispatch(UdpConnectionEvent.Disconnect)
        sessionTasks.cancelReconnect()
        cwTransmitActive.set(false)
        cwPreviewActive.set(false)
        listener.onCwPreviewState(false)
        listener.onTransmitLevel(0f)
        stopSessionTasks()
        pttCoordinator.cancel()
        stopPlayback()
        finishAllIncomingVoices().forEach(listener::onMessage)
        closeTransport()
        sessionState.updateStatus { RadioStatus(phase = RadioConnectionPhase.DISCONNECTED) }
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
            reportNonFatal("消息过长，UTF-8 编码后不能超过 710 字节", generation())
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
                reportNonFatal(error.message ?: "CW 内容无效", generation())
                return false
            }
        val tailSamples = TransmitTailToneGenerator.generate(transmitTailTone)
        val transmitDurationMs = (tone.samples.size + tailSamples.size) * 1_000L / OpusAudioFormat.SAMPLE_RATE
        if (transmitDurationMs > transmitTimeoutSeconds * 1_000L) {
            reportNonFatal("CW 音频超过当前发射限时，请缩短内容或提高速度", generation())
            return false
        }
        if (!cwTransmitActive.compareAndSet(false, true)) return false

        stopPlayback()
        audioEngine.playback.resetDecoder()
        synchronized(cwVoiceLock) { cwVoiceBuffer = ByteArrayOutputStream() }
        val currentGeneration = generation()
        val startedAt = clock.nowMillis()
        val statusUpdated = updateStatusIfActive(currentGeneration) { current ->
            if (!current.connected) current else current.copy(transmitting = true, speaker = "", error = "")
        }
        if (!statusUpdated || !status.transmitting) {
            cwTransmitActive.set(false)
            return false
        }
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
                reportNonFatal(error.message ?: "CW 内容无效", generation())
                return false
            }
        val tailSamples = TransmitTailToneGenerator.generate(transmitTailTone)
        if (!cwPreviewActive.compareAndSet(false, true)) return false
        stopPlayback()
        audioEngine.playback.resetDecoder()
        listener.onCwPreviewState(true)
        val currentGeneration = generation()
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
        val currentGeneration = generation()
        return pttCoordinator.start(currentGeneration, transmitTimeoutSeconds * 1_000L)
    }

    @Synchronized
    fun stopPtt() {
        stopPtt(generation())
    }

    private fun stopPtt(currentGeneration: Int) {
        if (!status.transmitting || cwTransmitActive.get()) return
        pttCoordinator.stop(
            generation = currentGeneration,
            tailSamples = TransmitTailToneGenerator.generate(transmitTailTone),
            sendTailToRemote = transmitTailToneToRemoteEnabled
        )
    }

    private fun finishPttTransmission(currentGeneration: Int, transmission: CompletedPttTransmission) {
        val statusUpdated = updateStatusIfActive(currentGeneration) { it.copy(transmitting = false) }
        if (!statusUpdated) return
        val networkPayload = transmission.networkPayload
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
                    timestamp = transmission.startedAt,
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
                reportNonFatal(error, generation())
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
        if (status.transmitting && !cwTransmitActive.get()) {
            pttCoordinator.rescheduleTimeout(
                generation = generation(),
                timeoutMillis = transmitTimeoutSeconds * 1_000L
            )
        }
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
        sessionState.dispatch(UdpConnectionEvent.RoutingChanged(groupId))
        sessionState.updateStatus { it.copy(groupId = groupId, receiveGroupIds = receiveGroupIds.distinct()) }
    }

    fun snapshot(): RadioStatus = sessionState.snapshot().status

    @Synchronized
    fun release() {
        if (sessionState.snapshot().connection.stage == UdpConnectionStage.CLOSED) return
        disconnect()
        sessionState.dispatch(UdpConnectionEvent.Close)
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
            sessionState.transition(
                event = UdpConnectionEvent.AuthenticationFailed(currentGeneration),
                expectedGeneration = currentGeneration
            ) { it.copy(phase = RadioConnectionPhase.ERROR, error = error.message.orEmpty()) }
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
            pttCoordinator.cancel()
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
        val started = sessionState.transition(
            event = UdpConnectionEvent.AuthenticationStarted(currentGeneration),
            expectedGeneration = currentGeneration
        ) { it.copy(phase = RadioConnectionPhase.AUTHENTICATING) }
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
        activeTransport.receiveTimeoutMillis = RECEIVE_SOCKET_TIMEOUT_MS
        sessionMonitor.recordServerPacket()
        val activated = sessionState.transition(
            event = UdpConnectionEvent.Authenticated(currentGeneration),
            expectedGeneration = currentGeneration,
            authenticatedIdentity = UdpSessionIdentity(
                sessionTag = session.sessionTag,
                username = response.username,
                ssid = authenticated.ssid
            )
        ) { current ->
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
        val result = incomingVoiceAssembler.accept(
            IncomingVoicePacket(
                source = IncomingVoiceSource(
                    username = username,
                    callsign = callsign,
                    ssid = ssid,
                    groupId = groupId
                ),
                payload = payload,
                receivedAt = clock.nowMillis()
            )
        )
        result.actions.filterIsInstance<IncomingVoiceAction.Complete>().forEach { action ->
            listener.onMessage(completeIncomingVoice(action.voice))
        }
        if (result.startsPlaybackSession) stopPlayback()
        result.actions.filterIsInstance<IncomingVoiceAction.Play>().forEach { action ->
            playIncomingVoice(action, currentGeneration)
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
                requireActive = true,
                onRemotePacket = ::appendCwPacket
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
                    onRemotePacket = ::appendCwPacket
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
        onRemotePacket: (ByteArray) -> Unit = {}
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
                onRemotePacket(payload)
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
        val networkPayload = synchronized(cwVoiceLock) {
            cwVoiceBuffer.toByteArray().also { cwVoiceBuffer = ByteArrayOutputStream() }
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
        val result = incomingVoiceAssembler.expire(now)
        val completedMessages = mutableListOf<RadioMessage>()
        result.actions.forEach { action ->
            when (action) {
                is IncomingVoiceAction.Complete -> completedMessages += completeIncomingVoice(action.voice)
                is IncomingVoiceAction.Play -> playIncomingVoice(action, currentGeneration)
            }
        }
        completedMessages.forEach(listener::onMessage)
        if (completedMessages.isNotEmpty()) {
            val activeSpeaker = result.activeSpeaker?.let { speaker ->
                formatRadioIdentity(speaker.identity, speaker.ssid)
            }.orEmpty()
            updateStatusIfActive(currentGeneration) { it.copy(speaker = activeSpeaker) }
            if (activeSpeaker.isBlank()) playReceiveTailTone(currentGeneration)
        }
    }

    private fun stopSessionTasks() {
        sessionMonitor.stop()
    }

    private fun scheduleReconnect(reason: String, expectedGeneration: Int = generation()) {
        val reconnectGeneration = sessionState.scheduleReconnect(expectedGeneration) ?: return
        cwTransmitActive.set(false)
        cwPreviewActive.set(false)
        listener.onCwPreviewState(false)
        listener.onTransmitLevel(0f)
        stopSessionTasks()
        pttCoordinator.cancel()
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
                shouldKeep = {
                    val connection = sessionState.snapshot().connection
                    connection.generation == reconnectGeneration &&
                        connection.stage == UdpConnectionStage.RECONNECT_DELAY
                },
                reconnect = reconnect@{
                    val attempt = sessionState.startReconnect(reconnectGeneration) ?: return@reconnect
                    connectionExecutor.execute { establish(attempt) }
                }
            )
        }.getOrElse { error ->
            sessionState.transition(
                event = UdpConnectionEvent.ReconnectFailed(reconnectGeneration),
                expectedGeneration = reconnectGeneration
            ) { it.copy(phase = RadioConnectionPhase.ERROR, error = error.message ?: "无法安排自动重连") }
            return
        }
    }

    private fun send(bytes: ByteArray, expectedGeneration: Int = generation()): Boolean {
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

    private fun sessionHeartbeat(): ByteArray = sessionState.snapshot().identity.let { identity ->
        DraarlProtocol.heartbeat(
            username = identity.username,
            ssid = identity.ssid,
            sessionTag = identity.sessionTag
        )
    }

    private fun sessionText(text: String): ByteArray = sessionState.snapshot().identity.let { identity ->
        DraarlProtocol.text(
            message = text,
            username = identity.username,
            ssid = identity.ssid,
            sessionTag = identity.sessionTag
        )
    }

    private fun sessionVoice(payload: ByteArray): ByteArray = sessionState.snapshot().identity.let { identity ->
        DraarlProtocol.voice(
            mergedOpusFrames = payload,
            username = identity.username,
            ssid = identity.ssid,
            sessionTag = identity.sessionTag
        )
    }

    private fun sourceGroupId(reserved: Long): Int =
        reserved.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: status.groupId

    private fun sendRaw(activeTransport: UdpTransport, bytes: ByteArray) {
        activeTransport.send(bytes)
        sessionMonitor.recordPacketSent()
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
        sessionState.clearIdentity()
        runCatching { activeTransport?.close() }
    }

    private fun generation(): Int = sessionState.snapshot().connection.generation

    private fun isActive(expectedGeneration: Int): Boolean = sessionState.snapshot().connection.let { connection ->
        connection.generation == expectedGeneration &&
            connection.stage !in setOf(UdpConnectionStage.DISCONNECTED, UdpConnectionStage.CLOSED)
    }

    private fun updateStatusIfActive(expectedGeneration: Int, transform: (RadioStatus) -> RadioStatus): Boolean =
        sessionState.updateStatusIfActive(expectedGeneration, transform)

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
                        !incomingVoiceAssembler.hasStreams() &&
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

    private fun finishAllIncomingVoices(): List<RadioMessage> =
        incomingVoiceAssembler.finishAll().map(::completeIncomingVoice)

    private fun completeIncomingVoice(voice: CompletedIncomingVoice): RadioMessage {
        audioEngine.playback.endStream(voice.key.playbackKey)
        val endedAt = voice.lastPacketAt + VOICE_PACKET_DURATION_MS
        val messageId = UUID.randomUUID().toString()
        return RadioMessage(
            id = messageId,
            type = RadioMessageType.VOICE,
            senderCallsign = voice.source.identity,
            senderSsid = voice.source.ssid,
            senderUsername = voice.source.username,
            content = "语音",
            timestamp = voice.startedAt,
            mine = false,
            durationMs = (endedAt - voice.startedAt).coerceAtLeast(VOICE_PACKET_DURATION_MS),
            audioCacheKey = cacheNetworkRecording(messageId, voice.networkPayload),
            groupId = voice.source.groupId
        )
    }

    private fun playIncomingVoice(action: IncomingVoiceAction.Play, currentGeneration: Int) {
        updateStatusIfActive(currentGeneration) { current ->
            current.copy(speaker = formatRadioIdentity(action.speaker.identity, action.speaker.ssid))
        }
        audioEngine.playback.playStream(action.key.playbackKey, action.payload) { message ->
            reportNonFatal(message, currentGeneration)
        }
    }

    private fun appendVoiceData(buffer: ByteArrayOutputStream, payload: ByteArray) {
        val remaining = MAX_CACHED_VOICE_BYTES - buffer.size()
        if (payload.size <= remaining) buffer.write(payload)
    }

    private fun appendCwPacket(payload: ByteArray) = synchronized(cwVoiceLock) {
        appendVoiceData(cwVoiceBuffer, payload)
    }

    private fun cacheNetworkRecording(messageId: String, payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val cacheKey = "message:$messageId"
        return runCatching {
            audioCache.put(cacheKey, RawOpusRecording.fromNetworkPayload(payload))
            cacheKey
        }.onFailure {
            reportNonFatal(it.message ?: "语音缓存失败", generation())
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
