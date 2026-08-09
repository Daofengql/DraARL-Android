package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.CaptchaChallenge
import cn.silverdragon.draarl.data.EmailCodeSession
import cn.silverdragon.draarl.data.RegistrationResult
import org.json.JSONObject

internal data class CaptchaDto(val id: String, val imageBase64: String, val expiresInSeconds: Int) {
    fun toDomain() = CaptchaChallenge(id, imageBase64, expiresInSeconds)
}

internal data class LoginDto(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresInSeconds: Long,
    val refreshExpiresInSeconds: Long,
    val user: UserDto
)

internal data class RegistrationConfigDto(val requiresEmailVerification: Boolean)

internal data class EmailCodeDto(val sessionId: String, val expiresInSeconds: Int) {
    fun toDomain() = EmailCodeSession(sessionId, expiresInSeconds)
}

internal data class RegistrationResultDto(
    val id: Int,
    val username: String,
    val nickname: String,
    val approvalStatus: Int,
    val devicePassword: String
) {
    fun toDomain() = RegistrationResult(id, username, nickname, approvalStatus, devicePassword)
}

internal object AuthApiResponseMapper {
    fun captcha(response: JSONObject): CaptchaDto {
        val data = response.requireObject("data")
        return CaptchaDto(
            id = data.requireString("captcha_id"),
            imageBase64 = data.optStringClean("captcha_image")
                .ifBlank { data.optStringClean("image_base64") }
                .ifBlank { throw ApiException(HTTP_RESPONSE_MAPPING_ERROR, "服务器响应缺少验证码图片") },
            expiresInSeconds = data.optInt("expire", DEFAULT_CAPTCHA_EXPIRES_SECONDS)
        )
    }

    fun login(response: JSONObject): LoginDto {
        val data = response.requireObject("data")
        return LoginDto(
            accessToken = data.requireString("token"),
            refreshToken = data.optStringClean("refresh_token"),
            accessExpiresInSeconds = data.optLong("expires_in", DEFAULT_ACCESS_EXPIRES_SECONDS),
            refreshExpiresInSeconds = data.optLong("refresh_expires_in", DEFAULT_REFRESH_EXPIRES_SECONDS),
            user = UserDto.fromJson(data.requireObject("user"))
        )
    }

    fun registrationConfig(response: JSONObject): RegistrationConfigDto {
        val registration = response.requireObject("data").optJSONObject("registration")
        return RegistrationConfigDto(registration?.optBoolean("require_email_verification", true) ?: true)
    }

    fun emailCode(response: JSONObject): EmailCodeDto {
        val data = response.requireObject("data")
        return EmailCodeDto(
            sessionId = data.requireString("session_id"),
            expiresInSeconds = data.optInt("expires_in", DEFAULT_EMAIL_CODE_EXPIRES_SECONDS)
        )
    }

    fun registration(response: JSONObject): RegistrationResultDto {
        val data = response.requireObject("data")
        return RegistrationResultDto(
            id = data.requireInt("id"),
            username = data.requireString("username"),
            nickname = data.optStringClean("nickname"),
            approvalStatus = data.optInt("approval_status"),
            devicePassword = data.optStringClean("device_password")
        )
    }
}

private const val DEFAULT_ACCESS_EXPIRES_SECONDS = 10_800L
private const val DEFAULT_CAPTCHA_EXPIRES_SECONDS = 300
private const val DEFAULT_EMAIL_CODE_EXPIRES_SECONDS = 600
private const val DEFAULT_REFRESH_EXPIRES_SECONDS = 1_209_600L
private const val HTTP_RESPONSE_MAPPING_ERROR = 500
