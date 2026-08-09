package cn.silverdragon.draarl.radio

import cn.silverdragon.draarl.data.RadioStatus
import cn.silverdragon.draarl.protocol.DraarlProtocol
import java.util.ArrayDeque

internal data class UdpSessionIdentity(
    val sessionTag: Long = 0L,
    val username: String = "",
    val ssid: Int = DraarlProtocol.SSID_ANDROID
)

internal data class UdpSessionSnapshot(
    val connection: UdpConnectionState,
    val status: RadioStatus,
    val identity: UdpSessionIdentity
)

internal class UdpSessionStateContext(private val onStatus: (RadioStatus) -> Unit) {
    private val connection = UdpConnectionStateMachine()
    private val pendingStatuses = ArrayDeque<RadioStatus>()
    private var status = RadioStatus()
    private var identity = UdpSessionIdentity()
    private var publishingStatus = false

    @Synchronized
    fun connect(config: RadioConnectionConfig): UdpConnectionAttempt? = connection.connect(config)

    @Synchronized
    fun scheduleReconnect(expectedGeneration: Int): Int? = connection.scheduleReconnect(expectedGeneration)

    @Synchronized
    fun startReconnect(expectedGeneration: Int): UdpConnectionAttempt? = connection.startReconnect(expectedGeneration)

    @Synchronized
    fun dispatch(event: UdpConnectionEvent): Boolean {
        val changed = connection.dispatch(event)
        if (changed && (event is UdpConnectionEvent.Disconnect || event is UdpConnectionEvent.Close)) {
            identity = UdpSessionIdentity()
        }
        return changed
    }

    fun transition(
        event: UdpConnectionEvent,
        expectedGeneration: Int,
        authenticatedIdentity: UdpSessionIdentity? = null,
        transform: (RadioStatus) -> RadioStatus
    ): Boolean {
        var shouldPublish = false
        val transitioned = synchronized(this) {
            if (!connection.dispatch(event) || !connection.isActive(expectedGeneration)) {
                false
            } else {
                if (authenticatedIdentity != null) identity = authenticatedIdentity
                shouldPublish = updateLocked(transform(status))
                true
            }
        }
        if (shouldPublish) publishPendingStatuses()
        return transitioned
    }

    @Synchronized
    fun snapshot(): UdpSessionSnapshot = UdpSessionSnapshot(connection.snapshot(), status, identity)

    fun updateStatus(transform: (RadioStatus) -> RadioStatus) {
        val shouldPublish = synchronized(this) { updateLocked(transform(status)) }
        if (shouldPublish) publishPendingStatuses()
    }

    fun updateStatusIfActive(expectedGeneration: Int, transform: (RadioStatus) -> RadioStatus): Boolean {
        var shouldPublish = false
        val active = synchronized(this) {
            if (!connection.isActive(expectedGeneration)) {
                false
            } else {
                shouldPublish = updateLocked(transform(status))
                true
            }
        }
        if (shouldPublish) publishPendingStatuses()
        return active
    }

    @Synchronized
    fun clearIdentity() {
        identity = UdpSessionIdentity()
    }

    private fun updateLocked(newStatus: RadioStatus): Boolean {
        if (newStatus == status) return false
        status = newStatus
        pendingStatuses += newStatus
        return if (publishingStatus) {
            false
        } else {
            publishingStatus = true
            true
        }
    }

    private fun publishPendingStatuses() {
        while (true) {
            val next = synchronized(this) {
                if (pendingStatuses.isEmpty()) {
                    publishingStatus = false
                    null
                } else {
                    pendingStatuses.removeFirst()
                }
            } ?: return
            var delivered = false
            try {
                onStatus(next)
                delivered = true
            } finally {
                if (!delivered) {
                    synchronized(this) {
                        pendingStatuses.clear()
                        publishingStatus = false
                    }
                }
            }
        }
    }
}
