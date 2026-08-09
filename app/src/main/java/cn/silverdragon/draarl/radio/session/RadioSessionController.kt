package cn.silverdragon.draarl.radio.session

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.settings.RadioAudioSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class RadioSessionController internal constructor(
    private val dependencies: RadioSessionDependencies,
    execution: RadioSessionExecution,
    initialAudioSettings: RadioAudioSettings
) {
    private val controllerScope = CoroutineScope(execution.scope.coroutineContext + SupervisorJob())
    private val ownedExecution = execution.copy(scope = controllerScope)
    private val state = RadioSessionStateStore()
    private val configuration = RadioServiceConfiguration(
        dependencies.service.connection,
        initialAudioSettings
    )
    private val routing = RadioRoutingCoordinator(dependencies, ownedExecution, state, configuration)
    private val connection = RadioConnectionCoordinator(dependencies, ownedExecution, state, configuration)
    private val accessPoints = RadioAccessPointCoordinator(
        dependencies = dependencies,
        execution = ownedExecution,
        state = state,
        reconnect = {
            dependencies.service.connection.disconnect()
            connection.connect()
        }
    )
    private val serviceEvents = RadioSessionServiceEventsHandler(
        state = state,
        routing = routing,
        connection = connection,
        configuration = configuration,
        effects = dependencies.effects
    )
    private val serviceCallbacks = RadioSessionServiceCallbacks(controllerScope, serviceEvents)

    val uiState: RadioSessionUiState
        get() = state.uiState

    val controls: RadioServiceControls = dependencies.service.controls

    init {
        dependencies.service.connection.setCallbacks(serviceCallbacks, serviceCallbacks)
        dependencies.service.connection.bind()
    }

    internal fun onAccountChanged(account: RadioSessionAccount?) {
        val accountChanged = state.setAccount(account)
        if (accountChanged) {
            accessPoints.cancel()
            routing.cancel()
        }
        connection.onAccountChanged(accountChanged)
        routing.onAccountChanged(accountChanged)
    }

    fun onAvailableGroupsChanged(groups: List<Group>, preferredGroupId: Int) {
        routing.onGroupsChanged(groups, preferredGroupId)
    }

    fun discoverAccessPoints() = accessPoints.discover()

    fun selectAccessPoint(accessPoint: AccessPoint) = accessPoints.select(accessPoint)

    fun connect() {
        if (uiState.selectedAccessPoint == null) accessPoints.discover()
        connection.connect()
    }

    fun disconnect() = connection.disconnect()

    fun switchGroup(group: Group) = routing.switchGroup(group)

    fun updateRouting(txGroupId: Int, rxGroupIds: Collection<Int>) = routing.updateRouting(txGroupId, rxGroupIds)

    fun applyAudioSettings(settings: RadioAudioSettings) = configuration.applyAudioSettings(settings)

    fun configurePttOverlay(config: RadioPttOverlayConfig): Boolean = configuration.configurePttOverlay(config)

    fun close() {
        state.close()
        accessPoints.close()
        routing.cancel()
        connection.close()
        controllerScope.cancel()
        dependencies.service.connection.close()
    }
}
