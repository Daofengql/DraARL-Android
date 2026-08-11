package cn.silverdragon.draarl.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RadioMessageStore internal constructor(context: Context, databaseName: String) :
    SQLiteOpenHelper(
        context.applicationContext,
        databaseName,
        null,
        DATABASE_VERSION
    ) {
    constructor(context: Context) : this(context, DATABASE_NAME)

    private val databaseFile = context.applicationContext.getDatabasePath(databaseName)

    // Incremented whenever the message cache is cleared. Async writers capture
    // this value and are ignored if they belong to the previous cache epoch.
    @Volatile private var cacheGeneration = 0
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE_MESSAGES (
                local_id TEXT PRIMARY KEY,
                account_key TEXT NOT NULL,
                group_id INTEGER NOT NULL,
                server_record_id INTEGER,
                message_type TEXT NOT NULL,
                sender_callsign TEXT NOT NULL,
                sender_ssid INTEGER NOT NULL,
                sender_username TEXT NOT NULL,
                sender_nickname TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                mine INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                audio_url TEXT NOT NULL,
                audio_cache_key TEXT NOT NULL,
                sync_state TEXT NOT NULL,
                source_group_id INTEGER NOT NULL DEFAULT 0,
                played INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX messages_server_record ON $TABLE_MESSAGES " +
                "(account_key, group_id, server_record_id)"
        )
        database.execSQL(
            "CREATE INDEX messages_conversation_time ON $TABLE_MESSAGES " +
                "(account_key, group_id, timestamp DESC)"
        )
        createSyncStateTable(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN sender_username TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN sender_nickname TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN audio_url TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN audio_data BLOB")
        }
        if (oldVersion < 3) {
            database.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN audio_cache_key TEXT NOT NULL DEFAULT ''")
            database.execSQL("UPDATE $TABLE_MESSAGES SET audio_data = NULL")
        }
        if (oldVersion < 4) {
            database.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN source_group_id INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 5) {
            database.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN played INTEGER NOT NULL DEFAULT 1")
        }
        if (oldVersion < HISTORY_STATE_DATABASE_VERSION) {
            createSyncStateTable(database)
            database.execSQL(
                "UPDATE $TABLE_MESSAGES SET played = 1 WHERE message_type = ?",
                arrayOf<Any>(RadioMessageType.VOICE.name)
            )
            database.execSQL(
                "INSERT OR IGNORE INTO $TABLE_SYNC_STATE (account_key, group_id) " +
                    "SELECT DISTINCT account_key, group_id FROM $TABLE_MESSAGES"
            )
        }
    }

    @Synchronized
    fun save(accountKey: String, groupId: Int, message: RadioMessage, expectedGeneration: Int = cacheGeneration) {
        if (expectedGeneration != cacheGeneration) return
        writableDatabase.transaction {
            if (expectedGeneration != cacheGeneration) return@transaction
            if (message.serverRecordId == null) {
                findMatchingMessage(this, accountKey, groupId, message, confirmed = true)?.let { confirmedId ->
                    if (message.audioCacheKey.isNotBlank()) {
                        update(
                            TABLE_MESSAGES,
                            ContentValues().apply { put("audio_cache_key", message.audioCacheKey) },
                            "local_id = ? AND audio_cache_key = ''",
                            arrayOf(confirmedId)
                        )
                    }
                    if (message.mine || message.played) {
                        update(
                            TABLE_MESSAGES,
                            ContentValues().apply { put("played", 1) },
                            "local_id = ?",
                            arrayOf(confirmedId)
                        )
                    }
                    return@transaction
                }
            }
            upsert(this, accountKey, groupId, message)
            prune(this, accountKey, groupId)
        }
    }

    @Synchronized
    fun reconcile(
        accountKey: String,
        groupId: Int,
        remoteMessages: List<RadioMessage>,
        authoritativeWindow: LongRange? = null,
        expectedGeneration: Int = cacheGeneration
    ) {
        if (expectedGeneration != cacheGeneration) return
        if (remoteMessages.isEmpty() && authoritativeWindow == null) return
        writableDatabase.transaction {
            if (expectedGeneration != cacheGeneration) return@transaction
            remoteMessages.forEach { remote ->
                val storedState = loadServerPlaybackState(
                    this,
                    accountKey,
                    groupId,
                    remote.serverRecordId
                )
                var mergedRemote = remote.copy(
                    audioCacheKey = remote.audioCacheKey.ifBlank { storedState?.audioCacheKey.orEmpty() },
                    played = remote.mine || remote.played || (storedState?.played ?: false)
                )
                if (mergedRemote.audioCacheKey.isBlank()) {
                    findMatchingMessage(this, accountKey, groupId, remote, confirmed = false)?.let { localId ->
                        val localState = loadLocalPlaybackState(this, localId)
                        mergedRemote = remote.copy(
                            audioCacheKey = localState?.audioCacheKey.orEmpty(),
                            played = remote.mine || remote.played || (localState?.played == true)
                        )
                        delete(TABLE_MESSAGES, "local_id = ?", arrayOf(localId))
                    }
                }
                upsert(this, accountKey, groupId, mergedRemote)
            }
            authoritativeWindow?.let { window ->
                removeMessagesMissingFromAuthoritativeWindow(
                    database = this,
                    accountKey = accountKey,
                    groupId = groupId,
                    authoritativeRecordIds = remoteMessages.mapNotNullTo(mutableSetOf(), RadioMessage::serverRecordId),
                    window = window
                )
            }
            prune(this, accountKey, groupId)
        }
    }

    @Synchronized
    fun load(accountKey: String, groupId: Int, limit: Int = DISPLAY_LIMIT): List<RadioMessage> {
        val messages = mutableListOf<RadioMessage>()
        readableDatabase.query(
            TABLE_MESSAGES,
            MESSAGE_COLUMNS,
            "account_key = ? AND group_id = ?",
            arrayOf(accountKey, groupId.toString()),
            null,
            null,
            "timestamp DESC, local_id DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                messages += RadioMessage(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("local_id")),
                    type = runCatching {
                        RadioMessageType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("message_type")))
                    }.getOrDefault(RadioMessageType.SYSTEM),
                    senderCallsign = cursor.getString(cursor.getColumnIndexOrThrow("sender_callsign")),
                    senderSsid = cursor.getInt(cursor.getColumnIndexOrThrow("sender_ssid")),
                    senderUsername = cursor.getString(cursor.getColumnIndexOrThrow("sender_username")),
                    senderNickname = cursor.getString(cursor.getColumnIndexOrThrow("sender_nickname")),
                    content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    mine = cursor.getInt(cursor.getColumnIndexOrThrow("mine")) == 1,
                    durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms")),
                    audioUrl = cursor.getString(cursor.getColumnIndexOrThrow("audio_url")),
                    audioCacheKey = cursor.getString(cursor.getColumnIndexOrThrow("audio_cache_key")),
                    serverRecordId = cursor.getIntOrNull("server_record_id"),
                    syncState = runCatching {
                        RadioMessageSyncState.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("sync_state")))
                    }.getOrDefault(RadioMessageSyncState.LOCAL),
                    groupId = cursor.getInt(cursor.getColumnIndexOrThrow("source_group_id")),
                    played = cursor.getInt(cursor.getColumnIndexOrThrow("played")) == 1
                )
            }
        }
        return messages.asReversed()
    }

    private fun upsert(database: SQLiteDatabase, accountKey: String, groupId: Int, message: RadioMessage) {
        database.insertWithOnConflict(
            TABLE_MESSAGES,
            null,
            ContentValues().apply {
                put("local_id", message.id)
                put("account_key", accountKey)
                put("group_id", groupId)
                message.serverRecordId?.let { put("server_record_id", it) } ?: putNull("server_record_id")
                put("message_type", message.type.name)
                put("sender_callsign", message.senderCallsign)
                put("sender_ssid", message.senderSsid)
                put("sender_username", message.senderUsername)
                put("sender_nickname", message.senderNickname)
                put("content", message.content)
                put("timestamp", message.timestamp)
                put("mine", if (message.mine) 1 else 0)
                put("duration_ms", message.durationMs)
                put("audio_url", message.audioUrl)
                put("audio_cache_key", message.audioCacheKey)
                put("sync_state", message.syncState.name)
                put("source_group_id", message.groupId)
                put("played", if (message.mine || message.played) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun loadServerPlaybackState(
        database: SQLiteDatabase,
        accountKey: String,
        groupId: Int,
        serverRecordId: Int?
    ): StoredPlaybackState? {
        if (serverRecordId == null) return null
        database.query(
            TABLE_MESSAGES,
            arrayOf("audio_cache_key", "played"),
            "account_key = ? AND group_id = ? AND server_record_id = ?",
            arrayOf(accountKey, groupId.toString(), serverRecordId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                StoredPlaybackState(cursor.getString(0), cursor.getInt(1) == 1)
            } else {
                null
            }
        }
    }

    private fun loadLocalPlaybackState(database: SQLiteDatabase, localId: String): StoredPlaybackState? {
        database.query(
            TABLE_MESSAGES,
            arrayOf("audio_cache_key", "played"),
            "local_id = ?",
            arrayOf(localId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                StoredPlaybackState(cursor.getString(0), cursor.getInt(1) == 1)
            } else {
                null
            }
        }
    }

    @Synchronized
    fun markPlayed(
        accountKey: String,
        groupId: Int,
        localId: String,
        serverRecordId: Int?,
        expectedGeneration: Int = cacheGeneration
    ) {
        if (expectedGeneration != cacheGeneration) return
        val values = ContentValues().apply { put("played", 1) }
        val where = if (serverRecordId == null) {
            "account_key = ? AND group_id = ? AND local_id = ?"
        } else {
            "account_key = ? AND group_id = ? AND (local_id = ? OR server_record_id = ?)"
        }
        val arguments = if (serverRecordId == null) {
            arrayOf(accountKey, groupId.toString(), localId)
        } else {
            arrayOf(accountKey, groupId.toString(), localId, serverRecordId.toString())
        }
        writableDatabase.update(TABLE_MESSAGES, values, where, arguments)
    }

    @Synchronized
    fun markAllPlayed(accountKey: String, groupId: Int, expectedGeneration: Int = cacheGeneration) {
        if (expectedGeneration != cacheGeneration) return
        writableDatabase.update(
            TABLE_MESSAGES,
            ContentValues().apply { put("played", 1) },
            "account_key = ? AND group_id = ? AND message_type = ? AND mine = 0 AND played = 0",
            arrayOf(accountKey, groupId.toString(), RadioMessageType.VOICE.name)
        )
    }

    private fun findMatchingMessage(
        database: SQLiteDatabase,
        accountKey: String,
        groupId: Int,
        target: RadioMessage,
        confirmed: Boolean
    ): String? {
        val window = RadioMessageReconciler.matchWindowMs(target.type)
        database.query(
            TABLE_MESSAGES,
            MESSAGE_COLUMNS,
            "account_key = ? AND group_id = ? AND server_record_id IS ${if (confirmed) "NOT NULL" else "NULL"} " +
                "AND message_type = ? AND mine = ? AND timestamp BETWEEN ? AND ?",
            arrayOf(
                accountKey,
                groupId.toString(),
                target.type.name,
                if (target.mine) "1" else "0",
                (target.timestamp - window).toString(),
                (target.timestamp + window).toString()
            ),
            null,
            null,
            "timestamp ASC"
        ).use { cursor ->
            var bestId: String? = null
            var bestDistance = Long.MAX_VALUE
            while (cursor.moveToNext()) {
                val candidate = cursor.toRadioMessage()
                val distance = kotlin.math.abs(candidate.timestamp - target.timestamp)
                if (RadioMessageReconciler.isLikelySameEvent(candidate, target) && distance < bestDistance) {
                    bestId = candidate.id
                    bestDistance = distance
                }
            }
            return bestId
        }
    }

    private fun prune(database: SQLiteDatabase, accountKey: String, groupId: Int) {
        database.execSQL(
            "DELETE FROM $TABLE_MESSAGES WHERE account_key = ? AND group_id = ? AND local_id IN " +
                "(SELECT local_id FROM $TABLE_MESSAGES WHERE account_key = ? AND group_id = ? " +
                "ORDER BY timestamp DESC, local_id DESC LIMIT -1 OFFSET $CACHE_LIMIT)",
            arrayOf<Any>(accountKey, groupId, accountKey, groupId)
        )
    }

    private fun removeMessagesMissingFromAuthoritativeWindow(
        database: SQLiteDatabase,
        accountKey: String,
        groupId: Int,
        authoritativeRecordIds: Set<Int>,
        window: LongRange
    ) {
        val staleLocalIds = mutableListOf<String>()
        database.query(
            TABLE_MESSAGES,
            arrayOf("local_id", "server_record_id", "timestamp", "duration_ms"),
            "account_key = ? AND group_id = ? AND timestamp BETWEEN ? AND ?",
            arrayOf(accountKey, groupId.toString(), window.first.toString(), window.last.toString()),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val serverRecordIndex = 1
                val serverRecordId = if (cursor.isNull(serverRecordIndex)) null else cursor.getInt(serverRecordIndex)
                if (RadioMessageReconciler.shouldRemoveFromAuthoritativeWindow(
                        serverRecordId = serverRecordId,
                        timestamp = cursor.getLong(2),
                        durationMs = cursor.getLong(3),
                        authoritativeRecordIds = authoritativeRecordIds,
                        window = window
                    )
                ) {
                    staleLocalIds += cursor.getString(0)
                }
            }
        }
        staleLocalIds.forEach { localId ->
            database.delete(TABLE_MESSAGES, "local_id = ?", arrayOf(localId))
        }
    }

    @Synchronized
    fun confirmedServerRecordIds(accountKey: String, groupId: Int): Set<Int> {
        val ids = mutableSetOf<Int>()
        readableDatabase.query(
            TABLE_MESSAGES,
            arrayOf("server_record_id"),
            "account_key = ? AND group_id = ? AND server_record_id IS NOT NULL",
            arrayOf(accountKey, groupId.toString()),
            null,
            null,
            "timestamp DESC",
            CACHE_LIMIT.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getInt(0)
        }
        return ids
    }

    @Synchronized
    fun generation(): Int = cacheGeneration

    @Synchronized
    fun isHistoryInitialized(accountKey: String, groupId: Int): Boolean =
        readableDatabase.query(
            TABLE_SYNC_STATE,
            arrayOf("account_key"),
            "account_key = ? AND group_id = ?",
            arrayOf(accountKey, groupId.toString()),
            null,
            null,
            null,
            "1"
        ).use { it.moveToFirst() }

    @Synchronized
    fun markHistoryInitialized(
        accountKey: String,
        groupId: Int,
        expectedGeneration: Int = cacheGeneration
    ) {
        if (expectedGeneration != cacheGeneration) return
        writableDatabase.insertWithOnConflict(
            TABLE_SYNC_STATE,
            null,
            ContentValues().apply {
                put("account_key", accountKey)
                put("group_id", groupId)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    @Synchronized
    fun clearAll() {
        cacheGeneration++
        writableDatabase.transaction {
            delete(TABLE_MESSAGES, null, null)
            delete(TABLE_SYNC_STATE, null, null)
        }
        // DELETE can leave the SQLite file and WAL at their previous size.
        // Checkpoint first, then reclaim free pages while no transaction is open.
        runCatching { writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
        runCatching { writableDatabase.execSQL("VACUUM") }
    }

    @Synchronized
    fun sizeBytes(): Long = listOf(
        databaseFile,
        java.io.File("${databaseFile.path}-wal"),
        java.io.File("${databaseFile.path}-shm")
    ).sumOf(java.io.File::length)

    private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun android.database.Cursor.getIntOrNull(columnName: String): Int? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getInt(index)
    }

    private fun android.database.Cursor.toRadioMessage() = RadioMessage(
        id = getString(getColumnIndexOrThrow("local_id")),
        type = runCatching {
            RadioMessageType.valueOf(getString(getColumnIndexOrThrow("message_type")))
        }.getOrDefault(RadioMessageType.SYSTEM),
        senderCallsign = getString(getColumnIndexOrThrow("sender_callsign")),
        senderSsid = getInt(getColumnIndexOrThrow("sender_ssid")),
        senderUsername = getString(getColumnIndexOrThrow("sender_username")),
        senderNickname = getString(getColumnIndexOrThrow("sender_nickname")),
        content = getString(getColumnIndexOrThrow("content")),
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
        mine = getInt(getColumnIndexOrThrow("mine")) == 1,
        durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
        audioUrl = getString(getColumnIndexOrThrow("audio_url")),
        audioCacheKey = getString(getColumnIndexOrThrow("audio_cache_key")),
        serverRecordId = getIntOrNull("server_record_id"),
        syncState = runCatching {
            RadioMessageSyncState.valueOf(getString(getColumnIndexOrThrow("sync_state")))
        }.getOrDefault(RadioMessageSyncState.LOCAL),
        groupId = getInt(getColumnIndexOrThrow("source_group_id")),
        played = getInt(getColumnIndexOrThrow("played")) == 1
    )

    private data class StoredPlaybackState(val audioCacheKey: String, val played: Boolean)

    private fun createSyncStateTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SYNC_STATE (
                account_key TEXT NOT NULL,
                group_id INTEGER NOT NULL,
                PRIMARY KEY (account_key, group_id)
            )
            """.trimIndent()
        )
    }

    companion object {
        private const val DATABASE_NAME = "radio_messages.db"
        private const val HISTORY_STATE_DATABASE_VERSION = 6
        private const val DATABASE_VERSION = HISTORY_STATE_DATABASE_VERSION
        private const val TABLE_MESSAGES = "radio_messages"
        private const val TABLE_SYNC_STATE = "radio_message_sync_state"
        private const val DISPLAY_LIMIT = 200
        private const val CACHE_LIMIT = 1_000
        private val MESSAGE_COLUMNS = arrayOf(
            "local_id",
            "server_record_id",
            "message_type",
            "sender_callsign",
            "sender_ssid",
            "sender_username",
            "sender_nickname",
            "content",
            "timestamp",
            "mine",
            "duration_ms",
            "audio_url",
            "audio_cache_key",
            "sync_state",
            "source_group_id",
            "played"
        )
    }
}
