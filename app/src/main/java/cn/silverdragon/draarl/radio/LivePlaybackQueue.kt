package cn.silverdragon.draarl.radio

import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/** One scheduled worker and at most five packets waiting for blocking playback. */
internal class LivePlaybackQueue<T>(
    private val executor: ExecutorService,
    private val isActive: () -> Boolean,
    private val play: (T, Boolean) -> Unit,
    private val capacity: Int = 5
) {
    private val lock = Any()
    private val packets = ArrayDeque<T>()
    private var scheduled = false
    private var resetAfterDrop = false
    val droppedBacklog = AtomicInteger()

    fun submit(packet: T): Boolean = synchronized(lock) {
        if (!isActive()) return false
        if (packets.size == capacity) {
            packets.removeFirst()
            droppedBacklog.incrementAndGet()
            resetAfterDrop = true
        }
        packets.addLast(packet)
        if (scheduled) return true
        scheduled = true
        schedule()
    }

    fun clear() = synchronized(lock) {
        packets.clear()
        resetAfterDrop = true
    }

    private fun schedule(): Boolean = try {
        executor.execute(::drainOne)
        true
    } catch (_: RejectedExecutionException) {
        synchronized(lock) { packets.clear(); scheduled = false }
        false
    }

    private fun drainOne() {
        val next = synchronized(lock) {
            if (!isActive()) packets.clear()
            val packet = packets.pollFirst()
            if (packet == null) { scheduled = false; return }
            val reset = resetAfterDrop
            resetAfterDrop = false
            packet to reset
        }
        try {
            play(next.first, next.second)
        } finally {
            synchronized(lock) {
                if (!isActive()) packets.clear()
                if (packets.isEmpty()) scheduled = false else schedule()
            }
        }
    }
}
