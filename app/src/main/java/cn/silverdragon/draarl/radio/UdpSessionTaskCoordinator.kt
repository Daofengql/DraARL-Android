package cn.silverdragon.draarl.radio

internal class UdpSessionTaskCoordinator(private val scheduler: RadioScheduler) {
    private var heartbeatTask: RadioScheduledTask? = null
    private var watchdogTask: RadioScheduledTask? = null
    private var reconnectTask: RadioScheduledTask? = null
    private var pttTimeoutTask: RadioScheduledTask? = null

    @Synchronized
    fun startSession(
        heartbeatIntervalMillis: Long,
        watchdogIntervalMillis: Long,
        heartbeat: () -> Unit,
        watchdog: () -> Unit
    ) {
        stopSessionLocked()
        heartbeatTask = scheduler.scheduleWithFixedDelay(
            initialDelayMillis = 0,
            delayMillis = heartbeatIntervalMillis,
            task = heartbeat
        )
        watchdogTask = runCatching {
            scheduler.scheduleWithFixedDelay(
                initialDelayMillis = watchdogIntervalMillis,
                delayMillis = watchdogIntervalMillis,
                task = watchdog
            )
        }.getOrElse { error ->
            stopSessionLocked()
            throw error
        }
    }

    @Synchronized
    fun stopSession() = stopSessionLocked()

    fun execute(task: () -> Unit) = scheduler.execute(task)

    fun scheduleReconnect(delayMillis: Long, shouldKeep: () -> Boolean, reconnect: () -> Unit) {
        val scheduled = scheduler.schedule(delayMillis) {
            clearReconnectTask()
            reconnect()
        }
        synchronized(this) {
            reconnectTask?.cancel()
            if (shouldKeep()) {
                reconnectTask = scheduled
            } else {
                scheduled.cancel()
            }
        }
    }

    @Synchronized
    fun cancelReconnect() {
        reconnectTask?.cancel()
        reconnectTask = null
    }

    @Synchronized
    fun schedulePttTimeout(delayMillis: Long, timeout: () -> Unit) {
        pttTimeoutTask?.cancel()
        pttTimeoutTask = scheduler.schedule(delayMillis) {
            clearPttTimeoutTask()
            timeout()
        }
    }

    @Synchronized
    fun cancelPttTimeout() {
        pttTimeoutTask?.cancel()
        pttTimeoutTask = null
    }

    @Synchronized
    fun close() {
        stopSessionLocked()
        cancelReconnect()
        scheduler.close()
    }

    @Synchronized
    private fun clearReconnectTask() {
        reconnectTask = null
    }

    @Synchronized
    private fun clearPttTimeoutTask() {
        pttTimeoutTask = null
    }

    private fun stopSessionLocked() {
        heartbeatTask?.cancel()
        watchdogTask?.cancel()
        pttTimeoutTask?.cancel()
        heartbeatTask = null
        watchdogTask = null
        pttTimeoutTask = null
    }
}
