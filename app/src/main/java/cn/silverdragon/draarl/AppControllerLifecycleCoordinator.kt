package cn.silverdragon.draarl

import java.util.concurrent.atomic.AtomicBoolean

internal class AppControllerLifecycleCoordinator(
    private val disposed: AtomicBoolean,
    private val removeScheduledCallbacks: () -> Unit,
    private val cancelOwnedRequests: () -> Unit,
    private val closeActions: List<AppControllerCloseAction>
) {
    fun close() {
        if (!disposed.compareAndSet(false, true)) return

        var failure: Throwable? = null
        val actions = buildList {
            add(removeScheduledCallbacks)
            add(cancelOwnedRequests)
            closeActions.forEach { closeAction -> add(closeAction::runIfInitialized) }
        }
        actions.forEach { action ->
            runCatching(action).onFailure { error ->
                val primaryFailure = failure
                if (primaryFailure == null) {
                    failure = error
                } else {
                    primaryFailure.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
    }
}

internal class AppControllerCloseAction(
    private val isInitialized: () -> Boolean = { true },
    private val close: () -> Unit
) {
    fun runIfInitialized() {
        if (isInitialized()) close()
    }
}
