package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.tools.LogbookEntry
import cn.silverdragon.draarl.tools.LogbookPage
import cn.silverdragon.draarl.tools.RadioPreset
import cn.silverdragon.draarl.tools.RelayStation
import org.json.JSONArray
import org.json.JSONObject

internal class ToolsApiClient(private val requester: ApiJsonRequester) : ToolsApi {
    override fun searchPublicRelays(location: String): List<RelayStation> {
        val encoded = urlEncode(location.trim())
        return requester.executeMapped(
            "GET",
            "/api/public/relays?location=$encoded",
            requiresAuth = false,
            mapper = ToolApiResponseMapper::relays
        ).map(RelayStationDto::toDomain)
    }

    override fun getLogbooks(page: Int, pageSize: Int, callsign: String): LogbookPage {
        val query = buildString {
            append("?page=$page&page_size=$pageSize")
            if (callsign.isNotBlank()) append("&callsign=").append(urlEncode(callsign.trim()))
        }
        return requester.executeMapped("GET", "/api/logbooks$query") {
            ToolApiResponseMapper.logbookPage(it, page, pageSize)
        }.toDomain()
    }

    override fun saveLogbook(entry: LogbookEntry): LogbookEntry {
        val body = JSONObject()
            .put("my_callsign", entry.myCallsign.trim().uppercase())
            .put("time_utc", entry.timeUtc)
            .put("tx_frequency", entry.txFrequency)
            .put("rx_frequency", entry.rxFrequency)
            .put("cq_zone", entry.cqZone)
            .put("itu_zone", entry.ituZone)
            .put("mode", entry.mode.trim().uppercase())
            .put("callsign", entry.callsign.trim().uppercase())
            .put("their_rst", entry.theirRst)
            .put("their_power", entry.theirPower ?: JSONObject.NULL)
            .put("their_qth", entry.theirQth)
            .put("their_radio", entry.theirRadio)
            .put("their_antenna", entry.theirAntenna)
            .put("my_rst", entry.myRst)
            .put("my_power", entry.myPower ?: JSONObject.NULL)
            .put("my_qth", entry.myQth)
            .put("my_radio", entry.myRadio)
            .put("my_antenna", entry.myAntenna)
            .put("notes", entry.notes)
        val path = if (entry.id > 0) "/api/logbooks/${entry.id}" else "/api/logbooks"
        val method = if (entry.id > 0) "PUT" else "POST"
        return requester.executeMapped(method, path, body, mapper = ToolApiResponseMapper::logbook).toDomain()
    }

    override fun deleteLogbook(id: Int) {
        requester.execute("DELETE", "/api/logbooks/$id")
    }

    override fun deleteLogbooks(ids: Collection<Int>) {
        if (ids.isEmpty()) return
        requester.execute(
            "DELETE",
            "/api/logbooks/batch",
            JSONObject().put("ids", JSONArray().apply { ids.distinct().forEach(::put) })
        )
    }

    override fun getRadioPresets(): List<RadioPreset> = requester.executeMapped(
        "GET",
        "/api/user/radio-presets",
        mapper = ToolApiResponseMapper::presets
    ).map(RadioPresetDto::toDomain)

    override fun saveRadioPreset(preset: RadioPreset): RadioPreset {
        val body = JSONObject()
            .put("name", preset.name.trim())
            .put("radio", preset.radio.trim())
            .put("antenna", preset.antenna.trim())
            .put("power", preset.power ?: JSONObject.NULL)
            .put("qth", preset.qth.trim())
            .put("sort_order", preset.sortOrder)
        val path = if (preset.id > 0) "/api/user/radio-presets/${preset.id}" else "/api/user/radio-presets"
        val method = if (preset.id > 0) "PUT" else "POST"
        return requester.executeMapped(method, path, body, mapper = ToolApiResponseMapper::preset).toDomain()
    }

    override fun deleteRadioPreset(id: Int) {
        requester.execute("DELETE", "/api/user/radio-presets/$id")
    }

    override fun reorderRadioPresets(orders: List<Pair<Int, Int>>) {
        val items = JSONArray()
        orders.forEach { (id, order) -> items.put(JSONObject().put("id", id).put("order", order)) }
        requester.execute("PUT", "/api/user/radio-presets/reorder", JSONObject().put("orders", items))
    }
}
