package cn.silverdragon.draarl.radio

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

internal fun executeIfActive(
    executor: Executor,
    isActive: () -> Boolean,
    task: () -> Unit,
): Boolean {
    if (!isActive()) return false
    return try {
        executor.execute {
            if (isActive()) task()
        }
        true
    } catch (_: RejectedExecutionException) {
        false
    }
}
