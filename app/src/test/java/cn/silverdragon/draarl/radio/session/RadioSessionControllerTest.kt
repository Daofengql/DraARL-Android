package cn.silverdragon.draarl.radio.session

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.radio.AccessPointProbe
import cn.silverdragon.draarl.radio.AccessPointSelection
import cn.silverdragon.draarl.radio.RadioConnectionConfig
import cn.silverdragon.draarl.radio.RadioServiceListener
import cn.silverdragon.draarl.radio.TransmitTailTone
import cn.silverdragon.draarl.settings.RadioAudioSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioSessionControllerTest {
    @Test
    fun accountRestoresRoutingAndPublishesMessageContext() = runBlocking {
        val storage = FakeStorage().apply {
            routings[1] = RadioSessionRoutingResult(7, setOf(7, 8))
        }
        val effects = FakeEffects()
        val fixture = fixture(storage = storage, effects = effects)

        fixture.controller.onAccountChanged(account())

        assertEquals(7, fixture.controller.uiState.selectedGroupId)
        assertEquals(setOf(7, 8), fixture.controller.uiState.receiveGroupIds)
        assertEquals(ContextChange(7, true), effects.contextChanges.last())
        assertEquals("access-token", fixture.connection.accessTokens.last())
        assertTrue(fixture.controller.uiState.autoConnectAllowed)
    }

    @Test
    fun unapprovedAccountKeepsRadioMessagesOutOfAnActiveGroup() = runBlocking {
        val effects = FakeEffects()
        val fixture = fixture(effects = effects)

        fixture.controller.onAccountChanged(account(approved = false))
        fixture.controller.connect()

        assertEquals(ContextChange(0, true), effects.contextChanges.last())
        assertEquals("账号审核通过后才能连接在线电台", effects.notices.last())
        assertTrue(fixture.connection.startedForeground == 0)
    }

    @Test
    fun discoverySelectsReachableEntryAndPersistsIt() = runBlocking {
        val first = accessPoint("first", priority = 20)
        val second = accessPoint("second", priority = 10)
        val remote = FakeRemote(accessPoints = listOf(first, second))
        val storage = FakeStorage()
        val fixture = fixture(remote = remote, storage = storage, selectedPoint = second)
        fixture.controller.onAccountChanged(account())

        fixture.controller.discoverAccessPoints()
        yield()

        assertEquals(listOf(first, second), fixture.controller.uiState.accessPoints)
        assertEquals(second, fixture.controller.uiState.selectedAccessPoint)
        assertEquals("second", storage.selectedAccessPointIds.last())
        assertFalse(fixture.controller.uiState.selectingAccessPoint)
    }

    @Test
    fun connectUsesFreshTokenInstanceIdSelectedEntryAndTransmitGroup() = runBlocking {
        val remote = FakeRemote(freshTokens = ArrayDeque(listOf("fresh-token")))
        val storage = FakeStorage(clientInstance = "client-instance")
        val fixture = fixture(remote = remote, storage = storage)
        val point = accessPoint("edge")
        fixture.controller.onAccountChanged(account())
        fixture.controller.selectAccessPoint(point)

        fixture.controller.connect()
        yield()

        assertEquals(1, fixture.connection.startedForeground)
        val config = fixture.connection.connectionConfigs.single()
        assertEquals(point, config.accessPoint)
        assertEquals("fresh-token", config.accessToken)
        assertEquals("client-instance", config.clientInstanceId)
        assertEquals(7, config.groupId)
    }

    @Test
    fun preparedConnectionWaitsUntilTheServiceBinderIsAvailable() = runBlocking {
        val connection = FakeConnection(connectAvailable = false)
        val fixture = fixture(connection = connection)
        fixture.controller.onAccountChanged(account())
        fixture.controller.selectAccessPoint(accessPoint("edge"))

        fixture.controller.connect()
        yield()
        assertTrue(connection.connectionConfigs.isEmpty())

        connection.connectAvailable = true
        connection.emitServiceConnected()
        yield()

        assertEquals("edge", connection.connectionConfigs.single().accessPoint.id)
    }

    @Test
    fun connectedRoutingUpdatePersistsAndUpdatesBoundService() = runBlocking {
        val remote = FakeRemote(routingResult = RadioSessionRoutingResult(7, setOf(7, 8)))
        val storage = FakeStorage()
        val effects = FakeEffects()
        val fixture = fixture(remote = remote, storage = storage, effects = effects)
        fixture.controller.onAccountChanged(account())
        fixture.connection.emitStatus(
            RadioStatus(
                phase = RadioConnectionPhase.CONNECTED,
                groupId = 7,
                sessionId = "radio-session",
                receiveGroupIds = listOf(7)
            )
        )
        yield()

        fixture.controller.updateRouting(7, setOf(7, 8))
        yield()

        assertEquals(RoutingRequest("radio-session", 7, setOf(7, 8)), remote.routingRequests.single())
        assertEquals(setOf(7, 8), fixture.controller.uiState.receiveGroupIds)
        assertEquals(StoredRouting(1, 7, setOf(7, 8)), storage.savedRoutings.last())
        assertEquals(7 to setOf(7, 8), fixture.connection.serviceRoutings.last())
        assertEquals("收听频道已更新", effects.notices.last())
    }

    @Test
    fun serviceReconnectReappliesLatestAudioAndOverlayConfiguration() = runBlocking {
        val fixture = fixture()
        fixture.controller.onAccountChanged(account())
        fixture.controller.onAvailableGroupsChanged(listOf(group(7, "应急通信")), preferredGroupId = 7)
        val updatedAudio = AUDIO_SETTINGS.copy(muted = true, transmitTimeoutSeconds = 45)
        fixture.controller.applyAudioSettings(updatedAudio)
        fixture.controller.configurePttOverlay(RadioPttOverlayConfig(enabled = true, visible = true))
        fixture.connection.appliedAudioSettings.clear()
        fixture.connection.overlayConfigs.clear()

        fixture.connection.emitServiceConnected()
        yield()

        assertEquals(updatedAudio, fixture.connection.appliedAudioSettings.single())
        assertEquals(
            RadioPttOverlayConfig(enabled = true, visible = true, groupName = "应急通信"),
            fixture.connection.overlayConfigs.single()
        )
    }

    @Test
    fun staleConnectionPreparationCannotConnectAfterAccountSwitch() = runBlocking {
        val firstTokenStarted = CountDownLatch(1)
        val releaseFirstToken = CountDownLatch(1)
        val tokenRequest = AtomicInteger(0)
        val remote = FakeRemote(
            freshToken = {
                if (tokenRequest.incrementAndGet() == 1) {
                    firstTokenStarted.countDown()
                    try {
                        check(releaseFirstToken.await(1, TimeUnit.SECONDS))
                    } catch (_: InterruptedException) {
                        check(releaseFirstToken.await(1, TimeUnit.SECONDS))
                    }
                    "old-token"
                } else {
                    "new-token"
                }
            }
        )
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val fixture = fixture(remote = remote, ioDispatcher = dispatcher)
            fixture.controller.onAccountChanged(account(userId = 1, key = "account-1"))
            fixture.controller.selectAccessPoint(accessPoint("old"))
            fixture.controller.connect()
            yield()
            assertTrue(firstTokenStarted.await(1, TimeUnit.SECONDS))

            fixture.controller.onAccountChanged(account(userId = 2, key = "account-2"))
            fixture.controller.selectAccessPoint(accessPoint("new"))
            fixture.controller.connect()
            releaseFirstToken.countDown()
            withTimeout(1_000) {
                while (fixture.connection.connectionConfigs.isEmpty()) yield()
            }

            assertEquals(1, fixture.connection.connectionConfigs.size)
            assertEquals("new", fixture.connection.connectionConfigs.single().accessPoint.id)
            assertEquals("new-token", fixture.connection.connectionConfigs.single().accessToken)
            fixture.controller.close()
        }
    }

    private fun CoroutineScope.fixture(
        remote: FakeRemote = FakeRemote(),
        storage: FakeStorage = FakeStorage(),
        connection: FakeConnection = FakeConnection(),
        controls: FakeControls = FakeControls(),
        effects: FakeEffects = FakeEffects(),
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
        selectedPoint: AccessPoint? = null
    ): Fixture {
        val gateway = RadioServiceGateway(connection, controls)
        val controller = RadioSessionController(
            dependencies = RadioSessionDependencies(remote, storage, gateway, effects),
            execution = RadioSessionExecution(
                scope = this,
                ioDispatcher = ioDispatcher,
                selectAccessPoint = { points ->
                    val selected = selectedPoint ?: points.first()
                    AccessPointSelection(
                        selected = selected,
                        probes = points.map { AccessPointProbe(it, if (it == selected) 20 else null) },
                        measured = true
                    )
                },
                periodicDiscoveryIntervalMs = 0
            ),
            initialAudioSettings = AUDIO_SETTINGS
        )
        return Fixture(controller, connection, controls, effects)
    }

    private data class Fixture(
        val controller: RadioSessionController,
        val connection: FakeConnection,
        val controls: FakeControls,
        val effects: FakeEffects
    )

    private class FakeRemote(
        private val accessPoints: List<AccessPoint> = emptyList(),
        private val freshTokens: ArrayDeque<String> = ArrayDeque(),
        private val routingResult: RadioSessionRoutingResult = RadioSessionRoutingResult(7, setOf(7)),
        private val freshToken: (() -> String)? = null
    ) : RadioSessionRemoteDataSource {
        val routingRequests = mutableListOf<RoutingRequest>()

        override fun loadAccessPoints(): List<AccessPoint> = accessPoints

        override fun freshAccessToken(): String = freshToken?.invoke() ?: freshTokens.removeFirstOrNull() ?: "token"

        override fun renewAccessToken(): String = "renewed-token"

        override fun updateRouting(
            sessionId: String,
            txGroupId: Int,
            rxGroupIds: Collection<Int>
        ): RadioSessionRoutingResult {
            routingRequests += RoutingRequest(sessionId, txGroupId, rxGroupIds.toSet())
            return routingResult
        }
    }

    private class FakeStorage(private val clientInstance: String = "instance") : RadioSessionStorage {
        val routings = mutableMapOf<Int, RadioSessionRoutingResult>()
        val savedRoutings = mutableListOf<StoredRouting>()
        val selectedAccessPointIds = mutableListOf<String>()

        override fun clientInstanceId(): String = clientInstance

        override fun loadRouting(userId: Int, fallbackGroupId: Int): RadioSessionRoutingResult =
            routings[userId] ?: RadioSessionRoutingResult(fallbackGroupId, setOf(fallbackGroupId))

        override fun saveRouting(userId: Int, txGroupId: Int, rxGroupIds: Collection<Int>) {
            savedRoutings += StoredRouting(userId, txGroupId, rxGroupIds.toSet())
        }

        override fun saveSelectedAccessPoint(id: String) {
            selectedAccessPointIds += id
        }
    }

    private class FakeConnection(var connectAvailable: Boolean = true) : RadioServiceConnectionGateway {
        private var listener: RadioServiceListener? = null
        private var observer: RadioServiceConnectionObserver? = null
        val accessTokens = mutableListOf<String>()
        val connectionConfigs = mutableListOf<RadioConnectionConfig>()
        val serviceRoutings = mutableListOf<Pair<Int, Set<Int>>>()
        val appliedAudioSettings = mutableListOf<RadioAudioSettings>()
        val overlayConfigs = mutableListOf<RadioPttOverlayConfig>()
        var startedForeground = 0

        override fun setCallbacks(listener: RadioServiceListener, observer: RadioServiceConnectionObserver) {
            this.listener = listener
            this.observer = observer
        }

        override fun bind(): Boolean = true

        override fun startForeground() {
            startedForeground++
        }

        override fun stopStartedService() = Unit

        override fun connect(config: RadioConnectionConfig): Boolean {
            if (connectAvailable) connectionConfigs += config
            return connectAvailable
        }

        override fun disconnect() = Unit

        override fun setRouting(groupId: Int, receiveGroupIds: Collection<Int>) {
            serviceRoutings += groupId to receiveGroupIds.toSet()
        }

        override fun updateAccessToken(token: String) {
            accessTokens += token
        }

        override fun applyAudioSettings(settings: RadioAudioSettings) {
            appliedAudioSettings += settings
        }

        override fun configurePttOverlay(config: RadioPttOverlayConfig): Boolean {
            overlayConfigs += config
            return true
        }

        override fun close() = Unit

        fun emitServiceConnected() = observer?.onServiceConnected()

        fun emitStatus(status: RadioStatus) = listener?.onRadioStatus(status)
    }

    private class FakeControls : RadioServiceControls {
        override fun sendText(text: String): Boolean = true
        override fun sendCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean = true
        override fun stopCw(): Boolean = true
        override fun previewCw(text: String, wordsPerMinute: Int, toneHz: Int): Boolean = true
        override fun stopCwPreview(): Boolean = true
        override fun startPtt(): Boolean = true
        override fun stopPtt() = Unit
        override fun togglePlayback(message: RadioMessage): Boolean = true
        override fun stopPlayback() = Unit
        override fun hasAudioCacheKey(key: String): Boolean = false
        override fun clearAudioCache(): Boolean = true
    }

    private class FakeEffects : RadioSessionEffects {
        val contextChanges = mutableListOf<ContextChange>()
        val notices = mutableListOf<String>()

        override fun onContextChanged(groupId: Int, selectionChanged: Boolean) {
            contextChanges += ContextChange(groupId, selectionChanged)
        }

        override fun onStatusChanged(previous: RadioStatus, current: RadioStatus) = Unit
        override fun onRadioMessage(message: RadioMessage) = Unit
        override fun onPlaybackState(messageId: String?) = Unit
        override fun onPlaybackLevel(level: Float) = Unit
        override fun onTransmitLevel(level: Float) = Unit
        override fun onCwPreviewState(active: Boolean) = Unit

        override fun showNotice(message: String) {
            notices += message
        }
    }

    private data class ContextChange(val groupId: Int, val selectionChanged: Boolean)
    private data class RoutingRequest(val sessionId: String, val txGroupId: Int, val rxGroupIds: Set<Int>)
    private data class StoredRouting(val userId: Int, val txGroupId: Int, val rxGroupIds: Set<Int>)

    private companion object {
        val AUDIO_SETTINGS = RadioAudioSettings(
            muted = false,
            playbackDenoiseEnabled = true,
            playbackDenoiseStrengthPercent = 50,
            transmitTimeoutSeconds = 120,
            transmitTailTone = TransmitTailTone.OFF,
            transmitTailToneToRemoteEnabled = true,
            receiveTailToneEnabled = false
        )

        fun account(userId: Int = 1, key: String = "account", approved: Boolean = true) = RadioSessionAccount(
            key = key,
            userId = userId,
            approved = approved,
            baseUrl = "https://example.com",
            accessToken = "access-token",
            defaultGroupId = 7
        )

        fun accessPoint(id: String, priority: Int = 100) = AccessPoint(
            id = id,
            displayName = id,
            host = "$id.example.com",
            port = 60_050,
            priority = priority
        )

        fun group(id: Int, name: String) = Group(id = id, name = name, type = 1, status = 1)
    }
}
