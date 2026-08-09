package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.protocol.DraarlProtocol
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpRadioClientPttTest {
    @Test
    fun `incoming voice plays live and settles when session disconnects`() {
        val fixture = fixture()
        try {
            fixture.connect()
            val payload = DraarlProtocol.mergeOpusFrames(List(2) { byteArrayOf(7, 8, 9) })

            fixture.transport.enqueue(voicePacket(payload))
            assertTrue(fixture.audio.streamPlayed.await(2, TimeUnit.SECONDS))
            fixture.client.disconnect()
            assertTrue(fixture.listener.messageReceived.await(2, TimeUnit.SECONDS))

            assertArrayEquals(payload, fixture.audio.streamed.single().second)
            val message = fixture.listener.messages.single()
            assertEquals("BG0REMOTE", message.senderCallsign)
            assertEquals(102, message.senderSsid)
            assertEquals(7, message.groupId)
            assertEquals(120L, message.durationMs)
            assertNotNull(fixture.store.entries[message.audioCacheKey])
            assertEquals(listOf(VoiceStreamKey(7, "BG0REMOTE", 102).playbackKey), fixture.audio.endedStreams)
        } finally {
            fixture.client.release()
        }
    }

    @Test
    fun `ptt sends captured opus and stores completed local message`() {
        val fixture = fixture()
        try {
            fixture.connect()
            assertTrue(fixture.client.startPtt())
            val payload = DraarlProtocol.mergeOpusFrames(List(6) { byteArrayOf(1, 2, 3) })

            fixture.audio.emitPacket(payload)
            fixture.client.stopPtt()

            val voicePacket = fixture.transport.sent
                .mapNotNull { DraarlProtocol.decode(it) }
                .last { it.type == DraarlProtocol.TYPE_OPUS_16K }
            assertTrue(payload.contentEquals(voicePacket.data))
            val message = fixture.listener.messages.single { it.type == RadioMessageType.VOICE }
            assertEquals(FIXED_NOW, message.timestamp)
            assertEquals(360L, message.durationMs)
            assertNotNull(fixture.store.entries[message.audioCacheKey])
            assertFalse(fixture.client.snapshot().transmitting)
        } finally {
            fixture.client.release()
        }
    }

    @Test
    fun `capture rejection leaves connected session idle`() {
        val fixture = fixture(captureStarts = false)
        try {
            fixture.connect()

            assertFalse(fixture.client.startPtt())

            assertEquals(RadioConnectionPhase.CONNECTED, fixture.client.snapshot().phase)
            assertFalse(fixture.client.snapshot().transmitting)
            assertEquals(0, fixture.scheduler.oneShotTasks)
        } finally {
            fixture.client.release()
        }
    }

    @Test
    fun `capture error is reported only while session is active`() {
        val fixture = fixture()
        try {
            fixture.connect()
            assertTrue(fixture.client.startPtt())

            fixture.audio.emitError("麦克风读取失败")

            assertEquals("麦克风读取失败", fixture.client.snapshot().error)
            fixture.client.disconnect()
            fixture.audio.emitError("陈旧错误")
            assertEquals("", fixture.client.snapshot().error)
        } finally {
            fixture.client.release()
        }
    }

    @Test
    fun `release closes transport scheduler and audio exactly once`() {
        val fixture = fixture()
        fixture.connect()

        fixture.client.release()
        fixture.client.release()

        assertTrue(fixture.transport.isClosed)
        assertTrue(fixture.scheduler.closed)
        assertEquals(1, fixture.audio.releaseCount)
    }

    private fun fixture(captureStarts: Boolean = true): Fixture {
        val listener = RecordingListener()
        val store = FakeAudioStore()
        val audio = FakeAudioEngine(captureStarts)
        val transport = FakeUdpTransport(authResponse())
        val scheduler = FakeRadioScheduler()
        val client = UdpRadioClient(
            listener = listener,
            audioRuntime = RadioAudioRuntime(store, audio),
            transportFactory = UdpTransportFactory { _, _, _, _ -> transport },
            clock = RadioClock { FIXED_NOW },
            scheduler = scheduler
        )
        return Fixture(client, listener, store, audio, transport, scheduler)
    }

    private fun Fixture.connect() {
        client.connect(
            RadioConnectionConfig(
                accessPoint = AccessPoint("edge", "edge", "127.0.0.1", 60_050),
                accessToken = "token",
                clientInstanceId = CLIENT_INSTANCE_ID,
                groupId = 7
            )
        )
        assertTrue(listener.connected.await(2, TimeUnit.SECONDS))
    }

    private fun authResponse(): ByteArray {
        val metadata = JSONObject()
            .put("version", 1)
            .put("session_id", "session-1")
            .put("session_tag", SESSION_TAG)
            .put("client_instance_id", CLIENT_INSTANCE_ID)
            .put("tx_group_id", 7)
            .put("rx_group_ids", JSONArray(listOf(7)))
            .toString()
            .toByteArray()
        return DraarlProtocol.encode(
            type = DraarlProtocol.TYPE_JWT_AUTH,
            data = byteArrayOf(0) + metadata,
            username = "operator",
            ssid = 101,
            callsign = "BG0ABC",
            reserved = SESSION_TAG
        )
    }

    private fun voicePacket(payload: ByteArray): ByteArray = DraarlProtocol.encode(
        type = DraarlProtocol.TYPE_OPUS_16K,
        data = payload,
        username = "remote",
        ssid = 102,
        callsign = "BG0REMOTE",
        reserved = 7
    )

    private data class Fixture(
        val client: UdpRadioClient,
        val listener: RecordingListener,
        val store: FakeAudioStore,
        val audio: FakeAudioEngine,
        val transport: FakeUdpTransport,
        val scheduler: FakeRadioScheduler
    )

    private class RecordingListener : UdpRadioListener {
        val connected = CountDownLatch(1)
        val messageReceived = CountDownLatch(1)
        val messages = CopyOnWriteArrayList<RadioMessage>()

        override fun onStatus(status: RadioStatus) {
            if (status.connected) connected.countDown()
        }

        override fun onMessage(message: RadioMessage) {
            messages += message
            messageReceived.countDown()
        }

        override fun onPlaybackState(messageId: String?) = Unit
        override fun onPlaybackLevel(level: Float) = Unit
        override fun onTransmitLevel(level: Float) = Unit
        override fun onCwPreviewState(active: Boolean) = Unit
    }

    private class FakeAudioStore : RadioAudioStore {
        val entries = ConcurrentHashMap<String, ByteArray>()

        override fun get(key: String): ByteArray? = entries[key]

        override fun put(key: String, bytes: ByteArray) {
            entries[key] = bytes
        }

        override fun contains(key: String): Boolean = entries.containsKey(key)

        override fun clear() = entries.clear()

        override fun sizeBytes(): Long = entries.values.sumOf(ByteArray::size).toLong()
    }

    private class FakeAudioEngine(private val captureStarts: Boolean) : RadioAudioEngine {
        private var packetCallback: ((ByteArray) -> Unit)? = null
        private var errorCallback: ((String) -> Unit)? = null
        var releaseCount = 0
        val streamPlayed = CountDownLatch(1)
        val streamed = CopyOnWriteArrayList<Pair<String, ByteArray>>()
        val endedStreams = CopyOnWriteArrayList<String>()

        override val capture = object : RadioAudioCapture {
            override fun start(onPacket: (ByteArray) -> Unit, onError: (String) -> Unit): Boolean {
                packetCallback = onPacket
                errorCallback = onError
                return captureStarts
            }

            override fun stop() = Unit
        }

        override val playback = object : RadioAudioPlayback {
            override fun playLocal(mergedFrames: ByteArray, onError: (String) -> Unit) = Unit
            override fun playStream(streamKey: String, mergedFrames: ByteArray, onError: (String) -> Unit) {
                streamed += streamKey to mergedFrames.copyOf()
                streamPlayed.countDown()
            }
            override fun endStream(streamKey: String) {
                endedStreams += streamKey
            }
            override fun playRecording(
                audioCacheKey: String,
                audioUrl: String,
                onFinished: () -> Unit,
                onError: (String) -> Unit
            ): Boolean = false
            override fun stopRecording() = Unit
            override fun setMuted(value: Boolean) = Unit
            override fun setDenoiseEnabled(value: Boolean) = Unit
            override fun setDenoiseWetMix(value: Float) = Unit
            override fun resetDecoder() = Unit
        }

        override fun release() {
            releaseCount++
        }

        fun emitPacket(payload: ByteArray) = requireNotNull(packetCallback)(payload)

        fun emitError(message: String) = requireNotNull(errorCallback)(message)
    }

    private class FakeUdpTransport(private val authentication: ByteArray) : UdpTransport {
        private val authenticationPending = AtomicBoolean(true)
        private val incoming = LinkedBlockingQueue<ByteArray>()
        val sent = CopyOnWriteArrayList<ByteArray>()

        @Volatile private var closed = false

        override val localPort: Int = 12_345
        override val isClosed: Boolean get() = closed
        override var receiveTimeoutMillis: Int = 1_000

        override fun send(payload: ByteArray) {
            sent += payload.copyOf()
        }

        override fun receive(buffer: ByteArray): Int? {
            if (authenticationPending.compareAndSet(true, false)) {
                authentication.copyInto(buffer)
                return authentication.size
            }
            while (!closed) {
                val payload = incoming.poll(100, TimeUnit.MILLISECONDS) ?: continue
                payload.copyInto(buffer)
                return payload.size
            }
            return null
        }

        fun enqueue(payload: ByteArray) {
            incoming += payload
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeRadioScheduler : RadioScheduler {
        var oneShotTasks = 0
        var closed = false

        override fun execute(task: () -> Unit) = task()

        override fun schedule(delayMillis: Long, task: () -> Unit): RadioScheduledTask {
            oneShotTasks++
            return RadioScheduledTask {}
        }

        override fun scheduleWithFixedDelay(
            initialDelayMillis: Long,
            delayMillis: Long,
            task: () -> Unit
        ): RadioScheduledTask = RadioScheduledTask {}

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val FIXED_NOW = 1_000_000L
        const val SESSION_TAG = 42L
        const val CLIENT_INSTANCE_ID = "client-instance"
    }
}
