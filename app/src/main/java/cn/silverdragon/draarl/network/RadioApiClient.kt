package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.ChannelMessage
import cn.silverdragon.draarl.data.ChannelMessagePage
import cn.silverdragon.draarl.data.ChannelMessageSender
import cn.silverdragon.draarl.data.CommunicationRecord
import cn.silverdragon.draarl.data.CommunicationRecordPage
import cn.silverdragon.draarl.data.CommunicationStats
import cn.silverdragon.draarl.data.DailyCommunicationStats
import cn.silverdragon.draarl.data.RadioRouting
import cn.silverdragon.draarl.data.RadioSession
import cn.silverdragon.draarl.data.User
import org.json.JSONArray
import org.json.JSONObject

internal class RadioApiClient(requester: ApiJsonRequester, sessions: ApiSessionManager, users: UserJsonMapper) :
    RadioApi,
    RadioSessionApi by RadioSessionApiClient(requester, sessions),
    RadioMessageApi by RadioMessageApiClient(requester, users, sessions::currentSession),
    CommunicationApi by CommunicationApiClient(requester, sessions::currentSession)

private class RadioSessionApiClient(private val requester: ApiJsonRequester, private val sessions: ApiSessionManager) :
    RadioSessionApi {
    override fun freshAccessToken(): String = sessions.accessToken(forceRefresh = false)

    override fun renewAccessToken(): String = sessions.accessToken(forceRefresh = true)

    override fun getAccessPoints(): List<AccessPoint> {
        val items = requester.execute("GET", "/api/access-points").requireObject("data").optJSONArray("items")
            ?: JSONArray()
        return items.objects().mapNotNull(JSONObject::toAccessPoint)
            .sortedWith(compareBy(AccessPoint::priority, AccessPoint::displayName))
    }

    override fun getRadioSessions(): List<RadioSession> {
        val data = requester.execute("GET", "/api/radio/sessions").optJSONArray("data") ?: JSONArray()
        return data.objects().map(JSONObject::toRadioSession)
    }

    override fun updateRadioSessionRouting(
        sessionId: String,
        txGroupId: Int,
        rxGroupIds: Collection<Int>
    ): RadioSession {
        val routing = RadioRouting.normalize(txGroupId, rxGroupIds)
        val data = requester.execute(
            "PUT",
            "/api/radio/sessions/${urlEncode(sessionId)}/routing",
            JSONObject()
                .put("tx_group_id", routing.txGroupId)
                .put("rx_group_ids", JSONArray(routing.rxGroupIds))
        ).requireObject("data")
        return data.toRadioSession()
    }
}

private class RadioMessageApiClient(
    private val requester: ApiJsonRequester,
    private val users: UserJsonMapper,
    private val currentSession: () -> cn.silverdragon.draarl.data.Session?
) : RadioMessageApi {
    override fun getPublicUserByName(username: String): User {
        val encoded = urlEncode(username)
        return users.fromJson(requester.execute("GET", "/api/users/name/$encoded/public").requireObject("data"))
    }

    override fun getGroupMessages(groupId: Int, limit: Int?, cursor: String, messageType: String): ChannelMessagePage {
        val data = requester.execute("GET", groupMessagesPath(groupId, limit, cursor, messageType))
            .requireObject("data")
        return ChannelMessagePage(
            messages = (data.optJSONArray("messages") ?: JSONArray()).objects().map(::toChannelMessage),
            nextCursor = data.optStringClean("next_cursor"),
            hasMore = data.optBoolean("has_more"),
            serverTime = data.optStringClean("server_time")
        )
    }

    override fun getGroupMessage(groupId: Int, messageId: Int): ChannelMessage = toChannelMessage(
        requester.execute(
            "GET",
            "/api/groups/${groupId.coerceAtLeast(FIRST_VALID_ID)}/messages/${messageId.coerceAtLeast(FIRST_VALID_ID)}"
        ).requireObject("data")
    )

    private fun toChannelMessage(item: JSONObject): ChannelMessage {
        val sender = item.optJSONObject("sender") ?: JSONObject()
        return ChannelMessage(
            id = item.optInt("id"),
            messageType = item.optStringClean("message_type"),
            sourceGroupId = item.optInt("source_group_id"),
            sourceGroupName = item.optStringClean("source_group_name"),
            requestedGroupId = item.optInt("requested_group_id"),
            sender = ChannelMessageSender(
                userId = sender.optNullableInt("user_id"),
                username = sender.optStringClean("username"),
                callsign = sender.optStringClean("callsign"),
                nickname = sender.optStringClean("nickname"),
                ssid = sender.optInt("ssid"),
                model = sender.optInt("dev_model"),
                ghost = sender.optBoolean("is_ghost")
            ),
            sentAt = item.optStringClean("sent_at"),
            endTime = item.optStringClean("end_time"),
            durationMs = item.optLong("duration_ms"),
            text = item.optStringClean("text_content"),
            audioUrl = optionalHttpsUrl(item.optStringClean("audio_url"), currentSession()?.baseUrl.orEmpty()),
            status = item.optInt("status")
        )
    }
}

private class CommunicationApiClient(
    private val requester: ApiJsonRequester,
    private val currentSession: () -> cn.silverdragon.draarl.data.Session?
) : CommunicationApi {
    override fun getCommunicationStats(): CommunicationStats {
        val data = requester.execute("GET", "/api/comm-records/user-stats").requireObject("data")
        return CommunicationStats(
            totalCount = data.optInt("total_count"),
            totalSize = data.optLong("total_size"),
            totalDurationMs = data.optLong("total_duration")
        )
    }

    override fun getCommunicationTrend(): List<DailyCommunicationStats> {
        val data = requester.execute("GET", "/api/comm-records/user-trend").optJSONArray("data") ?: JSONArray()
        return data.objects().map { item ->
            DailyCommunicationStats(
                date = item.optStringClean("date"),
                count = item.optInt("count"),
                durationMs = item.optLong("duration")
            )
        }
    }

    override fun getCommunicationRecords(page: Int, pageSize: Int, groupId: Int?): CommunicationRecordPage {
        val safePage = page.coerceAtLeast(FIRST_VALID_ID)
        val safePageSize = pageSize.coerceIn(FIRST_VALID_ID, MAX_COMMUNICATION_PAGE_SIZE)
        val query = buildString {
            append("/api/comm-records?page=").append(safePage).append("&page_size=").append(safePageSize)
            if (groupId != null) append("&group_id=").append(groupId)
        }
        val data = requester.execute("GET", query).requireObject("data")
        return CommunicationRecordPage(
            records = (data.optJSONArray("list") ?: JSONArray()).objects().map { it.toCommunicationRecord() },
            total = data.optInt("total"),
            page = data.optInt("page", safePage),
            pageSize = data.optInt("page_size", safePageSize).coerceAtLeast(FIRST_VALID_ID)
        )
    }

    override fun getCommunicationRecord(id: Int): CommunicationRecord = requester
        .execute("GET", "/api/comm-records/${id.coerceAtLeast(FIRST_VALID_ID)}")
        .requireObject("data")
        .toCommunicationRecord()

    private fun JSONObject.toCommunicationRecord() = CommunicationRecord(
        id = optInt("id"),
        deviceId = optInt("device_id"),
        deviceName = optStringClean("device_name"),
        model = optInt("dev_model"),
        groupId = optNullableInt("group_id"),
        groupName = optStringClean("group_name"),
        username = optStringClean("username"),
        nickname = optStringClean("nickname"),
        startedAt = optStringClean("start_time"),
        durationMs = optLong("duration_ms"),
        messageType = optInt("msg_type"),
        text = optStringClean("text_content"),
        audioUrl = optionalHttpsUrl(optStringClean("audio_url"), currentSession()?.baseUrl.orEmpty())
    )
}

internal fun groupMessagesPath(groupId: Int, limit: Int?, cursor: String, messageType: String): String = buildString {
    append("/api/groups/").append(groupId.coerceAtLeast(FIRST_VALID_ID)).append("/messages")
    append("?message_type=").append(urlEncode(messageType))
    limit?.let { append("&limit=").append(it.coerceAtLeast(FIRST_VALID_ID)) }
    if (cursor.isNotBlank()) append("&cursor=").append(urlEncode(cursor))
}

private fun JSONObject.toAccessPoint(): AccessPoint? {
    val host = optStringClean("udp_host")
    val port = optInt("udp_port")
    if (host.isBlank() || port !in MIN_UDP_PORT..MAX_UDP_PORT) return null
    return AccessPoint(
        id = optStringClean("id").ifBlank { "$host:$port" },
        displayName = optStringClean("display_name").ifBlank { host },
        host = host,
        port = port,
        region = optStringClean("region"),
        network = optStringClean("network"),
        priority = optInt("priority", DEFAULT_ACCESS_POINT_PRIORITY)
    )
}

private fun JSONObject.toRadioSession() = RadioSession(
    sessionId = optStringClean("session_id"),
    clientInstanceId = optStringClean("client_instance_id"),
    model = optInt("dev_model"),
    ssid = optInt("ssid"),
    transport = optStringClean("transport"),
    protocolVersion = optInt("protocol_version"),
    capabilities = optJSONArray("capabilities")?.strings().orEmpty(),
    txGroupId = optInt("tx_group_id"),
    rxGroupIds = optJSONArray("rx_group_ids")?.ints().orEmpty(),
    disableSend = optBoolean("disable_send"),
    disableReceive = optBoolean("disable_recv")
)

private const val DEFAULT_ACCESS_POINT_PRIORITY = 100
private const val FIRST_VALID_ID = 1
private const val MAX_COMMUNICATION_PAGE_SIZE = 100
private const val MAX_UDP_PORT = 65_535
private const val MIN_UDP_PORT = 1
