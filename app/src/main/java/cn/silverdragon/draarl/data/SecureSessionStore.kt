package cn.silverdragon.draarl.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): Session? {
        val encrypted = preferences.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val wrapper = JSONObject(encrypted)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(wrapper.getString("iv"), Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plaintext = cipher.doFinal(Base64.decode(wrapper.getString("data"), Base64.NO_WRAP))
            sessionFromJson(JSONObject(plaintext.toString(Charsets.UTF_8)))
        }.getOrElse {
            preferences.edit { remove(KEY_SESSION) }
            null
        }
    }

    @Synchronized
    fun save(session: Session) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val plaintext = sessionToJson(session).toString().toByteArray(Charsets.UTF_8)
        val wrapper = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP))
        preferences.edit {
            putString(KEY_SESSION, wrapper.toString())
            putString(KEY_LAST_SERVER, session.baseUrl)
        }
    }

    fun clearSession() {
        preferences.edit { remove(KEY_SESSION) }
    }

    fun lastServerUrl(): String = preferences.getString(KEY_LAST_SERVER, "") ?: ""

    fun selectedAccessPointId(): String = preferences.getString(KEY_ACCESS_POINT, "") ?: ""

    fun setSelectedAccessPointId(id: String) {
        preferences.edit { putString(KEY_ACCESS_POINT, id) }
    }

    fun selectedGroupId(userId: Int, fallback: Int = 999): Int =
        preferences.getInt("${KEY_GROUP_PREFIX}$userId", fallback).takeIf { it > 0 } ?: fallback

    fun setSelectedGroupId(userId: Int, groupId: Int) {
        if (userId > 0 && groupId > 0) {
            preferences.edit { putInt("${KEY_GROUP_PREFIX}$userId", groupId) }
        }
    }

    fun isMuted(): Boolean = preferences.getBoolean(KEY_MUTED, false)

    fun setMuted(muted: Boolean) {
        preferences.edit { putBoolean(KEY_MUTED, muted) }
    }

    fun isPttOverlayEnabled(): Boolean = preferences.getBoolean(KEY_PTT_OVERLAY, false)

    fun setPttOverlayEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_PTT_OVERLAY, enabled) }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private fun sessionToJson(session: Session) = JSONObject()
        .put("base_url", session.baseUrl)
        .put("access_token", session.accessToken)
        .put("refresh_token", session.refreshToken)
        .put("access_expires_at", session.accessExpiresAt)
        .put("refresh_expires_at", session.refreshExpiresAt)
        .put("user", userToJson(session.user))

    private fun sessionFromJson(json: JSONObject) = Session(
        baseUrl = json.getString("base_url"),
        accessToken = json.getString("access_token"),
        refreshToken = json.optString("refresh_token"),
        accessExpiresAt = json.optLong("access_expires_at"),
        refreshExpiresAt = json.optLong("refresh_expires_at"),
        user = userFromJson(json.getJSONObject("user")),
    )

    private fun userToJson(user: User) = JSONObject()
        .put("id", user.id)
        .put("username", user.username)
        .put("nickname", user.nickname)
        .put("callsign", user.callsign)
        .put("email", user.email)
        .put("email_verified", user.emailVerified)
        .put("role", user.role)
        .put("approval_status", user.approvalStatus)
        .put("review_note", user.reviewNote)
        .put("avatar", user.avatarUrl)
        .put("address", user.address)
        .put("phone", user.phone)
        .put("introduction", user.introduction)
        .put("dmrid", user.dmrId)
        .put("mdcid", user.mdcId)
        .put("birthday", user.birthday)
        .put("sex", user.sex)
        .put("alarm_msg", user.alarmMsg)
        .put("last_group_id", user.lastGroupId)

    private fun userFromJson(json: JSONObject) = User(
        id = json.optInt("id"),
        username = json.optString("username"),
        nickname = json.optString("nickname"),
        callsign = json.optString("callsign"),
        email = json.optString("email"),
        emailVerified = json.optBoolean("email_verified"),
        role = json.optString("role", "user"),
        approvalStatus = json.optInt("approval_status"),
        reviewNote = json.optString("review_note"),
        avatarUrl = json.optString("avatar"),
        address = json.optString("address"),
        phone = json.optString("phone"),
        introduction = json.optString("introduction"),
        dmrId = json.optInt("dmrid"),
        mdcId = json.optString("mdcid"),
        birthday = json.optString("birthday"),
        sex = json.optInt("sex"),
        alarmMsg = json.optBoolean("alarm_msg"),
        lastGroupId = json.optInt("last_group_id", 999),
    )

    companion object {
        private const val PREFS_NAME = "draarl_secure_session"
        private const val KEY_SESSION = "session"
        private const val KEY_LAST_SERVER = "last_server"
        private const val KEY_ACCESS_POINT = "access_point"
        private const val KEY_GROUP_PREFIX = "android_group_"
        private const val KEY_MUTED = "muted"
        private const val KEY_PTT_OVERLAY = "ptt_overlay"
        private const val KEY_ALIAS = "draarl_session_key"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
