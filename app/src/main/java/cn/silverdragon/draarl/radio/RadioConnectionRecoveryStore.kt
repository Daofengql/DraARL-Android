package cn.silverdragon.draarl.radio

import android.content.Context
import androidx.core.content.edit
import cn.silverdragon.draarl.data.AccessPoint

internal data class RadioRecoveryConfig(
    val accessPoint: AccessPoint,
    val groupId: Int
)

/** Stores only the endpoint needed to rebuild an active radio connection. */
internal class RadioConnectionRecoveryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun load(): RadioRecoveryConfig? {
        val config = if (!preferences.getBoolean(KEY_ACTIVE, false)) {
            null
        } else {
            loadActiveConfig()
        }
        return config
    }

    private fun loadActiveConfig(): RadioRecoveryConfig? {
        val host = preferences.getString(KEY_HOST, "").orEmpty()
        val port = preferences.getInt(KEY_PORT, 0)
        val groupId = preferences.getInt(KEY_GROUP_ID, 0)
        val valid = host.isNotBlank() && port in 1..MAX_PORT && groupId > 0
        if (!valid) clear()
        val displayName = preferences.getString(KEY_DISPLAY_NAME, "").orEmpty()
        return if (valid) {
            RadioRecoveryConfig(
                accessPoint = AccessPoint(
                    id = preferences.getString(KEY_ACCESS_POINT_ID, "recovered").orEmpty(),
                    displayName = displayName.ifBlank { "$host:$port" },
                    host = host,
                    port = port
                ),
                groupId = groupId
            )
        } else {
            null
        }
    }

    @Synchronized
    fun save(config: RadioConnectionConfig) {
        preferences.edit(commit = true) {
            putBoolean(KEY_ACTIVE, true)
            putString(KEY_ACCESS_POINT_ID, config.accessPoint.id)
            putString(KEY_DISPLAY_NAME, config.accessPoint.displayName)
            putString(KEY_HOST, config.accessPoint.host)
            putInt(KEY_PORT, config.accessPoint.port)
            putInt(KEY_GROUP_ID, config.groupId)
        }
    }

    @Synchronized
    fun updateGroupId(groupId: Int) {
        if (groupId > 0 && preferences.getBoolean(KEY_ACTIVE, false)) {
            preferences.edit(commit = true) { putInt(KEY_GROUP_ID, groupId) }
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit(commit = true) { clear() }
    }

    private companion object {
        const val PREFERENCES_NAME = "draarl_radio_recovery"
        const val KEY_ACTIVE = "active"
        const val KEY_ACCESS_POINT_ID = "access_point_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_GROUP_ID = "group_id"
        const val MAX_PORT = 65_535
    }
}
