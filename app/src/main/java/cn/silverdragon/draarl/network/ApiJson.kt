package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.Device
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.toDevice() = Device(
    id = optInt("id"),
    name = optStringClean("name"),
    callsign = optStringClean("callsign").ifBlank { optStringClean("owner_callsign") },
    ssid = optInt("ssid"),
    model = optInt("dev_model", optInt("model")),
    groupId = optInt("group_id"),
    online = optBoolean("is_online", optBoolean("online")),
    enabled = optInt("status", 1) == 1,
    disableSend = optBoolean("disable_send"),
    disableReceive = optBoolean("disable_recv"),
    qth = optStringClean("qth"),
    note = optStringClean("note"),
    onlineTime = optStringClean("online_time"),
    entryName = optStringClean("entry_node_name"),
    priority = optInt("priority"),
    lastOnlineIp = optStringClean("last_online_ip"),
    lastOnlineIpLocation = optStringClean("last_online_ip_location"),
    entryId = optStringClean("entry_node_id"),
    entryMode = optStringClean("entry_mode"),
    entrySeenAt = optStringClean("entry_seen_at"),
    ownerId = optInt("owner_id"),
    ownerName = optStringClean("owner_name"),
    ownerCallsign = optStringClean("owner_callsign"),
    createdAt = optStringClean("create_time"),
    updatedAt = optStringClean("update_time")
)

internal fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

internal fun optionalHttpsUrl(value: String, baseUrl: String): String {
    if (value.isBlank() || baseUrl.isBlank()) return ""
    return runCatching { ApiClient.resolveHttpsUrl(baseUrl, value) }.getOrDefault("")
}

internal fun JSONObject.optNullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return optInt(key)
}

internal fun jsonStringMap(json: JSONObject): Map<String, String> = buildMap {
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (!json.isNull(key)) put(key, json.optString(key))
    }
}

internal fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}

internal fun JSONArray.requireObjects(field: String): List<JSONObject> = List(length()) { index ->
    optJSONObject(index)
        ?: throw ApiException(HTTP_RESPONSE_MAPPING_ERROR, "服务器响应字段 $field[$index] 类型不正确")
}

internal fun JSONArray.ints(): List<Int> = buildList {
    for (index in 0 until length()) optInt(index).takeIf { it > 0 }?.let(::add)
}

internal fun JSONArray.strings(): List<String> = buildList {
    for (index in 0 until length()) optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
}

private const val HTTP_RESPONSE_MAPPING_ERROR = 500
