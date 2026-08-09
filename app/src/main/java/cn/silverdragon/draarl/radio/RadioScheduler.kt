package cn.silverdragon.draarl.radio

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal fun interface RadioScheduledTask {
    fun cancel()
}

internal interface RadioScheduler {
    fun execute(task: () -> Unit)
    fun schedule(delayMillis: Long, task: () -> Unit): RadioScheduledTask
    fun scheduleWithFixedDelay(initialDelayMillis: Long, delayMillis: Long, task: () -> Unit): RadioScheduledTask
    fun close()
}

internal class ExecutorRadioScheduler(threadCount: Int, threadName: String) : RadioScheduler {
    private val executor = ScheduledThreadPoolExecutor(threadCount) { runnable -> Thread(runnable, threadName) }.apply {
        removeOnCancelPolicy = true
        continueExistingPeriodicTasksAfterShutdownPolicy = false
        executeExistingDelayedTasksAfterShutdownPolicy = false
    }

    override fun execute(task: () -> Unit) {
        executor.execute(task)
    }

    override fun schedule(delayMillis: Long, task: () -> Unit): RadioScheduledTask =
        executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS).asRadioTask()

    override fun scheduleWithFixedDelay(
        initialDelayMillis: Long,
        delayMillis: Long,
        task: () -> Unit
    ): RadioScheduledTask = executor.scheduleWithFixedDelay(
        task,
        initialDelayMillis,
        delayMillis,
        TimeUnit.MILLISECONDS
    ).asRadioTask()

    override fun close() {
        executor.shutdownNow()
    }

    private fun java.util.concurrent.Future<*>.asRadioTask() = RadioScheduledTask { cancel(false) }
}
