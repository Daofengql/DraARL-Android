package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.ChannelMessage
import cn.silverdragon.draarl.data.ChannelMessagePage
import cn.silverdragon.draarl.data.ChannelMessageSender
import cn.silverdragon.draarl.data.CommunicationRecord
import cn.silverdragon.draarl.data.CommunicationRecordPage
import cn.silverdragon.draarl.data.CommunicationStats
import cn.silverdragon.draarl.data.DailyCommunicationStats
import cn.silverdragon.draarl.data.RadioSession
import org.json.JSONArray
import org.json.JSONObject

internal data class AccessPointDto(
    val id: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val region: String,
    val network: String,
    val priority: Int
) {
    fun toDomain() = AccessPoint(id, displayName, host, port, region, network, priority)
}

internal data class RadioSessionDto(
    val sessionId: String,
    val clientInstanceId: String,
    val model: Int,
    val ssid: Int,
    val transport: String,
    val protocolVersion: Int,
    val capabilities: List<String>,
    val txGroupId: Int,
    val rxGroupIds: List<Int>,
    val disableSend: Boolean,
    val disableReceive: Boolean
) {
    fun toDomain() = RadioSession(
        sessionId,
        clientInstanceId,
        model,
        ssid,
        transport,
        protocolVersion,
        capabilities,
        txGroupId,
        rxGroupIds,
        disableSend,
        disableReceive
    )
}

internal data class ChannelMessageSenderDto(
    val userId: Int?,
    val username: String,
    val callsign: String,
    val nickname: String,
    val ssid: Int,
    val model: Int,
    val ghost: Boolean
) {
    fun toDomain() = ChannelMessageSender(userId, username, callsign, nickname, ssid, model, ghost)
}

internal data class ChannelMessageDto(
    val id: Int,
    val messageType: String,
    val sourceGroupId: Int,
    val sourceGroupName: String,
    val requestedGroupId: Int,
    val sender: ChannelMessageSenderDto,
    val sentAt: String,
    val endTime: String,
    val durationMs: Long,
    val text: String,
    val audioUrl: String,
    val status: Int,
    val isAutoBroadcast: Boolean = false
) {
    fun toDomain(baseUrl: String) = ChannelMessage(
        id = id,
        messageType = messageType,
        sourceGroupId = sourceGroupId,
        sourceGroupName = sourceGroupName,
        requestedGroupId = requestedGroupId,
        sender = sender.toDomain(),
        sentAt = sentAt,
        endTime = endTime,
        durationMs = durationMs,
        text = text,
        audioUrl = optionalHttpsUrl(audioUrl, baseUrl),
        status = status,
        isAutoBroadcast = isAutoBroadcast
    )
}

internal data class ChannelMessagePageDto(
    val messages: List<ChannelMessageDto>,
    val nextCursor: String,
    val hasMore: Boolean,
    val serverTime: String
) {
    fun toDomain(baseUrl: String) = ChannelMessagePage(
        messages.map { it.toDomain(baseUrl) },
        nextCursor,
        hasMore,
        serverTime
    )
}

internal data class CommunicationStatsDto(val totalCount: Int, val totalSize: Long, val totalDurationMs: Long) {
    fun toDomain() = CommunicationStats(totalCount, totalSize, totalDurationMs)
}

internal data class DailyCommunicationStatsDto(val date: String, val count: Int, val durationMs: Long) {
    fun toDomain() = DailyCommunicationStats(date, count, durationMs)
}

internal data class CommunicationRecordDto(
    val id: Int,
    val deviceId: Int,
    val deviceName: String,
    val model: Int,
    val groupId: Int?,
    val groupName: String,
    val username: String,
    val nickname: String,
    val startedAt: String,
    val durationMs: Long,
    val messageType: Int,
    val text: String,
    val audioUrl: String
) {
    fun toDomain(baseUrl: String) = CommunicationRecord(
        id,
        deviceId,
        deviceName,
        model,
        groupId,
        groupName,
        username,
        nickname,
        startedAt,
        durationMs,
        messageType,
        text,
        optionalHttpsUrl(audioUrl, baseUrl)
    )
}

internal data class CommunicationRecordPageDto(
    val records: List<CommunicationRecordDto>,
    val total: Int,
    val page: Int,
    val pageSize: Int
) {
    fun toDomain(baseUrl: String) = CommunicationRecordPage(
        records.map { it.toDomain(baseUrl) },
        total,
        page,
        pageSize.coerceAtLeast(FIRST_VALID_ID)
    )
}

internal object RadioSessionResponseMapper {
    fun accessPoints(response: JSONObject): List<AccessPointDto> {
        val items = response.requireObject("data").optJSONArray("items") ?: JSONArray()
        return items.requireObjects("data.items").mapNotNull(::accessPoint)
    }

    fun sessions(response: JSONObject): List<RadioSessionDto> = (response.optJSONArray("data") ?: JSONArray())
        .requireObjects("data")
        .map(::radioSession)

    fun session(response: JSONObject): RadioSessionDto = radioSession(response.requireObject("data"))

    private fun accessPoint(data: JSONObject): AccessPointDto? {
        val host = data.optStringClean("udp_host")
        val port = data.optInt("udp_port")
        if (host.isBlank() || port !in MIN_UDP_PORT..MAX_UDP_PORT) return null
        return AccessPointDto(
            id = data.optStringClean("id").ifBlank { "$host:$port" },
            displayName = data.optStringClean("display_name").ifBlank { host },
            host = host,
            port = port,
            region = data.optStringClean("region"),
            network = data.optStringClean("network"),
            priority = data.optInt("priority", DEFAULT_ACCESS_POINT_PRIORITY)
        )
    }

    private fun radioSession(data: JSONObject) = RadioSessionDto(
        sessionId = data.requireString("session_id"),
        clientInstanceId = data.optStringClean("client_instance_id"),
        model = data.optInt("dev_model"),
        ssid = data.optInt("ssid"),
        transport = data.optStringClean("transport"),
        protocolVersion = data.optInt("protocol_version"),
        capabilities = data.optJSONArray("capabilities")?.strings().orEmpty(),
        txGroupId = data.optInt("tx_group_id"),
        rxGroupIds = data.optJSONArray("rx_group_ids")?.ints().orEmpty(),
        disableSend = data.optBoolean("disable_send"),
        disableReceive = data.optBoolean("disable_recv")
    )
}

internal object RadioMessageResponseMapper {
    fun user(response: JSONObject): UserDto = UserDto.fromJson(response.requireObject("data"))

    fun messagePage(response: JSONObject): ChannelMessagePageDto {
        val data = response.requireObject("data")
        return ChannelMessagePageDto(
            messages = (data.optJSONArray("messages") ?: JSONArray())
                .requireObjects("data.messages")
                .map(::channelMessage),
            nextCursor = data.optStringClean("next_cursor"),
            hasMore = data.optBoolean("has_more"),
            serverTime = data.optStringClean("server_time")
        )
    }

    fun message(response: JSONObject): ChannelMessageDto = channelMessage(response.requireObject("data"))

    private fun channelMessage(data: JSONObject): ChannelMessageDto {
        val sender = data.optJSONObject("sender") ?: JSONObject()
        return ChannelMessageDto(
            id = data.requireInt("id"),
            messageType = data.optStringClean("message_type"),
            sourceGroupId = data.optInt("source_group_id"),
            sourceGroupName = data.optStringClean("source_group_name"),
            requestedGroupId = data.optInt("requested_group_id"),
            sender = ChannelMessageSenderDto(
                userId = sender.optNullableInt("user_id"),
                username = sender.optStringClean("username"),
                callsign = sender.optStringClean("callsign"),
                nickname = sender.optStringClean("nickname"),
                ssid = sender.optInt("ssid"),
                model = sender.optInt("dev_model"),
                ghost = sender.optBoolean("is_ghost")
            ),
            sentAt = data.optStringClean("sent_at"),
            endTime = data.optStringClean("end_time"),
            durationMs = data.optLong("duration_ms"),
            text = data.optStringClean("text_content"),
            audioUrl = data.optStringClean("audio_url"),
            status = data.optInt("status"),
            isAutoBroadcast = data.optBoolean("is_auto_broadcast")
        )
    }
}

internal object CommunicationResponseMapper {
    fun stats(response: JSONObject): CommunicationStatsDto {
        val data = response.requireObject("data")
        return CommunicationStatsDto(
            totalCount = data.optInt("total_count"),
            totalSize = data.optLong("total_size"),
            totalDurationMs = data.optLong("total_duration")
        )
    }

    fun trend(response: JSONObject): List<DailyCommunicationStatsDto> =
        (response.optJSONArray("data") ?: JSONArray()).requireObjects("data").map { data ->
            DailyCommunicationStatsDto(
                date = data.optStringClean("date"),
                count = data.optInt("count"),
                durationMs = data.optLong("duration")
            )
        }

    fun recordPage(response: JSONObject, defaultPage: Int, defaultPageSize: Int): CommunicationRecordPageDto {
        val data = response.requireObject("data")
        return CommunicationRecordPageDto(
            records = (data.optJSONArray("list") ?: JSONArray())
                .requireObjects("data.list")
                .map(::recordItem),
            total = data.optInt("total"),
            page = data.optInt("page", defaultPage),
            pageSize = data.optInt("page_size", defaultPageSize)
        )
    }

    fun record(response: JSONObject): CommunicationRecordDto = recordItem(response.requireObject("data"))

    private fun recordItem(data: JSONObject) = CommunicationRecordDto(
        id = data.requireInt("id"),
        deviceId = data.optInt("device_id"),
        deviceName = data.optStringClean("device_name"),
        model = data.optInt("dev_model"),
        groupId = data.optNullableInt("group_id"),
        groupName = data.optStringClean("group_name"),
        username = data.optStringClean("username"),
        nickname = data.optStringClean("nickname"),
        startedAt = data.optStringClean("start_time"),
        durationMs = data.optLong("duration_ms"),
        messageType = data.optInt("msg_type"),
        text = data.optStringClean("text_content"),
        audioUrl = data.optStringClean("audio_url")
    )
}

private const val DEFAULT_ACCESS_POINT_PRIORITY = 100
private const val FIRST_VALID_ID = 1
private const val MAX_UDP_PORT = 65_535
private const val MIN_UDP_PORT = 1
