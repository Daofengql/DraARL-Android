package cn.silverdragon.draarl.session

import cn.silverdragon.draarl.data.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SessionOperationTimeoutException : Exception()

internal class SessionOperationRunner(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher
) {
    private var operationJob: Job? = null
    private val backgroundJobs = mutableSetOf<Job>()
    private var generation = 0
    private var closed = false

    fun launch(operation: () -> Session, timeoutMillis: Long? = null, onResult: (Result<Session>) -> Unit) {
        operationJob?.cancel()
        timeoutJob?.cancel()
        val requestGeneration = ++generation
        operationJob = scope.launch {
            val result = runCatching { withContext(ioDispatcher) { operation() } }
            result.exceptionOrNull()?.let { error ->
                if (error is CancellationException) throw error
            }
            complete(requestGeneration, result, onResult)
        }
        timeoutJob = timeoutMillis?.takeIf { it > 0L }?.let { timeout ->
            scope.launch {
                delay(timeout)
                val job = operationJob
                val timedOut = complete(
                    requestGeneration,
                    Result.failure(SessionOperationTimeoutException()),
                    onResult
                )
                if (timedOut) job?.cancel()
            }
        }
    }

    fun invalidate() {
        generation++
        operationJob?.cancel()
        timeoutJob?.cancel()
        operationJob = null
        timeoutJob = null
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

    private fun complete(
        requestGeneration: Int,
        result: Result<Session>,
        onResult: (Result<Session>) -> Unit
    ): Boolean {
        if (closed || requestGeneration != generation) return false
        generation++
        operationJob = null
        timeoutJob?.cancel()
        timeoutJob = null
        onResult(result)
        return true
    }

    private var timeoutJob: Job? = null
}
