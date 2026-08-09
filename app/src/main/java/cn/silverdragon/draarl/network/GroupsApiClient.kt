package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import org.json.JSONArray
import org.json.JSONObject

internal class GroupsApiClient(requester: ApiJsonRequester) :
    GroupsApi,
    GroupDirectoryApi by GroupDirectoryApiClient(requester),
    GroupDeviceApi by GroupDeviceApiClient(requester)

private class GroupDirectoryApiClient(private val requester: ApiJsonRequester) : GroupDirectoryApi {
    override fun getGroups(): List<Group> {
        val groups = mutableListOf<Group>()
        var page = FIRST_PAGE
        while (page <= MAX_GROUP_PAGES) {
            val response = requester.execute("GET", "/api/groups?page=$page&page_size=$GROUP_PAGE_SIZE")
                .requireObject("data")
            val pageGroups = (response.optJSONArray("items") ?: JSONArray()).objects().map(JSONObject::toGroup)
            groups += pageGroups
            val pagination = response.optJSONObject("pagination")
            val total = response.optInt("total", pagination?.optInt("total", UNKNOWN_TOTAL) ?: UNKNOWN_TOTAL)
            val hasMore = response.optBoolean(
                "has_more",
                response.optBoolean("hasMore", pagination?.optBoolean("has_more", false) ?: false)
            )
            val shouldContinue = pageGroups.isNotEmpty() && (
                hasMore ||
                    (total >= 0 && groups.size < total) ||
                    (total < 0 && pageGroups.size >= GROUP_PAGE_SIZE)
                )
            if (!shouldContinue) break
            page++
        }
        val realtime = runCatching { getGroupStats() }.getOrDefault(emptyMap())
        return groups.distinctBy(Group::id).map { group ->
            realtime[group.id]?.let { (online, total) ->
                group.copy(onlineCount = online, totalCount = total)
            } ?: group
        }
    }

    override fun getGroupStats(): Map<Int, Pair<Int, Int>> {
        val data = requester.execute("GET", "/api/radio/groups/stats").optJSONArray("data") ?: JSONArray()
        return data.objects().associate { item ->
            item.optInt("id") to Pair(
                item.optInt("online_dev_number", item.optInt("online_count")),
                item.optInt("total_dev_number", item.optInt("total_count"))
            )
        }
    }

    override fun getOnlineDevices(groupId: Int): List<OnlineDevice> {
        val data = requester.execute("GET", "/api/radio/groups/$groupId/devices").optJSONArray("data")
            ?: JSONArray()
        return data.objects().map(JSONObject::toOnlineDevice)
    }

    override fun joinGroup(groupId: Int, password: String) {
        requester.execute("POST", "/api/groups/$groupId/join", JSONObject().put("password", password))
    }

    override fun leaveGroup(groupId: Int) {
        requester.execute("POST", "/api/groups/$groupId/leave", JSONObject())
    }

    override fun searchGroups(keyword: String): List<Group> {
        val data = requester.execute(
            "POST",
            "/api/groups/search",
            JSONObject().put("keyword", keyword).put("page", FIRST_PAGE).put("page_size", SEARCH_PAGE_SIZE)
        ).requireObject("data")
        return (data.optJSONArray("items") ?: JSONArray()).objects().map(JSONObject::toGroup)
    }

    override fun createGroup(name: String, type: Int, password: String, note: String): Group {
        val body = JSONObject().put("name", name).put("type", type).put("note", note)
        if (password.isNotBlank()) body.put("password", password)
        return requester.execute("POST", "/api/groups", body).requireObject("data").toGroup()
    }

    override fun updateGroup(request: GroupUpdateRequest): Group {
        val body = JSONObject().apply {
            request.name?.let { put("name", it) }
            request.type?.let { put("type", it) }
            request.password?.takeIf(String::isNotBlank)?.let { put("password", it) }
            request.note?.let { put("note", it) }
            request.status?.let { put("status", it) }
        }
        return requester.execute("PUT", "/api/groups/${request.groupId}", body).requireObject("data").toGroup()
    }

    override fun deleteGroup(groupId: Int) {
        requester.execute("DELETE", "/api/groups/$groupId")
    }
}

private class GroupDeviceApiClient(private val requester: ApiJsonRequester) : GroupDeviceApi {
    override fun getGroupDevices(groupId: Int): List<Device> {
        val data = requester.execute("GET", "/api/groups/$groupId/devices").requireObject("data")
        return (data.optJSONArray("items") ?: JSONArray()).objects().map(JSONObject::toDevice)
    }

    override fun updateGroupDeviceCommControl(
        groupId: Int,
        deviceId: Int,
        disableSend: Boolean,
        disableReceive: Boolean
    ): Pair<Boolean, Boolean> {
        val data = requester.execute(
            "PUT",
            "/api/groups/$groupId/devices/$deviceId/comm-control",
            JSONObject().put("disable_send", disableSend).put("disable_recv", disableReceive)
        ).requireObject("data")
        return data.optBoolean("disable_send") to data.optBoolean("disable_recv")
    }

    override fun kickGroupDevice(groupId: Int, deviceId: Int) {
        requester.execute("DELETE", "/api/groups/$groupId/devices/$deviceId")
    }
}

private fun JSONObject.toGroup() = Group(
    id = optInt("id"),
    name = optStringClean("name"),
    type = optInt("type"),
    status = optInt("status", 1),
    note = optStringClean("note"),
    ownerId = optInt("ower_id"),
    ownerCallsign = optStringClean("ower_callsign"),
    joined = optBoolean("is_joined"),
    owner = optBoolean("is_owner"),
    requiresPassword = optBoolean("require_password"),
    onlineCount = optInt("online_count"),
    totalCount = optInt("total_count"),
    createdAt = optStringClean("create_time"),
    updatedAt = optStringClean("update_time")
)

private fun JSONObject.toOnlineDevice() = OnlineDevice(
    id = optInt("id"),
    username = optStringClean("username"),
    callsign = optStringClean("callsign"),
    ssid = optInt("ssid"),
    nickname = optStringClean("nickname"),
    model = optInt("dev_model"),
    ghost = optBoolean("is_ghost"),
    disableSend = optBoolean("disable_send"),
    disableReceive = optBoolean("disable_recv"),
    lastActivity = optStringClean("last_activity")
)

private const val FIRST_PAGE = 1
private const val GROUP_PAGE_SIZE = 100
private const val MAX_GROUP_PAGES = 100
private const val SEARCH_PAGE_SIZE = 50
private const val UNKNOWN_TOTAL = -1
