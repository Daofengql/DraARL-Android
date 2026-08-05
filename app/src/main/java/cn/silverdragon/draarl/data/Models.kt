package cn.silverdragon.draarl.data

data class User(
    val id: Int,
    val username: String,
    val nickname: String = "",
    val callsign: String = "",
    val email: String = "",
    val emailVerified: Boolean = false,
    val role: String = "user",
    val approvalStatus: Int = 0,
    val reviewNote: String = "",
    val avatarUrl: String = "",
    val address: String = "",
    val phone: String = "",
    val introduction: String = "",
    val dmrId: Int = 0,
    val mdcId: String = "",
    val birthday: String = "",
    val sex: Int = 0, // 0=保密, 1=男, 2=女
    val alarmMsg: Boolean = false,
    val lastGroupId: Int = 999,
    val status: Int = 1, // 1=正常, 0=禁用
    val lastLoginTime: String = "",
    val lastLoginIp: String = "",
    val lastLoginIpLocation: String = "",
) {
    val displayName: String get() = nickname.ifBlank { callsign.ifBlank { username } }
    val isApproved: Boolean get() = approvalStatus == 1
}

data class Session(
    val baseUrl: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
    val user: User,
)

data class CaptchaChallenge(
    val id: String,
    val imageBase64: String,
    val expiresInSeconds: Int,
)

data class EmailCodeSession(
    val sessionId: String,
    val expiresInSeconds: Int,
)

data class RegistrationResult(
    val id: Int,
    val username: String,
    val nickname: String,
    val approvalStatus: Int,
    val devicePassword: String,
)

data class AccessPoint(
    val id: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val region: String = "",
    val network: String = "",
    val priority: Int = 100,
) {
    val address: String get() = "$host:$port"
}

data class Device(
    val id: Int,
    val name: String,
    val callsign: String,
    val ssid: Int,
    val model: Int,
    val groupId: Int,
    val online: Boolean,
    val enabled: Boolean,
    val disableSend: Boolean = false,
    val disableReceive: Boolean = false,
    val qth: String = "",
    val note: String = "",
    val onlineTime: String = "",
    val entryName: String = "",
    val priority: Int = 0,
    val lastOnlineIp: String = "",
    val lastOnlineIpLocation: String = "",
    val entryId: String = "",
    val entryMode: String = "",
    val entrySeenAt: String = "",
    val ownerId: Int = 0,
    val ownerName: String = "",
    val ownerCallsign: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class Group(
    val id: Int,
    val name: String,
    val type: Int,
    val status: Int,
    val note: String = "",
    val ownerId: Int = 0,
    val ownerCallsign: String = "",
    val joined: Boolean = false,
    val owner: Boolean = false,
    val requiresPassword: Boolean = false,
    val onlineCount: Int = 0,
    val totalCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    val isPrivate: Boolean get() = type == 2
}

data class DevicePasswordInfo(
    val password: String,
    val hasPassword: Boolean,
    val isNew: Boolean,
    val createdAt: String,
)

data class ReplaceableDevice(
    val deviceId: Int,
    val name: String,
    val callsign: String,
    val ssid: Int,
    val lastOnlineIp: String = "",
    val onlineTime: String = "",
)

data class DeviceBindPreview(
    val deviceMac: String,
    val callsign: String,
    val message: String,
    val availableSsids: List<Int>,
    val recommendedSsid: Int,
    val replaceableDevices: List<ReplaceableDevice>,
)

data class DeviceBindResult(
    val message: String,
    val ssid: Int?,
    val username: String,
    val devicePassword: String,
    val dmrId: Int,
)

data class OnlineDevice(
    val id: Int,
    val username: String,
    val callsign: String,
    val ssid: Int,
    val nickname: String,
    val model: Int,
    val ghost: Boolean,
    val disableSend: Boolean,
    val disableReceive: Boolean,
    val lastActivity: String,
)

data class CommunicationStats(
    val totalCount: Int = 0,
    val totalSize: Long = 0,
    val totalDurationMs: Long = 0,
)

data class CommunicationRecord(
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
    val audioUrl: String,
)

data class CommunicationRecordPage(
    val records: List<CommunicationRecord>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
) {
    val hasMore: Boolean get() = page * pageSize < total
}

data class RadioSession(
    val sessionId: String,
    val clientInstanceId: String,
    val legacy: Boolean,
    val model: Int,
    val ssid: Int,
    val transport: String,
    val protocolVersion: Int,
    val capabilities: List<String>,
    val txGroupId: Int,
    val rxGroupIds: List<Int>,
    val disableSend: Boolean,
    val disableReceive: Boolean,
)

data class ChannelMessageSender(
    val userId: Int?,
    val username: String,
    val callsign: String,
    val nickname: String,
    val ssid: Int,
    val model: Int,
    val ghost: Boolean,
)

data class ChannelMessage(
    val id: Int,
    val messageType: String,
    val sourceGroupId: Int,
    val sourceGroupName: String,
    val requestedGroupId: Int,
    val sender: ChannelMessageSender,
    val sentAt: String,
    val endTime: String,
    val durationMs: Long,
    val text: String,
    val audioUrl: String,
    val status: Int,
)

data class ChannelMessagePage(
    val messages: List<ChannelMessage>,
    val nextCursor: String,
    val hasMore: Boolean,
    val serverTime: String,
)

data class PlatformInfo(
    val name: String = "DraARL 麟链",
    val version: String = "",
    val protocolVersion: String = "DraARLv1",
)

enum class RadioConnectionPhase {
    DISCONNECTED,
    DISCOVERING,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    ERROR,
}

data class RadioStatus(
    val phase: RadioConnectionPhase = RadioConnectionPhase.DISCONNECTED,
    val endpoint: String = "",
    val callsign: String = "",
    val ssid: Int = 101,
    val groupId: Int = 999,
    val sessionId: String = "",
    val clientInstanceId: String = "",
    val receiveGroupIds: List<Int> = emptyList(),
    val transmitting: Boolean = false,
    val speaker: String = "",
    val error: String = "",
) {
    val connected: Boolean get() = phase == RadioConnectionPhase.CONNECTED
}

enum class RadioMessageType { TEXT, VOICE, SYSTEM }

enum class RadioMessageSyncState { LOCAL, CONFIRMED }

data class RadioMessage(
    val id: String,
    val type: RadioMessageType,
    val senderCallsign: String,
    val senderSsid: Int,
    val senderUsername: String = "",
    val senderNickname: String = "",
    val content: String,
    val timestamp: Long,
    val mine: Boolean,
    val durationMs: Long = 0,
    val audioUrl: String = "",
    val audioCacheKey: String = "",
    val serverRecordId: Int? = null,
    val syncState: RadioMessageSyncState = RadioMessageSyncState.LOCAL,
    val groupId: Int = 0, // 0 表示未知，用于兼容旧数据
    val played: Boolean = false,
)

data class DashboardData(
    val devices: Int = 0,
    val onlineDevices: Int = 0,
    val groups: Int = 0,
    val communications: Int = 0,
    val communicationDurationMs: Long = 0,
    val communicationTrend: List<DailyCommunicationStats> = emptyList(),
)

data class DailyCommunicationStats(
    val date: String,
    val count: Int,
    val durationMs: Long,
)

fun deviceModelName(model: Int): String = when (model) {
    1 -> "ESP32 射频版"
    2 -> "ESP32 网络版"
    100 -> "微信小程序"
    101 -> "Android 客户端"
    102 -> "iOS 客户端"
    103 -> "Windows 客户端"
    104 -> "macOS 客户端"
    105 -> "Web 客户端"
    236 -> "南山对讲桥"
    237 -> "涛涛对讲桥"
    238 -> "本视对讲桥"
    239 -> "NRL2 桥"
    else -> "设备 $model"
}
