package cn.silverdragon.draarl.tools.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleGattOperationQueueTest {
    @Test
    fun descriptorsReadsAndWritesShareOneSerialQueue() {
        val started = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val queue = BleGattOperationQueue({ {} }, failures::add)
        listOf("cccd-status", "cccd-rpc", "read-status", "write-auth", "write-rpc").forEach { key ->
            queue.enqueue(key, {
                started += key
                true
            })
        }
        assertEquals(listOf("cccd-status"), started)
        queue.complete("read-status", true)
        assertEquals(1, started.size)
        listOf("cccd-status", "cccd-rpc", "read-status", "write-auth", "write-rpc").forEach { queue.complete(it, true) }
        assertEquals(listOf("cccd-status", "cccd-rpc", "read-status", "write-auth", "write-rpc"), started)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun timeoutDropsQueuedWritesAndStaleTimersCannotBreakNewConnection() {
        val timers = mutableListOf<() -> Unit>()
        val failures = mutableListOf<String>()
        val started = mutableListOf<String>()
        val queue = BleGattOperationQueue({ task ->
            timers += task;
            {}
        }, failures::add)
        queue.enqueue("read", {
            started += "read"
            true
        })
        queue.enqueue("write", {
            started += "write"
            true
        })
        timers.first().invoke()
        assertEquals(listOf("read"), started)
        assertEquals(1, failures.size)
        queue.enqueue("new", {
            started += "new"
            true
        })
        timers.first().invoke()
        assertEquals(1, failures.size)
        queue.complete("new", true)
        assertEquals(listOf("read", "new"), started)
    }

    @Test
    fun failedSubmissionStopsTheQueue() {
        val errors = mutableListOf<String>()
        val queue = BleGattOperationQueue({ {} }, errors::add)
        queue.enqueue("descriptor", { false })
        assertEquals(1, errors.size)
    }
}
