package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
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
            val response = requester.executeMapped(
                "GET",
                "/api/groups?page=$page&page_size=$GROUP_PAGE_SIZE",
                mapper = GroupApiResponseMapper::page
            )
            val pageGroups = response.items.map(GroupDto::toDomain)
            groups += pageGroups
            val shouldContinue = pageGroups.isNotEmpty() && (
                response.hasMore ||
                    (response.total >= 0 && groups.size < response.total) ||
                    (response.total < 0 && pageGroups.size >= GROUP_PAGE_SIZE)
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

    override fun getGroupStats(): Map<Int, Pair<Int, Int>> = requester.executeMapped(
        "GET",
        "/api/radio/groups/stats",
        mapper = GroupApiResponseMapper::stats
    ).associate { item ->
        item.groupId to Pair(item.onlineCount, item.totalCount)
    }

    override fun getOnlineDevices(groupId: Int): List<OnlineDevice> = requester.executeMapped(
        "GET",
        "/api/radio/groups/$groupId/devices",
        mapper = GroupApiResponseMapper::onlineDevices
    ).map(OnlineDeviceDto::toDomain)

    override fun joinGroup(groupId: Int, password: String) {
        requester.execute("POST", "/api/groups/$groupId/join", JSONObject().put("password", password))
    }

    override fun leaveGroup(groupId: Int) {
        requester.execute("POST", "/api/groups/$groupId/leave", JSONObject())
    }

    override fun searchGroups(keyword: String): List<Group> = requester.executeMapped(
        "POST",
        "/api/groups/search",
        JSONObject().put("keyword", keyword).put("page", FIRST_PAGE).put("page_size", SEARCH_PAGE_SIZE),
        mapper = GroupApiResponseMapper::page
    ).items.map(GroupDto::toDomain)

    override fun createGroup(name: String, type: Int, password: String, note: String): Group {
        val body = JSONObject().put("name", name).put("type", type).put("note", note)
        if (password.isNotBlank()) body.put("password", password)
        return requester.executeMapped("POST", "/api/groups", body, mapper = GroupApiResponseMapper::group)
            .toDomain()
    }

    override fun updateGroup(request: GroupUpdateRequest): Group {
        val body = JSONObject().apply {
            request.name?.let { put("name", it) }
            request.type?.let { put("type", it) }
            request.password?.takeIf(String::isNotBlank)?.let { put("password", it) }
            request.note?.let { put("note", it) }
            request.status?.let { put("status", it) }
        }
        return requester.executeMapped(
            "PUT",
            "/api/groups/${request.groupId}",
            body,
            mapper = GroupApiResponseMapper::group
        ).toDomain()
    }

    override fun deleteGroup(groupId: Int) {
        requester.execute("DELETE", "/api/groups/$groupId")
    }
}

private class GroupDeviceApiClient(private val requester: ApiJsonRequester) : GroupDeviceApi {
    override fun getGroupDevices(groupId: Int): List<Device> = requester.executeMapped(
        "GET",
        "/api/groups/$groupId/devices",
        mapper = GroupApiResponseMapper::devices
    ).map(DeviceDto::toDomain)

    override fun updateGroupDeviceCommControl(
        groupId: Int,
        deviceId: Int,
        disableSend: Boolean,
        disableReceive: Boolean
    ): Pair<Boolean, Boolean> {
        val data = requester.executeMapped(
            "PUT",
            "/api/groups/$groupId/devices/$deviceId/comm-control",
            JSONObject().put("disable_send", disableSend).put("disable_recv", disableReceive),
            mapper = GroupApiResponseMapper::communicationControl
        )
        return data.disableSend to data.disableReceive
    }

    override fun kickGroupDevice(groupId: Int, deviceId: Int) {
        requester.execute("DELETE", "/api/groups/$groupId/devices/$deviceId")
    }
}

private const val FIRST_PAGE = 1
private const val GROUP_PAGE_SIZE = 100
private const val MAX_GROUP_PAGES = 100
private const val SEARCH_PAGE_SIZE = 50
