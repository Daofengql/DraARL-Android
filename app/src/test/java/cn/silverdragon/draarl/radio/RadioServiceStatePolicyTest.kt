package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.RadioConnectionPhase
import cn.silverdragon.draarl.data.RadioStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioServiceStatePolicyTest {
    @Test
    fun activeConnectionPhasesEnsureForegroundService() {
        val activePhases = listOf(
            RadioConnectionPhase.CONNECTING,
            RadioConnectionPhase.AUTHENTICATING,
            RadioConnectionPhase.CONNECTED,
            RadioConnectionPhase.RECONNECTING
        )

        activePhases.forEach { phase ->
            assertEquals(
                RadioServiceForegroundAction.ENSURE,
                RadioServiceStatePolicy.foregroundAction(phase, overlayEnabled = false)
            )
        }
    }

    @Test
    fun disconnectedServiceStopsUnlessOverlayRemainsEnabled() {
        assertEquals(
            RadioServiceForegroundAction.STOP,
            RadioServiceStatePolicy.foregroundAction(RadioConnectionPhase.DISCONNECTED, overlayEnabled = false)
        )
        assertEquals(
            RadioServiceForegroundAction.ENSURE,
            RadioServiceStatePolicy.foregroundAction(RadioConnectionPhase.DISCONNECTED, overlayEnabled = true)
        )
    }

    @Test
    fun discoveryAndErrorOnlyUpdateExistingNotification() {
        listOf(RadioConnectionPhase.DISCOVERING, RadioConnectionPhase.ERROR).forEach { phase ->
            assertEquals(
                RadioServiceForegroundAction.UPDATE,
                RadioServiceStatePolicy.foregroundAction(phase, overlayEnabled = false)
            )
        }
    }

    @Test
    fun notificationTitleUsesOperationalPriority() {
        val connected = RadioStatus(phase = RadioConnectionPhase.CONNECTED)
        val reconnecting = RadioStatus(phase = RadioConnectionPhase.RECONNECTING)

        assertEquals("正在发射", RadioServiceStatePolicy.notificationTitle(connected.copy(transmitting = true), true))
        assertEquals(
            "正在接收 BG5DRA-7",
            RadioServiceStatePolicy.notificationTitle(connected.copy(speaker = "BG5DRA-7"), true)
        )
        assertEquals("DraARL 电台在线", RadioServiceStatePolicy.notificationTitle(connected, true))
        assertEquals("DraARL 正在重连", RadioServiceStatePolicy.notificationTitle(reconnecting, true))
        assertEquals("悬浮 PTT 已开启", RadioServiceStatePolicy.notificationTitle(RadioStatus(), true))
        assertEquals("DraARL 正在连接", RadioServiceStatePolicy.notificationTitle(RadioStatus(), false))
    }
}
