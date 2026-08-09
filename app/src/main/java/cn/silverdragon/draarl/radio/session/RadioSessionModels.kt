package cn.silverdragon.draarl.radio.session

import androidx.compose.runtime.Immutable
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.radio.AccessPointProbe

@Immutable
data class RadioSessionUiState(
    val accessPoints: List<AccessPoint> = emptyList(),
    val accessPointProbes: List<AccessPointProbe> = emptyList(),
    val selectedAccessPoint: AccessPoint? = null,
    val selectingAccessPoint: Boolean = false,
    val selectedGroupId: Int = DEFAULT_RADIO_GROUP_ID,
    val receiveGroupIds: Set<Int> = setOf(DEFAULT_RADIO_GROUP_ID),
    val routingUpdating: Boolean = false,
    val status: RadioStatus = RadioStatus(),
    val autoConnectAllowed: Boolean = true
)

internal data class RadioSessionAccount(
    val key: String,
    val userId: Int,
    val approved: Boolean,
    val baseUrl: String,
    val accessToken: String,
    val defaultGroupId: Int
)

@Immutable
data class RadioPttOverlayConfig(val enabled: Boolean = false, val visible: Boolean = false, val groupName: String = "")

internal interface RadioSessionEffects {
    fun onContextChanged(groupId: Int, selectionChanged: Boolean)

    fun onStatusChanged(previous: RadioStatus, current: RadioStatus)

    fun onRadioMessage(message: RadioMessage)

    fun onPlaybackState(messageId: String?)

    fun onPlaybackLevel(level: Float)

    fun onTransmitLevel(level: Float)

    fun onCwPreviewState(active: Boolean)

    fun showNotice(message: String)
}

internal data class RadioSessionRoutingResult(val txGroupId: Int, val rxGroupIds: Set<Int>)

internal const val DEFAULT_RADIO_GROUP_ID = 999
