package cn.silverdragon.draarl.data

import cn.silverdragon.draarl.network.ApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface AppDataSource {
    fun devices(): List<Device>
    fun groups(): List<Group>
    fun defaultDeviceGroup(): Int?
    fun communicationStats(): CommunicationStats
    fun communicationTrend(): List<DailyCommunicationStats>
    fun currentUser(): User
}

class ApiAppDataSource(private val api: ApiClient) : AppDataSource {
    override fun devices() = api.getDevices()
    override fun groups() = api.getGroups()
    override fun defaultDeviceGroup() = api.getDefaultDeviceGroup()
    override fun communicationStats() = api.getCommunicationStats()
    override fun communicationTrend() = api.getCommunicationTrend()

    // The coordinator owns state application. Avoid letting an older refresh mutate
    // the persisted session before its generation has been accepted.
    override fun currentUser() = api.getMe(updateSession = false)
}

class AppDataRefresher(private val source: AppDataSource, private val ioDispatcher: CoroutineDispatcher) {
    suspend fun refresh(fallback: AppDataFallback): AppDataSnapshot = coroutineScope {
        val devices = async(ioDispatcher) { capture(source::devices).getOrDefault(fallback.devices) }
        val groups = async(ioDispatcher) { capture(source::groups).getOrDefault(fallback.groups) }
        val defaultGroup = async(ioDispatcher) { capture(source::defaultDeviceGroup) }
        val stats = async(ioDispatcher) { capture(source::communicationStats).getOrNull() }
        val trend = async(ioDispatcher) { capture(source::communicationTrend).getOrDefault(fallback.trend) }
        val user = async(ioDispatcher) { capture(source::currentUser).getOrNull() }
        AppDataSnapshot(
            devices = devices.await(),
            groups = groups.await(),
            defaultDeviceGroup = defaultGroup.await(),
            stats = stats.await(),
            trend = trend.await(),
            user = user.await()
        )
    }

    private fun <T> capture(block: () -> T): Result<T> = runCatching(block).onFailure { failure ->
        if (failure is CancellationException) throw failure
    }
}

data class AppDataFallback(val devices: List<Device>, val groups: List<Group>, val trend: List<DailyCommunicationStats>)

data class AppDataSnapshot(
    val devices: List<Device>,
    val groups: List<Group>,
    val defaultDeviceGroup: Result<Int?>,
    val stats: CommunicationStats?,
    val trend: List<DailyCommunicationStats>,
    val user: User?
)
