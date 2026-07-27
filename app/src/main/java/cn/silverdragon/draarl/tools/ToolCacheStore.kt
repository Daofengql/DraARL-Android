package cn.silverdragon.draarl.tools

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class ToolCacheStore(context: Context) {
    private val preferences = context.getSharedPreferences("draarl_tool_cache", Context.MODE_PRIVATE)

    fun saveRelays(location: String, relays: List<RelayStation>, savedAt: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString("relay_location", location)
            .putLong("relay_saved_at", savedAt)
            .putString("relay_items", RelayCacheJson.encode(relays))
            .apply()
    }

    fun loadRelays(): CachedRelays? = runCatching {
        val location = preferences.getString("relay_location", "").orEmpty()
        val savedAt = preferences.getLong("relay_saved_at", 0L)
        if (location.isBlank() || savedAt <= 0L) return null
        CachedRelays(
            location = location,
            savedAt = savedAt,
            items = RelayCacheJson.decode(preferences.getString("relay_items", "[]").orEmpty()),
        )
    }.getOrNull()

    fun saveDraft(userId: Int, draft: LogbookDraft) {
        if (userId <= 0) return
        preferences.edit().putString(logbookDraftCacheKey(userId), LogbookDraftJson.encode(draft)).apply()
    }

    fun loadDraft(userId: Int): LogbookDraft? = runCatching {
        if (userId <= 0) return null
        val raw = preferences.getString(logbookDraftCacheKey(userId), "").orEmpty()
        if (raw.isBlank()) return null
        LogbookDraftJson.decode(raw)
    }.getOrNull()

    fun clearDraft(userId: Int) {
        if (userId > 0) preferences.edit().remove(logbookDraftCacheKey(userId)).apply()
    }
}

internal object RelayCacheJson {
    fun encode(relays: List<RelayStation>): String = JSONArray().apply {
        relays.forEach { relay ->
            put(
                JSONObject()
                    .put("id", relay.id)
                    .put("name", relay.name)
                    .put("up", relay.uplinkFrequency)
                    .put("down", relay.downlinkFrequency)
                    .put("tx_tone", relay.transmitTone)
                    .put("rx_tone", relay.receiveTone)
                    .put("owner", relay.ownerCallsign)
                    .put("location", relay.location)
                    .put("status", relay.status)
                    .put("note", relay.note),
            )
        }
    }.toString()

    fun decode(raw: String): List<RelayStation> {
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            RelayStation(
                id = item.optInt("id"),
                name = item.optString("name"),
                uplinkFrequency = item.optString("up"),
                downlinkFrequency = item.optString("down"),
                transmitTone = item.optString("tx_tone"),
                receiveTone = item.optString("rx_tone"),
                ownerCallsign = item.optString("owner"),
                location = item.optString("location"),
                status = item.optInt("status", 1),
                note = item.optString("note"),
            )
        }
    }
}

internal object LogbookDraftJson {
    fun encode(draft: LogbookDraft): String = JSONObject()
        .put("editing_id", draft.editingId)
        .put("my_callsign", draft.myCallsign)
        .put("local_time", draft.localTime)
        .put("tx_frequency", draft.txFrequency)
        .put("rx_frequency", draft.rxFrequency)
        .put("cq_zone", draft.cqZone)
        .put("itu_zone", draft.ituZone)
        .put("mode", draft.mode)
        .put("callsign", draft.callsign)
        .put("their_rst", draft.theirRst)
        .put("their_power", draft.theirPower)
        .put("their_qth", draft.theirQth)
        .put("their_radio", draft.theirRadio)
        .put("their_antenna", draft.theirAntenna)
        .put("my_rst", draft.myRst)
        .put("my_power", draft.myPower)
        .put("my_qth", draft.myQth)
        .put("my_radio", draft.myRadio)
        .put("my_antenna", draft.myAntenna)
        .put("notes", draft.notes)
        .toString()

    fun decode(raw: String): LogbookDraft = JSONObject(raw).run {
        LogbookDraft(
            editingId = optInt("editing_id"),
            myCallsign = optString("my_callsign"),
            localTime = optString("local_time"),
            txFrequency = optString("tx_frequency"),
            rxFrequency = optString("rx_frequency"),
            cqZone = optString("cq_zone"),
            ituZone = optString("itu_zone"),
            mode = optString("mode", "FM"),
            callsign = optString("callsign"),
            theirRst = optString("their_rst", "59"),
            theirPower = optString("their_power"),
            theirQth = optString("their_qth"),
            theirRadio = optString("their_radio"),
            theirAntenna = optString("their_antenna"),
            myRst = optString("my_rst", "59"),
            myPower = optString("my_power"),
            myQth = optString("my_qth"),
            myRadio = optString("my_radio"),
            myAntenna = optString("my_antenna"),
            notes = optString("notes"),
        )
    }
}

internal fun logbookDraftCacheKey(userId: Int) = "logbook_draft_$userId"

internal data class CachedRelays(val location: String, val items: List<RelayStation>, val savedAt: Long)
