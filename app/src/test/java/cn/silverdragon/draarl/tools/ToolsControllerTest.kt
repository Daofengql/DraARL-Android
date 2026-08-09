package cn.silverdragon.draarl.tools

import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.network.ToolsApi
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsControllerTest {
    @Test
    fun relayLogbookAndPresetRequestsUseIndependentTaskSlots() = runBlocking {
        val relayStarted = CountDownLatch(1)
        val logbookStarted = CountDownLatch(1)
        val presetStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = fixture(
            scope = this,
            api = FakeToolsApi(
                relayAction = {
                    relayStarted.countDown()
                    awaitIgnoringInterruption(release)
                    emptyList()
                },
                logbookAction = {
                    logbookStarted.countDown()
                    awaitIgnoringInterruption(release)
                    LogbookPage(emptyList(), 0, 1, 20)
                },
                presetAction = {
                    presetStarted.countDown()
                    awaitIgnoringInterruption(release)
                    emptyList()
                }
            )
        )
        try {
            fixture.controller.searchRelays("广东 深圳")
            fixture.controller.loadLogbooks(reset = true)
            fixture.controller.loadPresets()
            awaitCondition {
                relayStarted.count == 0L && logbookStarted.count == 0L && presetStarted.count == 0L
            }

            assertTrue(fixture.controller.relayBusy)
            assertTrue(fixture.controller.logbookBusy)
            assertTrue(fixture.controller.presetBusy)

            release.countDown()
            awaitCondition {
                !fixture.controller.relayBusy && !fixture.controller.logbookBusy && !fixture.controller.presetBusy
            }
        } finally {
            release.countDown()
            fixture.close()
        }
    }

    @Test
    fun resetDropsLateRelayResultAndRestoresIdleState() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val cachedRelay = relay("cached")
        val fixture = fixture(
            scope = this,
            api = FakeToolsApi(
                relayAction = {
                    started.countDown()
                    awaitIgnoringInterruption(release)
                    finished.countDown()
                    listOf(relay("remote"))
                }
            ),
            cache = FakeToolCache(cachedRelays = CachedRelays("广东 深圳", listOf(cachedRelay), 100L))
        )
        try {
            awaitCondition { fixture.controller.relays == listOf(cachedRelay) }
            assertEquals(listOf(cachedRelay), fixture.controller.relays)

            fixture.controller.searchRelays("广东 深圳")
            awaitCondition { started.count == 0L }
            fixture.controller.reset()
            release.countDown()
            awaitCondition { finished.count == 0L }
            yield()

            assertFalse(fixture.controller.relayBusy)
            assertEquals(listOf(cachedRelay), fixture.controller.relays)
            assertEquals("", fixture.controller.error)
        } finally {
            release.countDown()
            fixture.close()
        }
    }

    @Test
    fun draftCacheWritesStaySerializedAndKeepLatestValueLast() = runBlocking {
        val firstWriteStarted = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val cache = FakeToolCache(
            beforeFirstDraftSave = {
                firstWriteStarted.countDown()
                awaitIgnoringInterruption(releaseFirstWrite)
            }
        )
        val fixture = fixture(this, FakeToolsApi(), cache)
        try {
            fixture.controller.editDraft(entry = null, user = User(id = 7, username = "operator"))
            awaitCondition { firstWriteStarted.count == 0L }
            fixture.controller.updateDraft(LogbookDraft(notes = "first"))
            fixture.controller.updateDraft(LogbookDraft(notes = "latest"))
            releaseFirstWrite.countDown()
            awaitCondition { cache.savedDrafts.size == 3 }

            assertEquals(listOf("", "first", "latest"), cache.savedDrafts.map { it.second.notes })
            assertEquals("latest", fixture.controller.draft?.notes)
        } finally {
            releaseFirstWrite.countDown()
            fixture.close()
        }
    }

    private fun fixture(scope: CoroutineScope, api: FakeToolsApi, cache: FakeToolCache = FakeToolCache()): Fixture {
        val dispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
        return Fixture(
            controller = ToolsController(api, cache, scope, dispatcher),
            dispatcher = dispatcher
        )
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(1_000) {
            while (!condition()) yield()
        }
    }
}

private data class Fixture(val controller: ToolsController, val dispatcher: ExecutorCoroutineDispatcher) {
    fun close() {
        controller.close()
        dispatcher.close()
    }
}

private class FakeToolsApi(
    private val relayAction: (String) -> List<RelayStation> = { emptyList() },
    private val logbookAction: (Int) -> LogbookPage = { LogbookPage(emptyList(), 0, it, 20) },
    private val presetAction: () -> List<RadioPreset> = { emptyList() }
) : ToolsApi {
    override fun searchPublicRelays(location: String): List<RelayStation> = relayAction(location)

    override fun getLogbooks(page: Int, pageSize: Int, callsign: String): LogbookPage = logbookAction(page)

    override fun saveLogbook(entry: LogbookEntry): LogbookEntry = entry

    override fun deleteLogbook(id: Int) = Unit

    override fun deleteLogbooks(ids: Collection<Int>) = Unit

    override fun getRadioPresets(): List<RadioPreset> = presetAction()

    override fun saveRadioPreset(preset: RadioPreset): RadioPreset = preset

    override fun deleteRadioPreset(id: Int) = Unit

    override fun reorderRadioPresets(orders: List<Pair<Int, Int>>) = Unit
}

private class FakeToolCache(
    private val cachedRelays: CachedRelays? = null,
    private val beforeFirstDraftSave: () -> Unit = {}
) : ToolCache {
    val savedDrafts: MutableList<Pair<Int, LogbookDraft>> = Collections.synchronizedList(mutableListOf())

    override fun saveRelays(location: String, relays: List<RelayStation>, savedAt: Long) = Unit

    override fun loadRelays(): CachedRelays? = cachedRelays

    override fun saveDraft(userId: Int, draft: LogbookDraft) {
        if (savedDrafts.isEmpty()) beforeFirstDraftSave()
        savedDrafts += userId to draft
    }

    override fun loadDraft(userId: Int): LogbookDraft? = null

    override fun clearDraft(userId: Int) = Unit
}

private fun relay(name: String) = RelayStation(
    id = name.hashCode(),
    name = name,
    uplinkFrequency = "145.000",
    downlinkFrequency = "145.600",
    transmitTone = "",
    receiveTone = "",
    ownerCallsign = "BH1ABC",
    location = "广东 深圳",
    status = 1,
    note = ""
)

private fun awaitIgnoringInterruption(latch: CountDownLatch) {
    while (latch.count > 0L) {
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            // Simulates a blocking dependency that cannot be cancelled cooperatively.
        }
    }
}
