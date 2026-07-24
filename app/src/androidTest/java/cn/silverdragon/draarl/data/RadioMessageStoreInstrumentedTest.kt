package cn.silverdragon.draarl.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
            mine = true,
        )

        RadioMessageStore(context).use { store -> store.save(accountKey, groupId, local) }

        RadioMessageStore(context).use { reopened ->
            assertEquals(listOf(local), reopened.load(accountKey, groupId))

            val remote = local.copy(
                id = "record-123",
                timestamp = local.timestamp + 30_000L,
                serverRecordId = 123,
                syncState = RadioMessageSyncState.CONFIRMED,
            )
            reopened.reconcile(accountKey, groupId, listOf(remote))

            assertEquals(listOf(remote), reopened.load(accountKey, groupId))
        }
    }
}
