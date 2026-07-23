package cn.silverdragon.draarl.data

data class User(
    val id: Int,
    val username: String,
    val nickname: String = "",
    val callsign: String = "",
    val email: String = "",
    val role: String = "user",
    val approvalStatus: Int = 0,
    val reviewNote: String = "",
    val avatarUrl: String = "",
    val address: String = "",
    val phone: String = "",
    val introduction: String = "",
    val dmrId: Int = 0,
    val lastGroupId: Int = 999,
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
)

data class Group(
    val id: Int,
    val name: String,
    val type: Int,
    val status: Int,
    val note: String = "",
    val ownerCallsign: String = "",
    val joined: Boolean = false,
    val owner: Boolean = false,
    val requiresPassword: Boolean = false,
    val onlineCount: Int = 0,
    val totalCount: Int = 0,
) {
    val isPrivate: Boolean get() = type == 2
}

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
    val transmitting: Boolean = false,
    val speaker: String = "",
    val error: String = "",
) {
    val connected: Boolean get() = phase == RadioConnectionPhase.CONNECTED
}

enum class RadioMessageType { TEXT, VOICE, SYSTEM }

data class RadioMessage(
    val id: String,
    val type: RadioMessageType,
    val senderCallsign: String,
    val senderSsid: Int,
    val content: String,
    val timestamp: Long,
    val mine: Boolean,
    val durationMs: Long = 0,
)

data class DashboardData(
    val devices: Int = 0,
    val onlineDevices: Int = 0,
    val groups: Int = 0,
    val communications: Int = 0,
    val communicationDurationMs: Long = 0,
    val platform: PlatformInfo = PlatformInfo(),
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
