package cn.silverdragon.draarl.tools

import org.json.JSONObject

internal object ToolApiJson {
    fun relay(item: JSONObject) = RelayStation(
        id = item.optInt("id"),
        name = item.cleanString("name"),
        uplinkFrequency = item.cleanString("up_freq"),
        downlinkFrequency = item.cleanString("down_freq"),
        transmitTone = item.cleanString("send_ctss"),
        receiveTone = item.cleanString("recive_ctss"),
        ownerCallsign = item.cleanString("ower_callsign"),
        location = item.cleanString("location"),
        status = item.optInt("status", 1),
        note = item.cleanString("note"),
    )

    fun logbook(item: JSONObject) = LogbookEntry(
        id = item.optInt("id"),
        myCallsign = item.cleanString("my_callsign"),
        timeUtc = item.cleanString("time_utc"),
        txFrequency = item.optDouble("tx_frequency"),
        rxFrequency = item.optDouble("rx_frequency"),
        cqZone = item.optInt("cq_zone"),
        ituZone = item.optInt("itu_zone"),
        mode = item.cleanString("mode"),
        callsign = item.cleanString("callsign"),
        theirRst = item.cleanString("their_rst"),
        theirPower = item.nullableInt("their_power"),
        theirQth = item.cleanString("their_qth"),
        theirRadio = item.cleanString("their_radio"),
        theirAntenna = item.cleanString("their_antenna"),
        myRst = item.cleanString("my_rst"),
        myPower = item.nullableInt("my_power"),
        myQth = item.cleanString("my_qth"),
        myRadio = item.cleanString("my_radio"),
        myAntenna = item.cleanString("my_antenna"),
        notes = item.cleanString("notes"),
        createdAt = item.cleanString("created_at"),
        updatedAt = item.cleanString("updated_at"),
    )

    fun preset(item: JSONObject) = RadioPreset(
        id = item.optInt("id"),
        name = item.cleanString("name"),
        radio = item.cleanString("radio"),
        antenna = item.cleanString("antenna"),
        power = item.nullableInt("power"),
        qth = item.cleanString("qth"),
        sortOrder = item.optInt("sort_order"),
    )

    private fun JSONObject.cleanString(key: String): String =
        if (!has(key) || isNull(key)) "" else optString(key).takeUnless { it == "null" }.orEmpty()

    private fun JSONObject.nullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)
}
