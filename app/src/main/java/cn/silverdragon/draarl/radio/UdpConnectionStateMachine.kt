package cn.silverdragon.draarl.radio

internal enum class UdpConnectionStage {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    ONLINE,
    RECONNECT_DELAY,
    RECONNECTING,
    ERROR,
    CLOSED
}

internal data class UdpConnectionState(
    val stage: UdpConnectionStage = UdpConnectionStage.DISCONNECTED,
    val generation: Int = 0,
    val config: RadioConnectionConfig? = null
)

internal data class UdpConnectionAttempt(
    val generation: Int,
    val config: RadioConnectionConfig,
    val reconnecting: Boolean
)

internal sealed interface UdpConnectionEvent {
    data class Connect(val config: RadioConnectionConfig) : UdpConnectionEvent
    data class AuthenticationStarted(val generation: Int) : UdpConnectionEvent
    data class Authenticated(val generation: Int) : UdpConnectionEvent
    data class AuthenticationFailed(val generation: Int) : UdpConnectionEvent
    data class ReconnectScheduled(val generation: Int) : UdpConnectionEvent
    data class ReconnectStarted(val generation: Int) : UdpConnectionEvent
    data class ReconnectFailed(val generation: Int) : UdpConnectionEvent
    data class AccessTokenChanged(val token: String) : UdpConnectionEvent
    data class RoutingChanged(val groupId: Int) : UdpConnectionEvent
    data object Disconnect : UdpConnectionEvent
    data object Close : UdpConnectionEvent
}

internal class UdpConnectionStateMachine {
    @Volatile
    private var state = UdpConnectionState()

    @Synchronized
    fun connect(config: RadioConnectionConfig): UdpConnectionAttempt? {
        val next = reduce(state, UdpConnectionEvent.Connect(config))
        if (next == state) return null
        state = next
        return next.attempt(reconnecting = false)
    }

    @Synchronized
    fun scheduleReconnect(expectedGeneration: Int): Int? {
        val next = reduce(state, UdpConnectionEvent.ReconnectScheduled(expectedGeneration))
        if (next == state) return null
        state = next
        return next.generation
    }

    @Synchronized
    fun startReconnect(expectedGeneration: Int): UdpConnectionAttempt? {
        val next = reduce(state, UdpConnectionEvent.ReconnectStarted(expectedGeneration))
        if (next == state) return null
        state = next
        return next.attempt(reconnecting = true)
    }

    @Synchronized
    fun dispatch(event: UdpConnectionEvent): Boolean {
        val next = reduce(state, event)
        if (next == state) return false
        state = next
        return true
    }

    fun generation(): Int = state.generation

    fun isActive(expectedGeneration: Int): Boolean =
        state.generation == expectedGeneration && state.stage !in TERMINAL_STAGES

    fun isWaitingToReconnect(expectedGeneration: Int): Boolean =
        state.generation == expectedGeneration && state.stage == UdpConnectionStage.RECONNECT_DELAY

    fun isClosed(): Boolean = state.stage == UdpConnectionStage.CLOSED

    internal fun snapshot(): UdpConnectionState = state

    private fun UdpConnectionState.attempt(reconnecting: Boolean): UdpConnectionAttempt = UdpConnectionAttempt(
        generation = generation,
        config = requireNotNull(config),
        reconnecting = reconnecting
    )

    private companion object {
        val TERMINAL_STAGES = setOf(UdpConnectionStage.DISCONNECTED, UdpConnectionStage.CLOSED)

        fun reduce(state: UdpConnectionState, event: UdpConnectionEvent): UdpConnectionState = when (event) {
            is UdpConnectionEvent.Connect -> state.connect(event.config)

            is UdpConnectionEvent.AuthenticationStarted -> state.transition(
                expectedGeneration = event.generation,
                from = setOf(UdpConnectionStage.CONNECTING, UdpConnectionStage.RECONNECTING),
                to = UdpConnectionStage.AUTHENTICATING
            )

            is UdpConnectionEvent.Authenticated -> state.transition(
                expectedGeneration = event.generation,
                from = setOf(UdpConnectionStage.AUTHENTICATING),
                to = UdpConnectionStage.ONLINE
            )

            is UdpConnectionEvent.AuthenticationFailed -> state.transition(
                expectedGeneration = event.generation,
                from = setOf(UdpConnectionStage.AUTHENTICATING),
                to = UdpConnectionStage.ERROR
            )

            is UdpConnectionEvent.ReconnectScheduled -> state.scheduleReconnect(event.generation)

            is UdpConnectionEvent.ReconnectStarted -> state.transition(
                expectedGeneration = event.generation,
                from = setOf(UdpConnectionStage.RECONNECT_DELAY),
                to = UdpConnectionStage.RECONNECTING
            )

            is UdpConnectionEvent.ReconnectFailed -> state.transition(
                expectedGeneration = event.generation,
                from = setOf(UdpConnectionStage.RECONNECT_DELAY, UdpConnectionStage.RECONNECTING),
                to = UdpConnectionStage.ERROR
            )

            is UdpConnectionEvent.AccessTokenChanged -> state.copy(
                config = state.config?.copy(accessToken = event.token)
            )

            is UdpConnectionEvent.RoutingChanged -> state.copy(
                config = state.config?.copy(groupId = event.groupId)
            )

            UdpConnectionEvent.Disconnect -> when (state.stage) {
                UdpConnectionStage.CLOSED -> state

                else -> UdpConnectionState(
                    stage = UdpConnectionStage.DISCONNECTED,
                    generation = state.generation + 1
                )
            }

            UdpConnectionEvent.Close -> when (state.stage) {
                UdpConnectionStage.CLOSED -> state

                else -> UdpConnectionState(
                    stage = UdpConnectionStage.CLOSED,
                    generation = state.generation + 1
                )
            }
        }

        fun UdpConnectionState.connect(config: RadioConnectionConfig): UdpConnectionState {
            if (stage == UdpConnectionStage.CLOSED) return this
            val duplicate = this.config == config && stage in setOf(
                UdpConnectionStage.CONNECTING,
                UdpConnectionStage.AUTHENTICATING,
                UdpConnectionStage.ONLINE,
                UdpConnectionStage.RECONNECT_DELAY,
                UdpConnectionStage.RECONNECTING
            )
            return if (duplicate) {
                this
            } else {
                UdpConnectionState(
                    stage = UdpConnectionStage.CONNECTING,
                    generation = generation + 1,
                    config = config
                )
            }
        }

        fun UdpConnectionState.scheduleReconnect(expectedGeneration: Int): UdpConnectionState {
            val unavailable = config == null || stage in TERMINAL_STAGES
            return if (
                generation != expectedGeneration ||
                unavailable ||
                stage == UdpConnectionStage.RECONNECT_DELAY
            ) {
                this
            } else {
                copy(
                    stage = UdpConnectionStage.RECONNECT_DELAY,
                    generation = generation + 1
                )
            }
        }

        fun UdpConnectionState.transition(
            expectedGeneration: Int,
            from: Set<UdpConnectionStage>,
            to: UdpConnectionStage
        ): UdpConnectionState = if (generation == expectedGeneration && stage in from) copy(stage = to) else this
    }
}
