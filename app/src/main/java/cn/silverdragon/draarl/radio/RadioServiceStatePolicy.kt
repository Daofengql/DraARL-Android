package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioStatus

internal enum class RadioServiceForegroundAction {
    ENSURE,
    UPDATE,
    STOP
}

internal object RadioServiceStatePolicy {
    fun foregroundAction(phase: RadioConnectionPhase, overlayEnabled: Boolean): RadioServiceForegroundAction =
        when (phase) {
            RadioConnectionPhase.CONNECTING,
            RadioConnectionPhase.AUTHENTICATING,
            RadioConnectionPhase.CONNECTED,
            RadioConnectionPhase.RECONNECTING -> RadioServiceForegroundAction.ENSURE

            RadioConnectionPhase.DISCONNECTED -> if (overlayEnabled) {
                RadioServiceForegroundAction.ENSURE
            } else {
                RadioServiceForegroundAction.STOP
            }

            RadioConnectionPhase.DISCOVERING,
            RadioConnectionPhase.ERROR -> RadioServiceForegroundAction.UPDATE
        }

    fun notificationTitle(status: RadioStatus, overlayEnabled: Boolean): String = when {
        status.transmitting -> "正在发射"
        status.speaker.isNotBlank() -> "正在接收 ${status.speaker}"
        status.connected -> "DraARL 电台在线"
        status.phase == RadioConnectionPhase.RECONNECTING -> "DraARL 正在重连"
        overlayEnabled -> "悬浮 PTT 已开启"
        else -> "DraARL 正在连接"
    }
}
