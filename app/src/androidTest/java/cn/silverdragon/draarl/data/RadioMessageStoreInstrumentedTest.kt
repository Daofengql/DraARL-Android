package cn.silverdragon.draarl.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioMessageStoreInstrumentedTest {
    @Test
    fun authoritativeReconcileReplacesOnlyTheRequestedWindow() {
        withIsolatedStore("authoritative") { store ->
            val accountKey = "authoritative-account"
            val groupId = 94
            val outside = confirmedMessage("outside", 1, timestamp = 900_000L)
            val staleConfirmed = confirmedMessage("stale-confirmed", 2, timestamp = 1_100_000L)
            val staleLocal = message("stale-local", timestamp = 1_200_000L)
            val authoritative = confirmedMessage("authoritative", 3, timestamp = 1_300_000L)
            listOf(outside, staleConfirmed, staleLocal).forEach { store.save(accountKey, groupId, it) }

            store.reconcile(
                accountKey = accountKey,
                groupId = groupId,
                remoteMessages = listOf(authoritative),
                authoritativeWindow = 1_000_000L..2_000_000L
            )

            assertEquals(listOf("outside", "authoritative"), store.load(accountKey, groupId).map(RadioMessage::id))
            assertEquals(setOf(1, 3), store.confirmedServerRecordIds(accountKey, groupId))
        }
    }

    @Test
    fun reconcileRollsBackEarlierUpsertsWhenTheBatchFails() {
        withIsolatedStore("reconcile-rollback") { store ->
            val accountKey = "rollback-account"
            val groupId = 95
            val original = confirmedMessage("original", 1, timestamp = 2_000_000L)
            val acceptedBeforeFailure = confirmedMessage("accepted", 2, timestamp = 2_100_000L)
            store.save(accountKey, groupId, original)
            val failingBatch = object : AbstractList<RadioMessage>() {
                override val size: Int = 2

                override fun get(index: Int): RadioMessage = when (index) {
                    0 -> acceptedBeforeFailure
                    else -> throw IllegalStateException("forced reconcile failure")
                }
            }

            assertThrows(IllegalStateException::class.java) {
                store.reconcile(accountKey, groupId, failingBatch)
            }

            assertEquals(listOf(original), store.load(accountKey, groupId))
            assertEquals(setOf(1), store.confirmedServerRecordIds(accountKey, groupId))
        }
    }

    @Test
    fun reconcilePrunesOnlyTheOldestMessageBeyondOneThousand() {
        withIsolatedStore("prune") { store ->
            val accountKey = "prune-account"
            val groupId = 96
            val messages = (0..CACHE_BOUNDARY).map { index ->
                confirmedMessage("message-$index", index + 1, timestamp = 3_000_000L + index)
            }

            store.reconcile(accountKey, groupId, messages)

            val loaded = store.load(accountKey, groupId, limit = CACHE_BOUNDARY + 1)
            assertEquals(CACHE_BOUNDARY, loaded.size)
            assertEquals("message-1", loaded.first().id)
            assertEquals("message-1000", loaded.last().id)
            assertFalse(loaded.any { it.id == "message-0" })
        }
    }

    @Test
    fun concurrentClearRejectsAWriterHoldingTheOldGeneration() {
        withIsolatedStore("concurrent-clear") { store ->
            val accountKey = "concurrent-clear-account"
            val groupId = 97
            val oldGeneration = store.generation()
            val writerReady = CountDownLatch(1)
            val releaseWriter = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val staleWrite = executor.submit {
                    writerReady.countDown()
                    assertTrue(releaseWriter.await(5, TimeUnit.SECONDS))
                    store.save(
                        accountKey,
                        groupId,
                        message("stale", timestamp = 4_000_000L),
                        expectedGeneration = oldGeneration
                    )
                }
                assertTrue(writerReady.await(5, TimeUnit.SECONDS))

                store.clearAll()
                releaseWriter.countDown()
                staleWrite.get(5, TimeUnit.SECONDS)

                assertTrue(store.load(accountKey, groupId).isEmpty())
                store.save(accountKey, groupId, message("current", timestamp = 4_100_000L))
                assertEquals(listOf("current"), store.load(accountKey, groupId).map(RadioMessage::id))
            } finally {
                releaseWriter.countDown()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun persistsAndReconcilesAConversationMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountKey = "instrumentation-${System.currentTimeMillis()}"
        val groupId = 24_680
        val local = RadioMessage(
            id = "local-message",
            type = RadioMessageType.TEXT,
            senderCallsign = "BG7TEST",
            senderSsid = 101,
            content = "本地缓存消息",
            timestamp = 1_000_000L,
            mine = true
        )

        RadioMessageStore(context).use { store -> store.save(accountKey, groupId, local) }

        RadioMessageStore(context).use { reopened ->
            val storedLocal = local.copy(played = true)
            assertEquals(listOf(storedLocal), reopened.load(accountKey, groupId))

            val remote = local.copy(
                id = "record-123",
                timestamp = local.timestamp + 30_000L,
                serverRecordId = 123,
                syncState = RadioMessageSyncState.CONFIRMED
            )
            reopened.reconcile(accountKey, groupId, listOf(remote))

            assertEquals(listOf(remote.copy(played = true)), reopened.load(accountKey, groupId))
        }
    }

    @Test
    fun clearInvalidatesOldWriterGeneration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountKey = "generation-${System.currentTimeMillis()}"
        val groupId = 24_681
        val message = message("generation-base", timestamp = 2_000_000L)

        RadioMessageStore(context).use { store ->
            val oldGeneration = store.generation()
            store.markHistoryInitialized(accountKey, groupId)
            assertTrue(store.isHistoryInitialized(accountKey, groupId))
            store.clearAll()
            store.save(accountKey, groupId, message.copy(id = "stale"), expectedGeneration = oldGeneration)

            assertTrue(store.load(accountKey, groupId).isEmpty())
            assertFalse(store.isHistoryInitialized(accountKey, groupId))

            store.save(accountKey, groupId, message.copy(id = "current"))
            assertEquals(listOf("current"), store.load(accountKey, groupId).map(RadioMessage::id))
        }
    }

    @Test
    fun reconcilePreservesReadStateFromInitialHistorySync() {
        withIsolatedStore("initial-history") { store ->
            val message = confirmedMessage("history", 55, timestamp = 2_500_000L)
                .copy(type = RadioMessageType.VOICE, played = true)

            store.reconcile("history-account", 98, listOf(message))

            assertTrue(store.load("history-account", 98).single().played)
        }
    }

    @Test
    fun limitsReadsAndMarksOnlyVoiceMessagesPlayed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountKey = "playback-${System.currentTimeMillis()}"
        val groupId = 24_682
        val voice = message("voice", RadioMessageType.VOICE, timestamp = 3_000_000L)
        val text = message("text", RadioMessageType.TEXT, timestamp = 3_001_000L)
        val newest = message("newest", RadioMessageType.TEXT, timestamp = 3_002_000L)

        RadioMessageStore(context).use { store ->
            store.save(accountKey, groupId, voice)
            store.save(accountKey, groupId, text)
            store.save(accountKey, groupId, newest)

            assertEquals(listOf("text", "newest"), store.load(accountKey, groupId, limit = 2).map(RadioMessage::id))
            store.markAllPlayed(accountKey, groupId)

            val loaded = store.load(accountKey, groupId)
            assertTrue(loaded.first { it.id == "voice" }.played)
            assertFalse(loaded.first { it.id == "text" }.played)
        }
    }

    @Test
    fun migratesVersionOneRowsWithoutLosingMessageState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "radio-message-migration-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { database ->
                database.execSQL(VERSION_ONE_SCHEMA)
                database.execSQL(
                    "INSERT INTO radio_messages " +
                        "(local_id, account_key, group_id, server_record_id, message_type, sender_callsign, " +
                        "sender_ssid, content, timestamp, mine, duration_ms, sync_state) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(
                        "legacy-message",
                        "legacy-account",
                        91,
                        123,
                        RadioMessageType.VOICE.name,
                        "BG7OLD",
                        7,
                        "legacy audio",
                        4_000_000L,
                        0,
                        2_500L,
                        RadioMessageSyncState.CONFIRMED.name
                    )
                )
                database.version = 1
            }

            RadioMessageStore(context, databaseName).use { store ->
                val migrated = store.load("legacy-account", 91).single()

                assertEquals("legacy-message", migrated.id)
                assertEquals("BG7OLD", migrated.senderCallsign)
                assertEquals("", migrated.senderUsername)
                assertEquals("", migrated.senderNickname)
                assertEquals("", migrated.audioUrl)
                assertEquals("", migrated.audioCacheKey)
                assertEquals(0, migrated.groupId)
                assertTrue(migrated.played)
                assertEquals(6, store.readableDatabase.version)
                assertTrue(store.isHistoryInitialized("legacy-account", 91))
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun latestWindowIsStableAndIsolatedByConversation() {
        withIsolatedStore("window") { store ->
            val accountKey = "window-account"
            val groupId = 92
            listOf("a", "c", "b").forEach { id ->
                store.save(accountKey, groupId, message(id, timestamp = 5_000_000L))
            }
            store.save("other-account", groupId, message("other-account", timestamp = 6_000_000L))
            store.save(accountKey, groupId + 1, message("other-group", timestamp = 6_000_000L))

            assertEquals(listOf("b", "c"), store.load(accountKey, groupId, limit = 2).map(RadioMessage::id))
            assertEquals(listOf("other-account"), store.load("other-account", groupId).map(RadioMessage::id))
            assertEquals(listOf("other-group"), store.load(accountKey, groupId + 1).map(RadioMessage::id))
        }
    }

    @Test
    fun concurrentWritersKeepEveryDistinctMessage() {
        withIsolatedStore("concurrent") { store ->
            val executor = Executors.newFixedThreadPool(4)
            try {
                val writes = (0 until CONCURRENT_MESSAGE_COUNT).map { index ->
                    executor.submit {
                        store.save(
                            "concurrent-account",
                            93,
                            message("message-$index", timestamp = 6_000_000L + index)
                        )
                    }
                }
                writes.forEach { it.get(5, TimeUnit.SECONDS) }

                assertEquals(
                    CONCURRENT_MESSAGE_COUNT,
                    store.load("concurrent-account", 93, limit = CONCURRENT_MESSAGE_COUNT).size
                )
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun withIsolatedStore(prefix: String, block: (RadioMessageStore) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "radio-message-$prefix-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            RadioMessageStore(context, databaseName).use(block)
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    private fun message(id: String, type: RadioMessageType = RadioMessageType.TEXT, timestamp: Long) = RadioMessage(
        id = id,
        type = type,
        senderCallsign = "BG7TEST",
        senderSsid = 101,
        content = id,
        timestamp = timestamp,
        mine = false
    )

    private fun confirmedMessage(id: String, serverRecordId: Int, timestamp: Long) =
        message(id, timestamp = timestamp).copy(
            serverRecordId = serverRecordId,
            syncState = RadioMessageSyncState.CONFIRMED
        )

    private companion object {
        const val CACHE_BOUNDARY = 1_000
        const val CONCURRENT_MESSAGE_COUNT = 40
        val VERSION_ONE_SCHEMA =
            """
            CREATE TABLE radio_messages (
                local_id TEXT PRIMARY KEY,
                account_key TEXT NOT NULL,
                group_id INTEGER NOT NULL,
                server_record_id INTEGER,
                message_type TEXT NOT NULL,
                sender_callsign TEXT NOT NULL,
                sender_ssid INTEGER NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                mine INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                sync_state TEXT NOT NULL
            )
            """.trimIndent()
    }
}
