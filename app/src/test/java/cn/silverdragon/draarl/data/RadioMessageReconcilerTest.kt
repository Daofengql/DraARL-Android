package cn.silverdragon.draarl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioMessageReconcilerTest {
    @Test
    fun `matches a local text message with its remote record`() {
        val local = message(content = "收到请回复", timestamp = 1_000_000L)
        val remote = message(
            id = "record-42",
            content = "收到请回复",
            timestamp = 1_030_000L,
            serverRecordId = 42,
            syncState = RadioMessageSyncState.CONFIRMED,
        )

        assertTrue(RadioMessageReconciler.isLikelySameEvent(local, remote))
    }

    @Test
    fun `does not merge different text payloads or distant events`() {
        val local = message(content = "第一条", timestamp = 1_000_000L)

        assertFalse(RadioMessageReconciler.isLikelySameEvent(local, message(content = "第二条", timestamp = 1_010_000L)))
        assertFalse(RadioMessageReconciler.isLikelySameEvent(local, message(content = "第一条", timestamp = 1_121_000L)))
    }

    @Test
    fun `matches voice events without relying on presentation text`() {
        val local = message(
            type = RadioMessageType.VOICE,
            content = "语音 12秒",
            timestamp = 2_000_000L,
        )
        val remote = message(
            type = RadioMessageType.VOICE,
            content = "历史语音 12秒",
            timestamp = 2_005_000L,
            serverRecordId = 9,
            syncState = RadioMessageSyncState.CONFIRMED,
        )

        assertTrue(RadioMessageReconciler.isLikelySameEvent(local, remote))
    }

    @Test
    fun `only accepts remote records older than the settle delay`() {
        val now = 10_000_000L
        val stable = message(content = "已稳定", timestamp = now - RadioMessageReconciler.REMOTE_SETTLE_DELAY_MS)
        val recent = message(content = "刚发送", timestamp = now - RadioMessageReconciler.REMOTE_SETTLE_DELAY_MS + 1)

        val result = RadioMessageReconciler.settledRemoteMessages(listOf(stable, recent), now)

        assertTrue(stable in result)
        assertFalse(recent in result)
    }

    @Test
    fun `server record replaces matching local message and keeps local audio cache`() {
        val local = message(
            type = RadioMessageType.VOICE,
            content = "语音",
            timestamp = 2_000_000L,
        ).copy(audioCacheKey = "message:local-1", played = true)
        val remote = message(
            id = "record-9",
            type = RadioMessageType.VOICE,
            content = "历史语音",
            timestamp = 2_005_000L,
            serverRecordId = 9,
            syncState = RadioMessageSyncState.CONFIRMED,
        )

        val result = RadioMessageReconciler.deduplicate(listOf(local, remote))

        assertEquals(1, result.size)
        assertEquals("record-9", result.single().id)
        assertEquals("message:local-1", result.single().audioCacheKey)
        assertTrue(result.single().played)
    }

    @Test
    fun `does not merge nearby voice events with clearly different durations`() {
        val short = message(
            type = RadioMessageType.VOICE,
            content = "语音",
            timestamp = 2_000_000L,
        ).copy(durationMs = 2_000L)
        val long = message(
            type = RadioMessageType.VOICE,
            content = "历史语音",
            timestamp = 2_005_000L,
        ).copy(durationMs = 12_000L)

        assertFalse(RadioMessageReconciler.isLikelySameEvent(short, long))
    }

    @Test
    fun `deduplicates immediate repeated live delivery only`() {
        val first = message(content = "测试", timestamp = 1_000_000L)

        assertTrue(RadioMessageReconciler.isDuplicateLiveDelivery(first, first.copy(id = "echo", timestamp = 1_001_000L)))
        assertFalse(RadioMessageReconciler.isDuplicateLiveDelivery(first, first.copy(id = "later", timestamp = 1_002_000L)))
    }

    @Test
    fun `authoritative window keeps a long voice that only just ended`() {
        val window = 1_000_000L..2_000_000L

        assertFalse(
            RadioMessageReconciler.shouldRemoveFromAuthoritativeWindow(
                serverRecordId = null,
                timestamp = 1_900_000L,
                durationMs = 200_000L,
                authoritativeRecordIds = emptySet(),
                window = window,
            ),
        )
    }

    @Test
    fun `authoritative window removes stale local and deleted server records`() {
        val window = 1_000_000L..2_000_000L

        assertTrue(
            RadioMessageReconciler.shouldRemoveFromAuthoritativeWindow(
                serverRecordId = null,
                timestamp = 1_500_000L,
                durationMs = 10_000L,
                authoritativeRecordIds = setOf(7),
                window = window,
            ),
        )
        assertTrue(
            RadioMessageReconciler.shouldRemoveFromAuthoritativeWindow(
                serverRecordId = 8,
                timestamp = 1_500_000L,
                durationMs = 0L,
                authoritativeRecordIds = setOf(7),
                window = window,
            ),
        )
        assertFalse(
            RadioMessageReconciler.shouldRemoveFromAuthoritativeWindow(
                serverRecordId = 7,
                timestamp = 1_500_000L,
                durationMs = 0L,
                authoritativeRecordIds = setOf(7),
                window = window,
            ),
        )
    }

    @Test
    fun `reconciles repeated text messages one to one`() {
        val firstLocal = message(id = "local-1", content = "收到", timestamp = 1_000_000L)
        val secondLocal = message(id = "local-2", content = "收到", timestamp = 1_060_000L)
        val firstRemote = message(
            id = "record-1",
            content = "收到",
            timestamp = 1_005_000L,
            serverRecordId = 1,
            syncState = RadioMessageSyncState.CONFIRMED,
        )
        val secondRemote = message(
            id = "record-2",
            content = "收到",
            timestamp = 1_065_000L,
            serverRecordId = 2,
            syncState = RadioMessageSyncState.CONFIRMED,
        )

        val result = RadioMessageReconciler.deduplicate(
            listOf(firstLocal, secondLocal, firstRemote, secondRemote),
        )

        assertEquals(listOf(1, 2), result.mapNotNull(RadioMessage::serverRecordId))
    }

    private fun message(
        id: String = "local-1",
        type: RadioMessageType = RadioMessageType.TEXT,
        content: String,
        timestamp: Long,
        serverRecordId: Int? = null,
        syncState: RadioMessageSyncState = RadioMessageSyncState.LOCAL,
    ) = RadioMessage(
        id = id,
        type = type,
        senderCallsign = "BG7XYZ",
        senderSsid = 101,
        content = content,
        timestamp = timestamp,
        mine = true,
        serverRecordId = serverRecordId,
        syncState = syncState,
    )
}
