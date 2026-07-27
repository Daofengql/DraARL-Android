package cn.silverdragon.draarl.data

import cn.silverdragon.draarl.network.ApiClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

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

class AppDataRefresher(private val source: AppDataSource, private val executor: Executor) {
    fun refresh(fallback: AppDataFallback): CompletableFuture<AppDataSnapshot> {
        val devices = supply { runCatching(source::devices).getOrDefault(fallback.devices) }
        val groups = supply { runCatching(source::groups).getOrDefault(fallback.groups) }
        val defaultGroup = supply { runCatching(source::defaultDeviceGroup) }
        val stats = supply { runCatching(source::communicationStats).getOrNull() }
        val trend = supply { runCatching(source::communicationTrend).getOrDefault(fallback.trend) }
        val user = supply { runCatching(source::currentUser).getOrNull() }
        return CompletableFuture.allOf(devices, groups, defaultGroup, stats, trend, user).thenApply {
            AppDataSnapshot(
                devices = devices.join(),
                groups = groups.join(),
                defaultDeviceGroup = defaultGroup.join(),
                stats = stats.join(),
                trend = trend.join(),
                user = user.join(),
            )
        }
    }

    private fun <T> supply(block: () -> T): CompletableFuture<T> = CompletableFuture.supplyAsync(block, executor)
}

data class AppDataFallback(
    val devices: List<Device>,
    val groups: List<Group>,
    val trend: List<DailyCommunicationStats>,
)

data class AppDataSnapshot(
    val devices: List<Device>,
    val groups: List<Group>,
    val defaultDeviceGroup: Result<Int?>,
    val stats: CommunicationStats?,
    val trend: List<DailyCommunicationStats>,
    val user: User?,
)
