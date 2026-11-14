package cn.silverdragon.draarl.tools.ble

/** All callers and the timeout scheduler use the same thread. */
internal class BleGattOperationQueue(
    private val scheduleTimeout: (() -> Unit) -> (() -> Unit),
    private val onFailure: (String) -> Unit
) {
    private data class Operation(val key: String, val start: () -> Boolean, val done: () -> Unit)
    private val pending = ArrayDeque<Operation>()
    private var active: Operation? = null
    private var cancelTimeout: (() -> Unit)? = null
    private var generation = 0

    fun enqueue(key: String, start: () -> Boolean, done: () -> Unit = {}) {
        pending.addLast(Operation(key, start, done))
        advance()
    }

    fun complete(key: String, success: Boolean) {
        val operation = active?.takeIf { it.key == key } ?: return
        cancelTimeout?.invoke()
        cancelTimeout = null
        active = null
        if (!success) {
            fail("蓝牙操作失败：$key")
            return
        }
        operation.done()
        advance()
    }

    fun clear() {
        generation++
        cancelTimeout?.invoke()
        cancelTimeout = null
        active = null
        pending.clear()
    }

    private fun advance() {
        if (active != null) return
        val operation = pending.removeFirstOrNull() ?: return
        active = operation
        val expected = generation
        cancelTimeout = scheduleTimeout {
            if (generation == expected && active === operation) fail("蓝牙操作超时，请重新连接")
        }
        if (!runCatching(operation.start).getOrDefault(false)) fail("无法提交蓝牙操作：${operation.key}")
    }

    private fun fail(message: String) {
        clear()
        onFailure(message)
    }
}
