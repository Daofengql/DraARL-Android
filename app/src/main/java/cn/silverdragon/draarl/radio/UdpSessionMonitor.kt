package cn.silverdragon.draarl.radio

internal class UdpSessionMonitor(
    private val scheduler: RadioScheduler,
    private val clock: RadioClock,
    private val heartbeatIntervalMillis: Long,
    private val watchdogIntervalMillis: Long,
    private val serverSilenceTimeoutMillis: Long
) {
    private var heartbeatTask: RadioScheduledTask? = null
    private var watchdogTask: RadioScheduledTask? = null

    @Volatile private var lastServerPacketAt = 0L

    @Volatile private var lastPacketSentAt = 0L

    init {
        require(heartbeatIntervalMillis > 0L) { "Heartbeat interval must be positive" }
        require(watchdogIntervalMillis > 0L) { "Watchdog interval must be positive" }
        require(serverSilenceTimeoutMillis > 0L) { "Server silence timeout must be positive" }
    }

    @Synchronized
    fun start(heartbeat: () -> Unit, watchdog: (Long) -> Unit, onServerSilence: () -> Unit) {
        stopLocked()
        // A restarted session must get a fresh silence window. Reusing a packet
        // timestamp from the previous transport can trigger an immediate reconnect.
        lastServerPacketAt = clock.nowMillis()
        heartbeatTask = scheduler.scheduleWithFixedDelay(
            initialDelayMillis = 0L,
            delayMillis = heartbeatIntervalMillis,
            task = heartbeat
        )
        watchdogTask = runCatching {
            scheduler.scheduleWithFixedDelay(
                initialDelayMillis = watchdogIntervalMillis,
                delayMillis = watchdogIntervalMillis
            ) {
                val now = clock.nowMillis()
                watchdog(now)
                if (now - lastServerPacketAt > serverSilenceTimeoutMillis) {
                    onServerSilence()
                }
            }
        }.getOrElse { error ->
            stopLocked()
            throw error
        }
    }

    fun recordServerPacket() {
        lastServerPacketAt = clock.nowMillis()
    }

    fun recordPacketSent() {
        lastPacketSentAt = clock.nowMillis()
    }

    fun lastPacketSentAt(): Long = lastPacketSentAt

    @Synchronized
    fun stop() = stopLocked()

    private fun stopLocked() {
        heartbeatTask?.cancel()
        watchdogTask?.cancel()
        heartbeatTask = null
        watchdogTask = null
    }
}
