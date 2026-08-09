package cn.silverdragon.draarl.radio.messages

import androidx.compose.runtime.Immutable
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.data.VoicePlaybackQueue

@Immutable
data class RadioMessageUiState(
    val messages: List<RadioMessage> = emptyList(),
    val historyLoading: Boolean = false,
    val historyHasMore: Boolean = true,
    val syncError: String = "",
    val publicProfiles: Map<String, User> = emptyMap()
) {
    val unplayedVoiceCount: Int
        get() = messages.count(VoicePlaybackQueue::isUnplayed)
}

sealed interface RadioMessageEvent {
    data object Refresh : RadioMessageEvent

    data object LoadOlder : RadioMessageEvent

    data object MarkAllPlayed : RadioMessageEvent

    data class OnlineDevicesChanged(val usernames: List<String>) : RadioMessageEvent

    data object BeforeCacheClear : RadioMessageEvent

    data object AfterCacheClear : RadioMessageEvent
}

@Immutable
data class RadioMessageAccount(val key: String, val user: User)

@Immutable
data class RadioMessageIdentityContext(val onlineDevices: List<OnlineDevice>, val currentSsid: Int, val muted: Boolean)
