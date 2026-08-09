package cn.silverdragon.draarl.radio

internal class UdpSessionTaskCoordinator(private val scheduler: RadioScheduler) {
    private var reconnectTask: RadioScheduledTask? = null
    private var pttTimeoutTask: RadioScheduledTask? = null

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
        cancelPttTimeout()
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
}
