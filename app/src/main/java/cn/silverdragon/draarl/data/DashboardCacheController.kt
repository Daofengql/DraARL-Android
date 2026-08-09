package cn.silverdragon.draarl.data

import cn.silverdragon.draarl.concurrency.ControllerTaskRunner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal class DashboardCacheController(
    private val cache: DashboardCache,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    private val applyDashboard: (DashboardData) -> Unit
) {
    private val loadTasks = ControllerTaskRunner(scope, ioDispatcher) {}
    private val writeTasks = ControllerTaskRunner(scope, ioDispatcher) {}
    private var activeUserId: Int? = null
    private var revision = 0
    private var closed = false

    fun onUserChanged(userId: Int?) {
        if (closed || activeUserId == userId) return
        activeUserId = userId
        val loadRevision = ++revision
        loadTasks.cancel()
        applyDashboard(DashboardData())
        if (userId == null || userId <= 0) return
        loadTasks.launch(
            operation = { cache.load(userId) },
            onSuccess = { cached ->
                if (activeUserId == userId && revision == loadRevision) {
                    applyDashboard(cached ?: DashboardData())
                }
            },
            onFailure = {}
        )
    }

    fun store(userId: Int, data: DashboardData) {
        if (closed || activeUserId != userId) return
        revision++
        loadTasks.cancel()
        writeTasks.enqueue(operation = { cache.save(userId, data) })
    }

    fun close() {
        if (closed) return
        closed = true
        activeUserId = null
        revision++
        loadTasks.close()
        writeTasks.close()
    }
}
