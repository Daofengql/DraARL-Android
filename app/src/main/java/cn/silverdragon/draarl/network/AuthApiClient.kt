package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.auth.accountLoginRejection
import cn.silverdragon.draarl.data.CaptchaChallenge
import cn.silverdragon.draarl.data.EmailCodeSession
import cn.silverdragon.draarl.data.RegistrationResult
import cn.silverdragon.draarl.data.Session
import org.json.JSONObject

internal class AuthApiClient(
    private val requests: ApiRequestExecutor,
    private val sessions: ApiSessionManager,
    private val users: UserJsonMapper,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : AuthApi {
    override fun getCaptcha(baseUrl: String): CaptchaChallenge {
        val data = publicRequest(baseUrl, "GET", "/api/captcha").requireSuccess().requireObject("data")
        return CaptchaChallenge(
            id = data.requireString("captcha_id"),
            imageBase64 = data.optStringClean("captcha_image")
                .ifBlank { data.optStringClean("image_base64") }
                .ifBlank { throw ApiException(HTTP_SERVER_ERROR, "服务器响应缺少验证码图片") },
            expiresInSeconds = data.optInt("expire", DEFAULT_CAPTCHA_EXPIRES_SECONDS)
        )
    }

    override fun login(
        baseUrl: String,
        username: String,
        password: String,
        captchaId: String,
        captchaCode: String
    ): Session {
        val operation = sessions.beginAuthOperation()
        val normalizedUrl = ApiClient.normalizeBaseUrl(baseUrl)
        val data = publicRequest(
            normalizedUrl,
            "POST",
            "/api/auth/login",
            JSONObject()
                .put("username", username.trim())
                .put("password", password)
                .put("captcha_id", captchaId)
                .put("captcha_code", captchaCode.trim())
        ).requireSuccess().requireObject("data")
        val now = clockMillis()
        val session = Session(
            baseUrl = normalizedUrl,
            accessToken = data.requireString("token"),
            refreshToken = data.optStringClean("refresh_token"),
            accessExpiresAt = now + data.optLong("expires_in", DEFAULT_ACCESS_EXPIRES_SECONDS) * MILLIS_PER_SECOND,
            refreshExpiresAt = now +
                data.optLong("refresh_expires_in", DEFAULT_REFRESH_EXPIRES_SECONDS) * MILLIS_PER_SECOND,
            user = users.fromJson(data.requireObject("user"), normalizedUrl)
        )
        accountLoginRejection(session.user)?.let { message ->
            throw ApiException(HTTP_FORBIDDEN, message)
        }
        return sessions.completeAuthOperation(operation, session, "登录请求已取消")
    }

    override fun getRegistrationRequiresEmailVerification(baseUrl: String): Boolean {
        val data = publicRequest(baseUrl, "GET", "/api/config/public").requireSuccess().requireObject("data")
        return data.optJSONObject("registration")
            ?.optBoolean("require_email_verification", true)
            ?: true
    }

    override fun sendEmailCode(
        baseUrl: String,
        email: String,
        purpose: String,
        captchaId: String,
        captchaCode: String
    ): EmailCodeSession {
        val data = publicRequest(
            baseUrl,
            "POST",
            "/api/auth/send-code",
            JSONObject()
                .put("email", email.trim())
                .put("purpose", purpose)
                .put("captcha_id", captchaId)
                .put("captcha_code", captchaCode.trim())
        ).requireSuccess().requireObject("data")
        return EmailCodeSession(
            sessionId = data.requireString("session_id"),
            expiresInSeconds = data.optInt("expires_in", DEFAULT_EMAIL_CODE_EXPIRES_SECONDS)
        )
    }

    override fun register(request: RegistrationRequest): RegistrationResult {
        val body = JSONObject()
            .put("username", request.username.trim())
            .put("password", request.password)
            .put("callsign", request.callsign.trim().uppercase())
            .put("phone", request.phone.trim())
            .put("nickname", request.nickname.trim())
            .put("email", request.email.trim())
        if (request.sessionId.isNotBlank()) body.put("session_id", request.sessionId)
        if (request.emailCode.isNotBlank()) body.put("email_code", request.emailCode.trim())
        val data = publicRequest(request.baseUrl, "POST", "/api/auth/register", body)
            .requireSuccess()
            .requireObject("data")
        return RegistrationResult(
            id = data.optInt("id"),
            username = data.optStringClean("username"),
            nickname = data.optStringClean("nickname"),
            approvalStatus = data.optInt("approval_status"),
            devicePassword = data.optStringClean("device_password")
        )
    }

    override fun resetPassword(baseUrl: String, sessionId: String, code: String, newPassword: String) {
        publicRequest(
            baseUrl,
            "POST",
            "/api/auth/reset-password",
            JSONObject()
                .put("session_id", sessionId)
                .put("code", code.trim())
                .put("new_password", newPassword)
        ).requireSuccess()
    }

    private fun publicRequest(baseUrl: String, method: String, path: String, body: JSONObject? = null): JSONObject =
        requests.executeJson(ApiClient.normalizeBaseUrl(baseUrl), method, path, body, accessToken = null)
}

private const val DEFAULT_ACCESS_EXPIRES_SECONDS = 10_800L
private const val DEFAULT_CAPTCHA_EXPIRES_SECONDS = 300
private const val DEFAULT_EMAIL_CODE_EXPIRES_SECONDS = 600
private const val DEFAULT_REFRESH_EXPIRES_SECONDS = 1_209_600L
private const val HTTP_FORBIDDEN = 403
private const val HTTP_SERVER_ERROR = 500
private const val MILLIS_PER_SECOND = 1_000L
