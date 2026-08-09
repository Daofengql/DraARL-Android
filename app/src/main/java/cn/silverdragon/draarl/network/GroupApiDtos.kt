package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import org.json.JSONArray
import org.json.JSONObject

internal data class GroupPageDto(val items: List<GroupDto>, val total: Int, val hasMore: Boolean)

internal data class GroupDto(
    val id: Int,
    val name: String,
    val type: Int,
    val status: Int,
    val note: String,
    val ownerId: Int,
    val ownerCallsign: String,
    val joined: Boolean,
    val owner: Boolean,
    val requiresPassword: Boolean,
    val onlineCount: Int,
    val totalCount: Int,
    val createdAt: String,
    val updatedAt: String
) {
    fun toDomain() = Group(
        id,
        name,
        type,
        status,
        note,
        ownerId,
        ownerCallsign,
        joined,
        owner,
        requiresPassword,
        onlineCount,
        totalCount,
        createdAt,
        updatedAt
    )

    companion object {
        fun fromJson(data: JSONObject) = GroupDto(
            id = data.requireInt("id"),
            name = data.optStringClean("name"),
            type = data.optInt("type"),
            status = data.optInt("status", 1),
            note = data.optStringClean("note"),
            ownerId = data.optInt("ower_id"),
            ownerCallsign = data.optStringClean("ower_callsign"),
            joined = data.optBoolean("is_joined"),
            owner = data.optBoolean("is_owner"),
            requiresPassword = data.optBoolean("require_password"),
            onlineCount = data.optInt("online_count"),
            totalCount = data.optInt("total_count"),
            createdAt = data.optStringClean("create_time"),
            updatedAt = data.optStringClean("update_time")
        )
    }
}

internal data class GroupStatsDto(val groupId: Int, val onlineCount: Int, val totalCount: Int)

internal data class OnlineDeviceDto(
    val id: Int,
    val username: String,
    val callsign: String,
    val ssid: Int,
    val nickname: String,
    val model: Int,
    val ghost: Boolean,
    val disableSend: Boolean,
    val disableReceive: Boolean,
    val lastActivity: String
) {
    fun toDomain() = OnlineDevice(
        id,
        username,
        callsign,
        ssid,
        nickname,
        model,
        ghost,
        disableSend,
        disableReceive,
        lastActivity
    )
}

internal data class CommunicationControlDto(val disableSend: Boolean, val disableReceive: Boolean)

internal object GroupApiResponseMapper {
    fun page(response: JSONObject): GroupPageDto {
        val data = response.requireObject("data")
        val pagination = data.optJSONObject("pagination")
        return GroupPageDto(
            items = (data.optJSONArray("items") ?: JSONArray())
                .requireObjects("data.items")
                .map(GroupDto::fromJson),
            total = data.optInt("total", pagination?.optInt("total", UNKNOWN_TOTAL) ?: UNKNOWN_TOTAL),
            hasMore = data.optBoolean(
                "has_more",
                data.optBoolean("hasMore", pagination?.optBoolean("has_more", false) ?: false)
            )
        )
    }

    fun group(response: JSONObject): GroupDto = GroupDto.fromJson(response.requireObject("data"))

    fun stats(response: JSONObject): List<GroupStatsDto> = (response.optJSONArray("data") ?: JSONArray())
        .requireObjects("data")
        .map { data ->
            GroupStatsDto(
                groupId = data.requireInt("id"),
                onlineCount = data.optInt("online_dev_number", data.optInt("online_count")),
                totalCount = data.optInt("total_dev_number", data.optInt("total_count"))
            )
        }

    fun onlineDevices(response: JSONObject): List<OnlineDeviceDto> =
        (response.optJSONArray("data") ?: JSONArray()).requireObjects("data").map { data ->
            OnlineDeviceDto(
                id = data.requireInt("id"),
                username = data.optStringClean("username"),
                callsign = data.optStringClean("callsign"),
                ssid = data.optInt("ssid"),
                nickname = data.optStringClean("nickname"),
                model = data.optInt("dev_model"),
                ghost = data.optBoolean("is_ghost"),
                disableSend = data.optBoolean("disable_send"),
                disableReceive = data.optBoolean("disable_recv"),
                lastActivity = data.optStringClean("last_activity")
            )
        }

    fun devices(response: JSONObject): List<DeviceDto> {
        val items = response.requireObject("data").optJSONArray("items") ?: JSONArray()
        return items.requireObjects("data.items").map(DeviceDto::fromJson)
    }

    fun communicationControl(response: JSONObject): CommunicationControlDto {
        val data = response.requireObject("data")
        return CommunicationControlDto(data.optBoolean("disable_send"), data.optBoolean("disable_recv"))
    }
}

private const val UNKNOWN_TOTAL = -1
