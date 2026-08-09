package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.ChannelMessage
import cn.silverdragon.draarl.data.ChannelMessagePage
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

    override fun getAccessPoints(): List<AccessPoint> = requester.executeMapped(
        "GET",
        "/api/access-points",
        mapper = RadioSessionResponseMapper::accessPoints
    ).map(AccessPointDto::toDomain)
        .sortedWith(compareBy(AccessPoint::priority, AccessPoint::displayName))

    override fun getRadioSessions(): List<RadioSession> = requester.executeMapped(
        "GET",
        "/api/radio/sessions",
        mapper = RadioSessionResponseMapper::sessions
    ).map(RadioSessionDto::toDomain)

    override fun updateRadioSessionRouting(
        sessionId: String,
        txGroupId: Int,
        rxGroupIds: Collection<Int>
    ): RadioSession {
        val routing = RadioRouting.normalize(txGroupId, rxGroupIds)
        return requester.executeMapped(
            "PUT",
            "/api/radio/sessions/${urlEncode(sessionId)}/routing",
            JSONObject()
                .put("tx_group_id", routing.txGroupId)
                .put("rx_group_ids", JSONArray(routing.rxGroupIds)),
            mapper = RadioSessionResponseMapper::session
        ).toDomain()
    }
}

private class RadioMessageApiClient(
    private val requester: ApiJsonRequester,
    private val users: UserJsonMapper,
    private val currentSession: () -> cn.silverdragon.draarl.data.Session?
) : RadioMessageApi {
    override fun getPublicUserByName(username: String): User {
        val encoded = urlEncode(username)
        return users.fromDto(
            requester.executeMapped(
                "GET",
                "/api/users/name/$encoded/public",
                mapper = RadioMessageResponseMapper::user
            )
        )
    }

    override fun getGroupMessages(groupId: Int, limit: Int?, cursor: String, messageType: String): ChannelMessagePage {
        val path = groupMessagesPath(groupId, limit, cursor, messageType)
        return requester.executeMapped("GET", path, mapper = RadioMessageResponseMapper::messagePage)
            .toDomain(currentSession()?.baseUrl.orEmpty())
    }

    override fun getGroupMessage(groupId: Int, messageId: Int): ChannelMessage {
        val path =
            "/api/groups/${groupId.coerceAtLeast(FIRST_VALID_ID)}/messages/${messageId.coerceAtLeast(FIRST_VALID_ID)}"
        return requester.executeMapped(
            "GET",
            path,
            mapper = RadioMessageResponseMapper::message
        ).toDomain(currentSession()?.baseUrl.orEmpty())
    }
}

private class CommunicationApiClient(
    private val requester: ApiJsonRequester,
    private val currentSession: () -> cn.silverdragon.draarl.data.Session?
) : CommunicationApi {
    override fun getCommunicationStats(): CommunicationStats = requester.executeMapped(
        "GET",
        "/api/comm-records/user-stats",
        mapper = CommunicationResponseMapper::stats
    ).toDomain()

    override fun getCommunicationTrend(): List<DailyCommunicationStats> = requester.executeMapped(
        "GET",
        "/api/comm-records/user-trend",
        mapper = CommunicationResponseMapper::trend
    ).map(DailyCommunicationStatsDto::toDomain)

    override fun getCommunicationRecords(page: Int, pageSize: Int, groupId: Int?): CommunicationRecordPage {
        val safePage = page.coerceAtLeast(FIRST_VALID_ID)
        val safePageSize = pageSize.coerceIn(FIRST_VALID_ID, MAX_COMMUNICATION_PAGE_SIZE)
        val query = buildString {
            append("/api/comm-records?page=").append(safePage).append("&page_size=").append(safePageSize)
            if (groupId != null) append("&group_id=").append(groupId)
        }
        return requester.executeMapped("GET", query) {
            CommunicationResponseMapper.recordPage(it, safePage, safePageSize)
        }.toDomain(currentSession()?.baseUrl.orEmpty())
    }

    override fun getCommunicationRecord(id: Int): CommunicationRecord {
        val path = "/api/comm-records/${id.coerceAtLeast(FIRST_VALID_ID)}"
        return requester.executeMapped("GET", path, mapper = CommunicationResponseMapper::record)
            .toDomain(currentSession()?.baseUrl.orEmpty())
    }
}

internal fun groupMessagesPath(groupId: Int, limit: Int?, cursor: String, messageType: String): String = buildString {
    append("/api/groups/").append(groupId.coerceAtLeast(FIRST_VALID_ID)).append("/messages")
    append("?message_type=").append(urlEncode(messageType))
    limit?.let { append("&limit=").append(it.coerceAtLeast(FIRST_VALID_ID)) }
    if (cursor.isNotBlank()) append("&cursor=").append(urlEncode(cursor))
}

private const val FIRST_VALID_ID = 1
private const val MAX_COMMUNICATION_PAGE_SIZE = 100
