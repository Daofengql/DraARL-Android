package cn.silverdragon.draarl.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ControllerTaskRunner(
    parentScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val onRunningChanged: (Boolean) -> Unit
) {
    private val controllerJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + controllerJob)
    private var operationJob: Job? = null
    private var generation = 0
    private var closed = false

    fun <T> launch(operation: () -> T, onSuccess: (T) -> Unit, onFailure: (Throwable) -> Unit): Boolean {
        if (closed || operationJob?.isActive == true) return false
        val requestGeneration = ++generation
        onRunningChanged(true)
        operationJob = scope.launch {
            val result = runCatching { withContext(ioDispatcher) { operation() } }
            result.exceptionOrNull()?.let { failure ->
                if (failure is CancellationException) throw failure
            }
            if (closed || requestGeneration != generation) return@launch
            operationJob = null
            onRunningChanged(false)
            result.fold(onSuccess, onFailure)
        }
        return true
    }

    fun <T> replace(operation: () -> T, onSuccess: (T) -> Unit, onFailure: (Throwable) -> Unit) {
        cancel()
        launch(operation, onSuccess, onFailure)
    }

    fun cancel() {
        generation++
        operationJob?.cancel()
        operationJob = null
        onRunningChanged(false)
    }

    fun close() {
        if (closed) return
        closed = true
        cancel()
        scope.cancel()
    }
}
