package cn.silverdragon.draarl.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadioMessageStoreInstrumentedTest {
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
            assertEquals(listOf(local), reopened.load(accountKey, groupId))

            val remote = local.copy(
                id = "record-123",
                timestamp = local.timestamp + 30_000L,
                serverRecordId = 123,
                syncState = RadioMessageSyncState.CONFIRMED
            )
            reopened.reconcile(accountKey, groupId, listOf(remote))

            assertEquals(listOf(remote), reopened.load(accountKey, groupId))
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
            store.clearAll()
            store.save(accountKey, groupId, message.copy(id = "stale"), expectedGeneration = oldGeneration)

            assertTrue(store.load(accountKey, groupId).isEmpty())

            store.save(accountKey, groupId, message.copy(id = "current"))
            assertEquals(listOf("current"), store.load(accountKey, groupId).map(RadioMessage::id))
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

    private fun message(id: String, type: RadioMessageType = RadioMessageType.TEXT, timestamp: Long) = RadioMessage(
        id = id,
        type = type,
        senderCallsign = "BG7TEST",
        senderSsid = 101,
        content = id,
        timestamp = timestamp,
        mine = false
    )
}
