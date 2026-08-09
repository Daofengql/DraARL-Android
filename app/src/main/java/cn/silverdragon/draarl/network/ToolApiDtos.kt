package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.tools.LogbookEntry
import cn.silverdragon.draarl.tools.LogbookPage
import cn.silverdragon.draarl.tools.RadioPreset
import cn.silverdragon.draarl.tools.RelayStation
import org.json.JSONArray
import org.json.JSONObject

internal data class RelayStationDto(
    val id: Int,
    val name: String,
    val uplinkFrequency: String,
    val downlinkFrequency: String,
    val transmitTone: String,
    val receiveTone: String,
    val ownerCallsign: String,
    val location: String,
    val status: Int,
    val note: String
) {
    fun toDomain() = RelayStation(
        id,
        name,
        uplinkFrequency,
        downlinkFrequency,
        transmitTone,
        receiveTone,
        ownerCallsign,
        location,
        status,
        note
    )
}

internal data class LogbookEntryDto(
    val id: Int,
    val myCallsign: String,
    val timeUtc: String,
    val txFrequency: Double,
    val rxFrequency: Double,
    val cqZone: Int,
    val ituZone: Int,
    val mode: String,
    val callsign: String,
    val theirRst: String,
    val theirPower: Int?,
    val theirQth: String,
    val theirRadio: String,
    val theirAntenna: String,
    val myRst: String,
    val myPower: Int?,
    val myQth: String,
    val myRadio: String,
    val myAntenna: String,
    val notes: String,
    val createdAt: String,
    val updatedAt: String
) {
    fun toDomain() = LogbookEntry(
        id,
        myCallsign,
        timeUtc,
        txFrequency,
        rxFrequency,
        cqZone,
        ituZone,
        mode,
        callsign,
        theirRst,
        theirPower,
        theirQth,
        theirRadio,
        theirAntenna,
        myRst,
        myPower,
        myQth,
        myRadio,
        myAntenna,
        notes,
        createdAt,
        updatedAt
    )
}

internal data class LogbookPageDto(val items: List<LogbookEntryDto>, val total: Int, val page: Int, val pageSize: Int) {
    fun toDomain() = LogbookPage(items.map(LogbookEntryDto::toDomain), total, page, pageSize)
}

internal data class RadioPresetDto(
    val id: Int,
    val name: String,
    val radio: String,
    val antenna: String,
    val power: Int?,
    val qth: String,
    val sortOrder: Int
) {
    fun toDomain() = RadioPreset(id, name, radio, antenna, power, qth, sortOrder)
}

internal object ToolApiResponseMapper {
    fun relays(response: JSONObject): List<RelayStationDto> {
        val items = response.requireObject("data").optJSONArray("items") ?: JSONArray()
        return items.requireObjects("data.items").map(::relay)
    }

    fun logbookPage(response: JSONObject, defaultPage: Int, defaultPageSize: Int): LogbookPageDto {
        val data = response.requireObject("data")
        val items = data.optJSONArray("items") ?: JSONArray()
        return LogbookPageDto(
            items = items.requireObjects("data.items").map(::logbookItem),
            total = data.optInt("total"),
            page = data.optInt("page", defaultPage),
            pageSize = data.optInt("page_size", defaultPageSize)
        )
    }

    fun logbook(response: JSONObject): LogbookEntryDto = logbookItem(response.requireObject("data"))

    fun presets(response: JSONObject): List<RadioPresetDto> = (response.optJSONArray("data") ?: JSONArray())
        .requireObjects("data")
        .map(::presetItem)

    fun preset(response: JSONObject): RadioPresetDto = presetItem(response.requireObject("data"))

    private fun relay(item: JSONObject) = RelayStationDto(
        id = item.requireInt("id"),
        name = item.optStringClean("name"),
        uplinkFrequency = item.optStringClean("up_freq"),
        downlinkFrequency = item.optStringClean("down_freq"),
        transmitTone = item.optStringClean("send_ctss"),
        receiveTone = item.optStringClean("recive_ctss"),
        ownerCallsign = item.optStringClean("ower_callsign"),
        location = item.optStringClean("location"),
        status = item.optInt("status", 1),
        note = item.optStringClean("note")
    )

    private fun logbookItem(item: JSONObject) = LogbookEntryDto(
        id = item.requireInt("id"),
        myCallsign = item.optStringClean("my_callsign"),
        timeUtc = item.optStringClean("time_utc"),
        txFrequency = item.optDouble("tx_frequency"),
        rxFrequency = item.optDouble("rx_frequency"),
        cqZone = item.optInt("cq_zone"),
        ituZone = item.optInt("itu_zone"),
        mode = item.optStringClean("mode"),
        callsign = item.optStringClean("callsign"),
        theirRst = item.optStringClean("their_rst"),
        theirPower = item.optNullableInt("their_power"),
        theirQth = item.optStringClean("their_qth"),
        theirRadio = item.optStringClean("their_radio"),
        theirAntenna = item.optStringClean("their_antenna"),
        myRst = item.optStringClean("my_rst"),
        myPower = item.optNullableInt("my_power"),
        myQth = item.optStringClean("my_qth"),
        myRadio = item.optStringClean("my_radio"),
        myAntenna = item.optStringClean("my_antenna"),
        notes = item.optStringClean("notes"),
        createdAt = item.optStringClean("created_at"),
        updatedAt = item.optStringClean("updated_at")
    )

    private fun presetItem(item: JSONObject) = RadioPresetDto(
        id = item.requireInt("id"),
        name = item.optStringClean("name"),
        radio = item.optStringClean("radio"),
        antenna = item.optStringClean("antenna"),
        power = item.optNullableInt("power"),
        qth = item.optStringClean("qth"),
        sortOrder = item.optInt("sort_order")
    )
}
