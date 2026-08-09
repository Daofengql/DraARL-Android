package cn.silverdragon.draarl.radio

import java.io.ByteArrayOutputStream

internal data class IncomingVoiceSource(val username: String, val callsign: String, val ssid: Int, val groupId: Int) {
    val identity: String get() = callsign.ifBlank { username }
    val speaker: IncomingVoiceSpeaker get() = IncomingVoiceSpeaker(identity, ssid)
    val key: VoiceStreamKey get() = VoiceStreamKey(groupId, identity.trim().lowercase(), ssid)
}

internal data class IncomingVoicePacket(val source: IncomingVoiceSource, val payload: ByteArray, val receivedAt: Long)

internal data class IncomingVoiceSpeaker(val identity: String, val ssid: Int)

internal data class CompletedIncomingVoice(
    val key: VoiceStreamKey,
    val source: IncomingVoiceSource,
    val startedAt: Long,
    val lastPacketAt: Long,
    val networkPayload: ByteArray
)

internal sealed interface IncomingVoiceAction {
    data class Play(val key: VoiceStreamKey, val speaker: IncomingVoiceSpeaker, val payload: ByteArray) :
        IncomingVoiceAction

    data class Complete(val voice: CompletedIncomingVoice) : IncomingVoiceAction
}

internal data class IncomingVoicePacketResult(
    val actions: List<IncomingVoiceAction>,
    val startsPlaybackSession: Boolean
)

internal data class IncomingVoiceTickResult(
    val actions: List<IncomingVoiceAction>,
    val activeSpeaker: IncomingVoiceSpeaker?
)

internal class IncomingVoiceAssembler(
    private val maxStreams: Int,
    private val maxPayloadBytes: Int,
    private val endTimeoutMillis: Long
) {
    private val streams = LinkedHashMap<VoiceStreamKey, VoiceStream>()
    private val playbackQueue = VoiceStreamPlaybackQueue()

    init {
        require(maxStreams > 0) { "Incoming voice stream limit must be positive" }
        require(maxPayloadBytes > 0) { "Incoming voice payload limit must be positive" }
        require(endTimeoutMillis > 0L) { "Incoming voice timeout must be positive" }
    }

    @Synchronized
    fun accept(packet: IncomingVoicePacket): IncomingVoicePacketResult {
        require(packet.payload.isNotEmpty()) { "Incoming voice payload must not be empty" }
        val actions = mutableListOf<IncomingVoiceAction>()
        val key = packet.source.key
        var stream = streams[key]
        var startsPlaybackSession = false
        if (stream == null) {
            if (streams.size >= maxStreams) {
                val oldest = playbackQueue.evictOldestPending()
                    ?: streams.minByOrNull { it.value.lastPacketAt }?.key
                oldest?.let(::completeLocked)?.let { actions += IncomingVoiceAction.Complete(it) }
            }
            stream = VoiceStream(
                source = packet.source,
                startedAt = packet.receivedAt,
                lastPacketAt = packet.receivedAt
            )
            streams[key] = stream
            startsPlaybackSession = playbackQueue.active == null
            playbackQueue.onStream(key)
        }
        stream.lastPacketAt = maxOf(stream.lastPacketAt, packet.receivedAt)
        stream.append(packet.payload, maxPayloadBytes)
        if (playbackQueue.active == key) {
            actions += IncomingVoiceAction.Play(
                key = key,
                speaker = packet.source.speaker,
                payload = packet.payload
            )
        }
        return IncomingVoicePacketResult(actions, startsPlaybackSession)
    }

    @Synchronized
    fun expire(now: Long): IncomingVoiceTickResult {
        val actions = mutableListOf<IncomingVoiceAction>()
        val activeKey = playbackQueue.active
        val active = activeKey?.let(streams::get)
        if (activeKey != null && (active == null || isExpired(active, now))) {
            completeLocked(activeKey)?.let { actions += IncomingVoiceAction.Complete(it) }
        }

        var shouldAdvance = true
        while (shouldAdvance) {
            val nextKey = playbackQueue.advance()
            val next = nextKey?.let(streams::get)
            if (nextKey == null) {
                shouldAdvance = false
            } else if (next == null) {
                playbackQueue.remove(nextKey)
            } else {
                val backlog = next.buffer.toByteArray()
                if (backlog.isNotEmpty()) {
                    actions += IncomingVoiceAction.Play(
                        key = nextKey,
                        speaker = next.speaker,
                        payload = backlog
                    )
                }
                if (isExpired(next, now)) {
                    completeLocked(nextKey)?.let { actions += IncomingVoiceAction.Complete(it) }
                } else {
                    shouldAdvance = false
                }
            }
        }
        return IncomingVoiceTickResult(actions, activeSpeakerLocked())
    }

    @Synchronized
    fun finishAll(): List<CompletedIncomingVoice> {
        val completed = streams.keys.toList().mapNotNull(::completeLocked)
        playbackQueue.clear()
        return completed
    }

    @Synchronized
    fun hasStreams(): Boolean = streams.isNotEmpty()

    @Synchronized
    fun activeSpeaker(): IncomingVoiceSpeaker? = activeSpeakerLocked()

    private fun isExpired(stream: VoiceStream, now: Long): Boolean = now - stream.lastPacketAt > endTimeoutMillis

    private fun completeLocked(key: VoiceStreamKey): CompletedIncomingVoice? {
        val stream = streams.remove(key) ?: return null
        playbackQueue.remove(key)
        return CompletedIncomingVoice(
            key = key,
            source = stream.source,
            startedAt = stream.startedAt,
            lastPacketAt = stream.lastPacketAt,
            networkPayload = stream.buffer.toByteArray()
        )
    }

    private fun activeSpeakerLocked(): IncomingVoiceSpeaker? = playbackQueue.active?.let(streams::get)?.speaker

    private data class VoiceStream(
        val source: IncomingVoiceSource,
        val startedAt: Long,
        var lastPacketAt: Long,
        val buffer: ByteArrayOutputStream = ByteArrayOutputStream()
    ) {
        val speaker: IncomingVoiceSpeaker
            get() = source.speaker

        fun append(payload: ByteArray, maxPayloadBytes: Int) {
            val remaining = maxPayloadBytes - buffer.size()
            if (payload.size <= remaining) buffer.write(payload)
        }
    }
}
