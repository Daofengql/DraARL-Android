package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.DeviceBindPreview
import cn.silverdragon.draarl.data.DeviceBindResult
import cn.silverdragon.draarl.data.DevicePasswordInfo
import cn.silverdragon.draarl.data.ReplaceableDevice
import org.json.JSONArray
import org.json.JSONObject

internal data class DevicePageDto(val total: Int, val items: List<DeviceDto>)

internal data class DeviceDto(
    val id: Int,
    val name: String,
    val callsign: String,
    val ssid: Int,
    val model: Int,
    val groupId: Int,
    val online: Boolean,
    val enabled: Boolean,
    val disableSend: Boolean,
    val disableReceive: Boolean,
    val qth: String,
    val note: String,
    val onlineTime: String,
    val entryName: String,
    val priority: Int,
    val lastOnlineIp: String,
    val lastOnlineIpLocation: String,
    val entryId: String,
    val entryMode: String,
    val entrySeenAt: String,
    val ownerId: Int,
    val ownerName: String,
    val ownerCallsign: String,
    val createdAt: String,
    val updatedAt: String
) {
    fun toDomain() = Device(
        id = id,
        name = name,
        callsign = callsign,
        ssid = ssid,
        model = model,
        groupId = groupId,
        online = online,
        enabled = enabled,
        disableSend = disableSend,
        disableReceive = disableReceive,
        qth = qth,
        note = note,
        onlineTime = onlineTime,
        entryName = entryName,
        priority = priority,
        lastOnlineIp = lastOnlineIp,
        lastOnlineIpLocation = lastOnlineIpLocation,
        entryId = entryId,
        entryMode = entryMode,
        entrySeenAt = entrySeenAt,
        ownerId = ownerId,
        ownerName = ownerName,
        ownerCallsign = ownerCallsign,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromJson(json: JSONObject) = DeviceDto(
            id = json.requireInt("id"),
            name = json.optStringClean("name"),
            callsign = json.optStringClean("callsign").ifBlank { json.optStringClean("owner_callsign") },
            ssid = json.optInt("ssid"),
            model = json.optInt("dev_model", json.optInt("model")),
            groupId = json.optInt("group_id"),
            online = json.optBoolean("is_online", json.optBoolean("online")),
            enabled = json.optInt("status", 1) == 1,
            disableSend = json.optBoolean("disable_send"),
            disableReceive = json.optBoolean("disable_recv"),
            qth = json.optStringClean("qth"),
            note = json.optStringClean("note"),
            onlineTime = json.optStringClean("online_time"),
            entryName = json.optStringClean("entry_node_name"),
            priority = json.optInt("priority"),
            lastOnlineIp = json.optStringClean("last_online_ip"),
            lastOnlineIpLocation = json.optStringClean("last_online_ip_location"),
            entryId = json.optStringClean("entry_node_id"),
            entryMode = json.optStringClean("entry_mode"),
            entrySeenAt = json.optStringClean("entry_seen_at"),
            ownerId = json.optInt("owner_id"),
            ownerName = json.optStringClean("owner_name"),
            ownerCallsign = json.optStringClean("owner_callsign"),
            createdAt = json.optStringClean("create_time"),
            updatedAt = json.optStringClean("update_time")
        )
    }
}

internal data class DefaultDeviceGroupDto(val groupId: Int?)

internal data class DeviceConfigDto(val values: Map<String, String>)

internal data class DeviceSyncDto(val message: String)

internal data class DevicePasswordDto(
    val password: String,
    val hasPassword: Boolean,
    val isNew: Boolean,
    val createdAt: String
) {
    fun toDomain() = DevicePasswordInfo(password, hasPassword, isNew, createdAt)
}

internal data class ReplaceableDeviceDto(
    val deviceId: Int,
    val name: String,
    val callsign: String,
    val ssid: Int,
    val lastOnlineIp: String,
    val onlineTime: String
) {
    fun toDomain() = ReplaceableDevice(deviceId, name, callsign, ssid, lastOnlineIp, onlineTime)
}

internal data class DeviceBindPreviewDto(
    val deviceMac: String,
    val callsign: String,
    val message: String,
    val availableSsids: List<Int>,
    val recommendedSsid: Int,
    val replaceableDevices: List<ReplaceableDeviceDto>
) {
    fun toDomain() = DeviceBindPreview(
        deviceMac,
        callsign,
        message,
        availableSsids,
        recommendedSsid,
        replaceableDevices.map(ReplaceableDeviceDto::toDomain)
    )
}

internal data class DeviceBindResultDto(
    val message: String,
    val ssid: Int?,
    val username: String,
    val devicePassword: String,
    val dmrId: Int
) {
    fun toDomain() = DeviceBindResult(message, ssid, username, devicePassword, dmrId)
}

internal object DeviceApiResponseMapper {
    fun page(response: JSONObject): DevicePageDto {
        val data = response.requireObject("data")
        val items = (data.optJSONArray("items") ?: JSONArray()).requireObjects("data.items")
        return DevicePageDto(data.optInt("total", Int.MAX_VALUE), items.map(DeviceDto::fromJson))
    }

    fun defaultGroup(response: JSONObject) = DefaultDeviceGroupDto(
        response.requireObject("data").optNullableInt("group_id")
    )

    fun device(response: JSONObject): DeviceDto = DeviceDto.fromJson(response.requireObject("data"))

    fun config(response: JSONObject) = DeviceConfigDto(jsonStringMap(response.requireObject("data")))

    fun sync(response: JSONObject): DeviceSyncDto {
        val message = response.optJSONObject("data")?.optStringClean("message").orEmpty()
        return DeviceSyncDto(message.ifBlank { "同步请求已发送" })
    }

    fun password(response: JSONObject, generated: Boolean): DevicePasswordDto {
        val data = response.requireObject("data")
        return DevicePasswordDto(
            password = data.optStringClean("device_password"),
            hasPassword = generated || data.optBoolean("has_password"),
            isNew = generated || data.optBoolean("is_new"),
            createdAt = data.optStringClean("created_at")
        )
    }

    fun bindPreview(response: JSONObject): DeviceBindPreviewDto {
        val data = response.requireObject("data")
        val availableSsids = data.optJSONArray("available_ssids") ?: JSONArray()
        val replacements = (data.optJSONArray("replaceable_devices") ?: JSONArray())
            .requireObjects("data.replaceable_devices")
            .map(::replaceableDevice)
        return DeviceBindPreviewDto(
            deviceMac = data.optStringClean("device_mac"),
            callsign = data.optStringClean("call_sign"),
            message = data.optStringClean("message"),
            availableSsids = List(availableSsids.length(), availableSsids::optInt),
            recommendedSsid = data.optInt("recommended_ssid"),
            replaceableDevices = replacements
        )
    }

    fun bindResult(response: JSONObject): DeviceBindResultDto {
        val data = response.requireObject("data")
        val auth = data.optJSONObject("udp_auth_info") ?: JSONObject()
        return DeviceBindResultDto(
            message = data.optStringClean("message"),
            ssid = data.optNullableInt("ssid"),
            username = auth.optStringClean("username"),
            devicePassword = auth.optStringClean("device_password"),
            dmrId = data.optInt("dmr_id")
        )
    }

    private fun replaceableDevice(data: JSONObject) = ReplaceableDeviceDto(
        deviceId = data.requireInt("device_id"),
        name = data.optStringClean("name"),
        callsign = data.optStringClean("callsign"),
        ssid = data.optInt("ssid"),
        lastOnlineIp = data.optStringClean("last_online_ip"),
        onlineTime = data.optStringClean("online_time")
    )
}
