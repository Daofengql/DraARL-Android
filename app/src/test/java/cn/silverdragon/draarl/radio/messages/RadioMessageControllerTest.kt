package cn.silverdragon.draarl.radio.messages

import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageReconciler
import cn.silverdragon.draarl.data.RadioMessageSyncState
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.User
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

class RadioMessageControllerTest {
    @Test
    fun selectingGroupLoadsItsCachedMessages() = runBlocking {
        val cached = message(id = "cached", groupId = 7)
        val cache = FakeCache().apply { seed(ACCOUNT.key, 7, listOf(cached)) }
        val controller = controller(cache = cache)

        controller.onContextChanged(ACCOUNT, 7)
        yield()

        assertEquals(listOf(cached), controller.uiState.messages)
        assertTrue(controller.uiState.historyHasMore)
    }

    @Test
    fun refreshReconcilesLatestPageAndPreloadsProfiles() = runBlocking {
        val confirmed = message(
            id = "server-9",
            username = "bob",
            groupId = 7,
            serverRecordId = 9,
            syncState = RadioMessageSyncState.CONFIRMED
        )
        val remote = FakeRemote(
            pages = mutableMapOf("" to RadioMessagePage(listOf(confirmed), "older", true)),
            profiles = mapOf("bob" to User(id = 2, username = "bob", nickname = "Bob"))
        )
        val cache = FakeCache()
        val controller = controller(remote = remote, cache = cache)
        controller.onContextChanged(ACCOUNT, 7)

        controller.onEvent(RadioMessageEvent.Refresh)
        withTimeout(1_000) {
            while (controller.uiState.publicProfiles["bob"] == null) yield()
        }

        assertEquals(listOf(confirmed.copy(played = true)), controller.uiState.messages)
        assertEquals("Bob", controller.uiState.publicProfiles["bob"]?.nickname)
        assertEquals(listOf("bob"), remote.profileRequests)
        assertEquals(9_999_000L..6_820_000L, cache.reconciliations.single().authoritativeWindow)
        assertTrue(controller.uiState.historyHasMore)
        assertTrue(controller.uiState.syncError.isBlank())
    }

    @Test
    fun refreshFailurePublishesSyncErrorWithoutDroppingCache() = runBlocking {
        val cached = message(id = "cached", groupId = 7)
        val cache = FakeCache().apply { seed(ACCOUNT.key, 7, listOf(cached)) }
        val remote = FakeRemote(error = IllegalStateException("network unavailable"))
        val controller = controller(remote = remote, cache = cache)
        controller.onContextChanged(ACCOUNT, 7)
        yield()

        controller.onEvent(RadioMessageEvent.Refresh)
        yield()

        assertEquals(listOf(cached), controller.uiState.messages)
        assertEquals("network unavailable", controller.uiState.syncError)
    }

    @Test
    fun onlyFirstServerSnapshotIsTreatedAsReadHistory() = runBlocking {
        val history = message(
            id = "server-1",
            groupId = 7,
            serverRecordId = 1,
            syncState = RadioMessageSyncState.CONFIRMED
        )
        val incoming = message(
            id = "server-2",
            groupId = 7,
            timestamp = NOW + 1,
            serverRecordId = 2,
            syncState = RadioMessageSyncState.CONFIRMED
        )
        val pages = mutableMapOf("" to RadioMessagePage(listOf(history), "", false))
        val remote = FakeRemote(pages = pages)
        val controller = controller(remote = remote)
        controller.onContextChanged(ACCOUNT, 7)

        controller.onEvent(RadioMessageEvent.Refresh)
        yield()
        assertTrue(controller.uiState.messages.single().played)
        assertEquals(0, controller.uiState.unplayedVoiceCount)

        pages[""] = RadioMessagePage(listOf(history, incoming), "", false)
        controller.onEvent(RadioMessageEvent.Refresh)
        yield()

        assertTrue(controller.uiState.messages.first { it.id == history.id }.played)
        assertFalse(controller.uiState.messages.first { it.id == incoming.id }.played)
        assertEquals(1, controller.uiState.unplayedVoiceCount)
    }

    @Test
    fun olderHistoryUsesCursorAndExpandsVisibleWindow() = runBlocking {
        val latest = (1..200).map { index ->
            message(
                id = "latest-$index",
                timestamp = 10_000L + index,
                groupId = 7,
                serverRecordId = index,
                syncState = RadioMessageSyncState.CONFIRMED
            )
        }
        val older = message(
            id = "older",
            timestamp = 1_000L,
            groupId = 7,
            serverRecordId = 500,
            syncState = RadioMessageSyncState.CONFIRMED
        )
        val cache = FakeCache().apply { seed(ACCOUNT.key, 7, latest) }
        val remote = FakeRemote(
            pages = mutableMapOf(
                "" to RadioMessagePage(latest, "cursor-1", true),
                "cursor-1" to RadioMessagePage(listOf(older), "", false)
            )
        )
        val controller = controller(remote = remote, cache = cache)
        controller.onContextChanged(ACCOUNT, 7)
        controller.onEvent(RadioMessageEvent.Refresh)
        yield()

        controller.onEvent(RadioMessageEvent.LoadOlder)
        yield()

        assertEquals(listOf("", "cursor-1"), remote.pageRequests)
        assertEquals(201, controller.uiState.messages.size)
        assertEquals("older", controller.uiState.messages.first().id)
        assertFalse(controller.uiState.historyLoading)
        assertFalse(controller.uiState.historyHasMore)
    }

    @Test
    fun liveDuplicateIsEnrichedMergedAndSavedOncePerDelivery() = runBlocking {
        val cache = FakeCache()
        val controller = controller(cache = cache)
        controller.onContextChanged(ACCOUNT, 7)
        val identity = RadioMessageIdentityContext(
            onlineDevices = listOf(
                OnlineDevice(
                    id = 8,
                    username = "bob",
                    callsign = "BG5BOB",
                    ssid = 12,
                    nickname = "Bob",
                    model = 1,
                    ghost = false,
                    disableSend = false,
                    disableReceive = false,
                    lastActivity = ""
                )
            ),
            currentSsid = 101,
            muted = true
        )

        controller.onLiveMessage(
            message(
                id = "live-a",
                username = "bob",
                senderSsid = 12,
                groupId = 7,
                durationMs = 1_000L,
                audioCacheKey = ""
            ),
            identity
        )
        controller.onLiveMessage(
            message(
                id = "live-b",
                username = "bob",
                senderSsid = 12,
                groupId = 7,
                durationMs = 2_000L,
                audioCacheKey = "voice-cache",
                timestamp = NOW - 500L
            ),
            identity
        )
        yield()

        val merged = controller.uiState.messages.single()
        assertEquals("bob", merged.senderUsername)
        assertEquals("BG5BOB", merged.senderCallsign)
        assertEquals("Bob", merged.senderNickname)
        assertEquals("voice-cache", merged.audioCacheKey)
        assertEquals(2_000L, merged.durationMs)
        assertEquals(2, cache.saved.size)
    }

    @Test
    fun playedEventsUpdateStateAndCache() = runBlocking {
        val first = message(id = "voice-1", groupId = 7)
        val second = message(id = "voice-2", groupId = 7, timestamp = NOW + 1)
        val cache = FakeCache().apply { seed(ACCOUNT.key, 7, listOf(first, second)) }
        val controller = controller(cache = cache)
        controller.onContextChanged(ACCOUNT, 7)
        yield()

        controller.markPlayed(first)
        yield()
        assertTrue(controller.uiState.messages.first().played)
        assertEquals(listOf("voice-1"), cache.markedPlayedIds)

        controller.onEvent(RadioMessageEvent.MarkAllPlayed)
        yield()
        assertTrue(controller.uiState.messages.all(RadioMessage::played))
        assertEquals(1, cache.markAllPlayedCalls)
    }

    @Test
    fun staleGroupCacheLoadCannotOverwriteNewGroup() = runBlocking {
        val firstLoadStarted = CountDownLatch(1)
        val releaseFirstLoad = CountDownLatch(1)
        val cache = FakeCache(
            beforeLoad = { groupId ->
                if (groupId == 1) {
                    firstLoadStarted.countDown()
                    try {
                        check(releaseFirstLoad.await(1, TimeUnit.SECONDS))
                    } catch (_: InterruptedException) {
                        check(releaseFirstLoad.await(1, TimeUnit.SECONDS))
                    }
                }
            }
        ).apply {
            seed(ACCOUNT.key, 1, listOf(message(id = "old", groupId = 1)))
            seed(ACCOUNT.key, 2, listOf(message(id = "new", groupId = 2)))
        }

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val controller = RadioMessageController(
                remote = FakeRemote(),
                cache = cache,
                scope = this,
                ioDispatcher = dispatcher,
                currentTimeMillis = { NOW },
                friendlyError = { it.message.orEmpty() }
            )
            controller.onContextChanged(ACCOUNT, 1)
            yield()
            assertTrue(firstLoadStarted.await(1, TimeUnit.SECONDS))

            controller.onContextChanged(ACCOUNT, 2)
            releaseFirstLoad.countDown()
            withTimeout(1_000) {
                while (controller.uiState.messages.singleOrNull()?.id != "new") yield()
            }

            assertEquals("new", controller.uiState.messages.single().id)
        }
    }

    @Test
    fun closeDropsLateServerRefreshCallback() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val refreshed = message(
            id = "server-9",
            groupId = 7,
            serverRecordId = 9,
            syncState = RadioMessageSyncState.CONFIRMED
        )
        val remote = FakeRemote(
            loadMessageAction = { _, _, _ ->
                started.countDown()
                awaitIgnoringInterruption(release)
                finished.countDown()
                refreshed
            }
        )

        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val controller = RadioMessageController(
                remote = remote,
                cache = FakeCache(),
                scope = this,
                ioDispatcher = dispatcher,
                currentTimeMillis = { NOW },
                friendlyError = { it.message.orEmpty() }
            )
            controller.onContextChanged(ACCOUNT, 7)
            yield()
            val callbacks = mutableListOf<Result<RadioMessage>>()
            controller.refreshServerMessage(refreshed, callbacks::add)
            yield()
            assertTrue(started.await(1, TimeUnit.SECONDS))

            controller.close()
            release.countDown()
            assertTrue(finished.await(1, TimeUnit.SECONDS))
            yield()

            assertTrue(callbacks.isEmpty())
        }
    }

    private fun CoroutineScope.controller(remote: FakeRemote = FakeRemote(), cache: FakeCache = FakeCache()) =
        RadioMessageController(
            remote = remote,
            cache = cache,
            scope = this,
            ioDispatcher = Dispatchers.Unconfined,
            currentTimeMillis = { NOW },
            friendlyError = { it.message ?: "failed" }
        )

    private class FakeRemote(
        private val pages: MutableMap<String, RadioMessagePage> = mutableMapOf(),
        private val profiles: Map<String, User> = emptyMap(),
        private val error: Throwable? = null,
        private val loadMessageAction: ((Int, Int, User) -> RadioMessage)? = null
    ) : RadioMessageRemoteDataSource {
        val pageRequests = mutableListOf<String>()
        val profileRequests = mutableListOf<String>()

        override fun loadPage(groupId: Int, cursor: String, accountUser: User): RadioMessagePage {
            error?.let { throw it }
            pageRequests += cursor
            return pages[cursor] ?: RadioMessagePage(emptyList(), "", false)
        }

        override fun loadMessage(groupId: Int, messageId: Int, accountUser: User): RadioMessage =
            loadMessageAction?.invoke(groupId, messageId, accountUser)
                ?: pages.values.asSequence().flatMap { it.messages.asSequence() }
                    .first { it.serverRecordId == messageId }

        override fun loadPublicProfile(username: String): User {
            profileRequests += username.lowercase()
            return profiles.getValue(username.lowercase())
        }
    }

    private class FakeCache(private val beforeLoad: (Int) -> Unit = {}) : RadioMessageCache {
        private val messages = mutableMapOf<Pair<String, Int>, MutableList<RadioMessage>>()
        private val initializedHistory = mutableSetOf<Pair<String, Int>>()
        private var cacheGeneration = 0
        val saved = mutableListOf<RadioMessage>()
        val reconciliations = mutableListOf<Reconciliation>()
        val markedPlayedIds = mutableListOf<String>()
        var markAllPlayedCalls = 0

        fun seed(accountKey: String, groupId: Int, values: List<RadioMessage>) {
            messages[accountKey to groupId] = values.toMutableList()
            initializedHistory += accountKey to groupId
        }

        override fun generation(): Int = cacheGeneration

        override fun isHistoryInitialized(accountKey: String, groupId: Int): Boolean =
            accountKey to groupId in initializedHistory

        override fun markHistoryInitialized(accountKey: String, groupId: Int, expectedGeneration: Int) {
            if (expectedGeneration == cacheGeneration) initializedHistory += accountKey to groupId
        }

        override fun load(accountKey: String, groupId: Int, limit: Int): List<RadioMessage> {
            beforeLoad(groupId)
            return messages[accountKey to groupId].orEmpty().sortedBy(RadioMessage::timestamp).takeLast(limit)
        }

        override fun save(accountKey: String, groupId: Int, message: RadioMessage, expectedGeneration: Int) {
            if (expectedGeneration != cacheGeneration) return
            saved += message
            val stored = messages.getOrPut(accountKey to groupId, ::mutableListOf)
            stored.removeAll { it.id == message.id }
            stored += message
        }

        override fun reconcile(
            accountKey: String,
            groupId: Int,
            remoteMessages: List<RadioMessage>,
            authoritativeWindow: LongRange?,
            expectedGeneration: Int
        ) {
            if (expectedGeneration != cacheGeneration) return
            reconciliations += Reconciliation(remoteMessages, authoritativeWindow)
            val key = accountKey to groupId
            messages[key] = RadioMessageReconciler
                .deduplicate(messages[key].orEmpty() + remoteMessages)
                .toMutableList()
        }

        override fun markPlayed(
            accountKey: String,
            groupId: Int,
            localId: String,
            serverRecordId: Int?,
            expectedGeneration: Int
        ) {
            if (expectedGeneration != cacheGeneration) return
            markedPlayedIds += localId
            messages[accountKey to groupId]?.replaceAll { message ->
                val matchesRecord = serverRecordId != null && message.serverRecordId == serverRecordId
                if (message.id == localId || matchesRecord) {
                    message.copy(played = true)
                } else {
                    message
                }
            }
        }

        override fun markAllPlayed(accountKey: String, groupId: Int, expectedGeneration: Int) {
            if (expectedGeneration != cacheGeneration) return
            markAllPlayedCalls++
            messages[accountKey to groupId]?.replaceAll { it.copy(played = true) }
        }
    }

    private data class Reconciliation(val messages: List<RadioMessage>, val authoritativeWindow: LongRange?)

    private companion object {
        const val NOW = 10_000_000L
        val ACCOUNT = RadioMessageAccount(
            key = "https://example.test#1",
            user = User(id = 1, username = "alice", nickname = "Alice", callsign = "BG5ALC")
        )

        fun message(
            id: String,
            username: String = "",
            senderSsid: Int = 12,
            timestamp: Long = NOW - 1_000L,
            groupId: Int = 7,
            durationMs: Long = 1_000L,
            audioCacheKey: String = "cache-$id",
            serverRecordId: Int? = null,
            syncState: RadioMessageSyncState = RadioMessageSyncState.LOCAL
        ) = RadioMessage(
            id = id,
            type = RadioMessageType.VOICE,
            senderCallsign = "",
            senderSsid = senderSsid,
            senderUsername = username,
            content = "",
            timestamp = timestamp,
            mine = false,
            durationMs = durationMs,
            audioCacheKey = audioCacheKey,
            serverRecordId = serverRecordId,
            syncState = syncState,
            groupId = groupId
        )
    }
}

private fun awaitIgnoringInterruption(latch: CountDownLatch) {
    while (latch.count > 0L) {
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            // Simulates a blocking remote request that cannot be cancelled cooperatively.
        }
    }
}
