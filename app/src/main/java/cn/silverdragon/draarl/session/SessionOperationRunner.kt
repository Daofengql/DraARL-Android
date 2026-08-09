package cn.silverdragon.draarl.session

import cn.silverdragon.draarl.data.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SessionOperationRunner(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    private var operationJob: Job? = null
    private val backgroundJobs = mutableSetOf<Job>()
    private var generation = 0
    private var closed = false

    fun launch(operation: () -> Session, onResult: (Result<Session>) -> Unit) {
        operationJob?.cancel()
        val requestGeneration = ++generation
        operationJob = scope.launch {
            val result = runCatching { withContext(ioDispatcher) { operation() } }
            result.exceptionOrNull()?.let { error ->
                if (error is CancellationException) throw error
            }
            if (closed || requestGeneration != generation) return@launch
            operationJob = null
            onResult(result)
        }
    }

    fun invalidate() {
        generation++
        operationJob?.cancel()
        operationJob = null
    }

    fun runInBackground(operation: () -> Unit) {
        val job = scope.launch(ioDispatcher) { operation() }
        synchronized(backgroundJobs) { backgroundJobs += job }
        job.invokeOnCompletion {
            synchronized(backgroundJobs) { backgroundJobs -= job }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        invalidate()
        val jobs = synchronized(backgroundJobs) {
            backgroundJobs.toList().also { backgroundJobs.clear() }
        }
        jobs.forEach(Job::cancel)
    }
}
