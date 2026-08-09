package cn.silverdragon.draarl.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

interface DashboardCache {
    fun load(userId: Int): DashboardData?

    fun save(userId: Int, data: DashboardData)
}

class DashboardCacheStore(context: Context) : DashboardCache {
    private val preferences = context.getSharedPreferences("draarl_dashboard_cache", Context.MODE_PRIVATE)

    override fun load(userId: Int): DashboardData? = runCatching {
        if (userId <= 0) return null
        val raw = preferences.getString(key(userId), "").orEmpty()
        if (raw.isBlank()) return null
        val json = JSONObject(raw)
        val trend = json.optJSONArray("trend") ?: JSONArray()
        DashboardData(
            devices = json.optInt("devices"),
            onlineDevices = json.optInt("online_devices"),
            groups = json.optInt("groups"),
            communications = json.optInt("communications"),
            communicationDurationMs = json.optLong("duration_ms"),
            communicationTrend = List(trend.length()) { index ->
                val item = trend.getJSONObject(index)
                DailyCommunicationStats(
                    date = item.optString("date"),
                    count = item.optInt("count"),
                    durationMs = item.optLong("duration_ms")
                )
            }
        )
    }.getOrNull()

    override fun save(userId: Int, data: DashboardData) {
        if (userId <= 0) return
        val trend = JSONArray()
        data.communicationTrend.forEach { item ->
            trend.put(
                JSONObject()
                    .put("date", item.date)
                    .put("count", item.count)
                    .put("duration_ms", item.durationMs)
            )
        }
        val json = JSONObject()
            .put("devices", data.devices)
            .put("online_devices", data.onlineDevices)
            .put("groups", data.groups)
            .put("communications", data.communications)
            .put("duration_ms", data.communicationDurationMs)
            .put("trend", trend)
        preferences.edit().putString(key(userId), json.toString()).apply()
    }

    private fun key(userId: Int) = "dashboard_$userId"
}
