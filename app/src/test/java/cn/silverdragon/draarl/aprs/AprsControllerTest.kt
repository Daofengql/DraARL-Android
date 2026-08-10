package cn.silverdragon.draarl.aprs

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
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

class AprsControllerTest {
    @Test
    fun userConfigLoadsAndSaveNormalizesPersistsAndStartsReporting() = runBlocking {
        val storage = FakeStorage(AprsConfig(callsign = "N0CALL"))
        val effects = FakeEffects()
        val controller = controller(storage = storage, effects = effects)

        controller.onUserChanged(7)
        yield()
        assertEquals("N0CALL", controller.uiState.config.callsign)

        controller.onEvent(
            AprsEvent.SaveConfig(
                AprsConfig(
                    enabled = true,
                    autoReport = true,
                    server = "  ",
                    port = 90_000,
                    callsign = " bg5dra-9 ",
                    movingIntervalSeconds = 10,
                    stationaryIntervalSeconds = 9_000
                )
            )
        )
        yield()

        val saved = storage.saved.single()
        assertEquals(7, saved.first)
        assertEquals("rotate.aprs2.net", saved.second.server)
        assertEquals(65_535, saved.second.port)
        assertEquals("BG5DRA-9", saved.second.callsign)
        assertEquals(60, saved.second.movingIntervalSeconds)
        assertEquals(3_600, saved.second.stationaryIntervalSeconds)
        assertEquals(listOf(7), effects.startedUserIds)
        assertEquals("APRS 设置已保存", effects.notices.single())
        assertFalse(controller.uiState.saving)
    }

    @Test
    fun disabledConfigurationStopsBackgroundReporting() = runBlocking {
        val effects = FakeEffects()
        val controller = controller(effects = effects)
        controller.onUserChanged(3)
        yield()

        controller.onEvent(AprsEvent.SaveConfig(AprsConfig(enabled = false, autoReport = true)))
        yield()

        assertEquals(1, effects.stopCount)
    }

    @Test
    fun backgroundStartFailureDoesNotClaimSaveSuccess() = runBlocking {
        val storage = FakeStorage()
        val effects = FakeEffects(startError = IllegalStateException("service unavailable"))
        val controller = controller(storage = storage, effects = effects)
        controller.onUserChanged(3)
        yield()

        controller.onEvent(
            AprsEvent.SaveConfig(
                AprsConfig(enabled = true, autoReport = true, callsign = "BG5DRA")
            )
        )
        yield()

        assertEquals(1, storage.saved.size)
        assertEquals("无法启动 APRS 后台上报：service unavailable", effects.notices.single())
        assertFalse(controller.uiState.saving)
    }

    @Test
    fun sendRequiresEnabledConfiguration() = runBlocking {
        val sender = FakeSender()
        val effects = FakeEffects()
        val controller = controller(sender = sender, effects = effects)

        controller.onEvent(AprsEvent.SendPosition(POSITION))

        assertTrue(sender.sent.isEmpty())
        assertEquals("请先在设置中启用 APRS", effects.notices.single())
    }

    @Test
    fun duplicateSendIsRejectedAndSuccessfulSendPublishesTimestamp() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val sender = FakeSender(gate = gate)
        val effects = FakeEffects(now = 123_456L)
        val controller = controller(sender = sender, effects = effects)
        controller.onEvent(AprsEvent.SaveConfig(AprsConfig(enabled = true, callsign = "BG5DRA")))
        yield()
        effects.notices.clear()

        controller.onEvent(AprsEvent.SendPosition(POSITION))
        assertEquals(AprsConnectionState.CONNECTING, controller.uiState.status.state)
        yield()
        assertEquals(AprsConnectionState.SENDING, controller.uiState.status.state)
        controller.onEvent(AprsEvent.SendPosition(POSITION))
        assertEquals("APRS 位置正在发送", effects.notices.single())

        gate.complete(Unit)
        yield()

        assertEquals(AprsConnectionState.SENT, controller.uiState.status.state)
        assertEquals(123_456L, controller.uiState.status.lastSentAt)
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun sendFailurePublishesErrorAndNotice() = runBlocking {
        val sender = FakeSender(error = IllegalStateException("authentication failed"))
        val effects = FakeEffects()
        val controller = controller(sender = sender, effects = effects)
        controller.onEvent(AprsEvent.SaveConfig(AprsConfig(enabled = true, callsign = "BG5DRA")))
        yield()
        effects.notices.clear()

        controller.onEvent(AprsEvent.SendPosition(POSITION))
        yield()

        assertEquals(AprsConnectionState.ERROR, controller.uiState.status.state)
        assertEquals("authentication failed", controller.uiState.status.message)
        assertEquals("authentication failed", effects.notices.single())
    }

    @Test
    fun logoutCancelsPendingSendAndClearsState() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val effects = FakeEffects()
        val controller = controller(sender = FakeSender(gate = gate), effects = effects)
        controller.onUserChanged(7)
        yield()
        controller.onEvent(AprsEvent.SaveConfig(AprsConfig(enabled = true, callsign = "BG5DRA")))
        yield()
        controller.onEvent(AprsEvent.SendPosition(POSITION))
        yield()
        val stopsBeforeLogout = effects.stopCount

        controller.onUserChanged(null)
        gate.complete(Unit)
        yield()

        assertEquals(AprsUiState(), controller.uiState)
        assertEquals(stopsBeforeLogout + 1, effects.stopCount)
    }

    @Test
    fun staleUserLoadCannotOverwriteNewUserConfiguration() = runBlocking {
        val firstLoadStarted = CountDownLatch(1)
        val releaseFirstLoad = CountDownLatch(1)
        val storage = object : AprsConfigStorage {
            override fun load(userId: Int): AprsConfig {
                if (userId == 1) {
                    firstLoadStarted.countDown()
                    check(releaseFirstLoad.await(1, TimeUnit.SECONDS))
                }
                return AprsConfig(callsign = if (userId == 1) "OLD" else "NEW")
            }

            override fun save(userId: Int, config: AprsConfig) = Unit
        }

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val controller = AprsController(
                storage = storage,
                sender = FakeSender(),
                effects = FakeEffects(),
                scope = this,
                ioDispatcher = dispatcher
            )
            controller.onUserChanged(1)
            yield()
            assertTrue(firstLoadStarted.await(1, TimeUnit.SECONDS))

            controller.onUserChanged(2)
            releaseFirstLoad.countDown()
            withTimeout(1_000) {
                while (controller.uiState.config.callsign != "NEW") yield()
            }

            assertEquals("NEW", controller.uiState.config.callsign)
        }
    }

    @Test
    fun closeDropsLateSaveResultAndBackgroundEffect() = runBlocking {
        val saveStarted = CountDownLatch(1)
        val releaseSave = CountDownLatch(1)
        val saveFinished = CountDownLatch(1)
        val storage = object : AprsConfigStorage {
            override fun load(userId: Int): AprsConfig = AprsConfig()

            override fun save(userId: Int, config: AprsConfig) {
                saveStarted.countDown()
                awaitIgnoringInterruption(releaseSave)
                saveFinished.countDown()
            }
        }
        val effects = FakeEffects()

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val controller = AprsController(
                storage = storage,
                sender = FakeSender(),
                effects = effects,
                scope = this,
                ioDispatcher = dispatcher
            )
            controller.onUserChanged(7)
            yield()
            controller.onEvent(
                AprsEvent.SaveConfig(
                    AprsConfig(enabled = true, autoReport = true, callsign = "BG5DRA")
                )
            )
            yield()
            assertTrue(saveStarted.await(1, TimeUnit.SECONDS))

            controller.close()
            releaseSave.countDown()
            assertTrue(saveFinished.await(1, TimeUnit.SECONDS))
            yield()

            assertEquals(AprsUiState(), controller.uiState)
            assertTrue(effects.startedUserIds.isEmpty())
            assertTrue(effects.notices.isEmpty())
            assertEquals(1, effects.stopCount)
        }
    }

    private fun CoroutineScope.controller(
        storage: FakeStorage = FakeStorage(),
        sender: FakeSender = FakeSender(),
        effects: FakeEffects = FakeEffects()
    ) = AprsController(
        storage = storage,
        sender = sender,
        effects = effects,
        scope = this,
        ioDispatcher = Dispatchers.Unconfined
    )

    private class FakeStorage(private val loaded: AprsConfig = AprsConfig()) : AprsConfigStorage {
        val saved = mutableListOf<Pair<Int, AprsConfig>>()

        override fun load(userId: Int): AprsConfig = loaded

        override fun save(userId: Int, config: AprsConfig) {
            saved += userId to config
        }
    }

    private class FakeSender(
        private val gate: CompletableDeferred<Unit>? = null,
        private val error: Throwable? = null
    ) : AprsPositionSender {
        val sent = mutableListOf<Pair<AprsConfig, AprsPosition>>()

        override suspend fun sendPosition(config: AprsConfig, position: AprsPosition) {
            sent += config to position
            gate?.await()
            error?.let { throw it }
        }
    }

    private class FakeEffects(private val now: Long = 0L, private val startError: Exception? = null) : AprsEffects {
        val startedUserIds = mutableListOf<Int>()
        val notices = mutableListOf<String>()
        var stopCount = 0

        override fun startBackgroundReporting(userId: Int) {
            startError?.let { throw it }
            startedUserIds += userId
        }

        override fun stopBackgroundReporting() {
            stopCount++
        }

        override fun showNotice(message: String) {
            notices += message
        }

        override fun friendlyError(error: Throwable): String = error.message.orEmpty()

        override fun currentTimeMillis(): Long = now
    }

    private companion object {
        val POSITION = AprsPosition(latitude = 30.25, longitude = 120.16)
    }
}

private fun awaitIgnoringInterruption(latch: CountDownLatch) {
    while (latch.count > 0L) {
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            // Simulates a blocking storage call that cannot be cancelled cooperatively.
        }
    }
}
