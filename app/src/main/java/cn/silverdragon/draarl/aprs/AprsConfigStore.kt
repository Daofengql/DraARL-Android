package cn.silverdragon.draarl.aprs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal class AprsConfigStore(context: Context) : AprsConfigStorage {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    override fun load(userId: Int): AprsConfig = if (userId <= 0) {
        AprsConfig()
    } else {
        preferences.getString(key(userId), null)?.let(::decrypt) ?: AprsConfig()
    }

    private fun decrypt(encrypted: String): AprsConfig = runCatching {
        val wrapper = JSONObject(encrypted)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(wrapper.getString("iv"), Base64.NO_WRAP))
        )
        fromJson(
            JSONObject(
                cipher.doFinal(Base64.decode(wrapper.getString("data"), Base64.NO_WRAP)).toString(Charsets.UTF_8)
            )
        )
    }.getOrDefault(AprsConfig())

    @Synchronized
    override fun save(userId: Int, config: AprsConfig) {
        if (userId <= 0) return
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put(
                "data",
                Base64.encodeToString(
                    cipher.doFinal(toJson(config).toString().toByteArray(Charsets.UTF_8)),
                    Base64.NO_WRAP
                )
            )
        preferences.edit { putString(key(userId), encrypted.toString()) }
    }

    private fun key(userId: Int) = "config_$userId"

    private fun toJson(config: AprsConfig) = JSONObject()
        .put("enabled", config.enabled)
        .put("server", config.server)
        .put("port", config.port)
        .put("callsign", config.callsign)
        .put("passcode", config.passcode)
        .put("comment", config.comment)
        .put("symbol_table", config.symbolTable.toString())
        .put("symbol_code", config.symbolCode.toString())
        .put("auto_report", config.autoReport)
        .put("moving_interval", config.movingIntervalSeconds)
        .put("stationary_interval", config.stationaryIntervalSeconds)

    private fun fromJson(json: JSONObject) = AprsConfig(
        enabled = json.optBoolean("enabled"),
        server = json.optString("server", "rotate.aprs2.net"),
        port = json.optInt("port", 14580).coerceIn(1, 65535),
        callsign = json.optString("callsign"),
        passcode = json.optString("passcode"),
        comment = json.optString("comment", "DraARL"),
        symbolTable = json.optString("symbol_table", "/").firstOrNull() ?: '/',
        symbolCode = json.optString("symbol_code", ">").firstOrNull() ?: '>',
        autoReport = json.optBoolean("auto_report"),
        movingIntervalSeconds = json.optInt("moving_interval", 120).coerceIn(60, 600),
        stationaryIntervalSeconds = json.optInt("stationary_interval", 600).coerceIn(60, 3_600)
    )

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES = "draarl_aprs"
        const val KEY_ALIAS = "draarl_aprs_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
