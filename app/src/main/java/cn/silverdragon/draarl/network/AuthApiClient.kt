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
    override fun getCaptcha(baseUrl: String): CaptchaChallenge = publicRequestMapped(
        baseUrl,
        "GET",
        "/api/captcha",
        mapper = AuthApiResponseMapper::captcha
    ).toDomain()

    override fun login(
        baseUrl: String,
        username: String,
        password: String,
        captchaId: String,
        captchaCode: String
    ): Session {
        val operation = sessions.beginAuthOperation()
        val normalizedUrl = ApiClient.normalizeBaseUrl(baseUrl)
        val dto = publicRequestMapped(
            normalizedUrl,
            "POST",
            "/api/auth/login",
            JSONObject()
                .put("username", username.trim())
                .put("password", password)
                .put("captcha_id", captchaId)
                .put("captcha_code", captchaCode.trim()),
            mapper = AuthApiResponseMapper::login
        )
        val now = clockMillis()
        val session = Session(
            baseUrl = normalizedUrl,
            accessToken = dto.accessToken,
            refreshToken = dto.refreshToken,
            accessExpiresAt = now + dto.accessExpiresInSeconds * MILLIS_PER_SECOND,
            refreshExpiresAt = now +
                dto.refreshExpiresInSeconds * MILLIS_PER_SECOND,
            user = users.fromDto(dto.user, normalizedUrl)
        )
        accountLoginRejection(session.user)?.let { message ->
            throw ApiException(HTTP_FORBIDDEN, message)
        }
        return sessions.completeAuthOperation(operation, session, "登录请求已取消")
    }

    override fun getRegistrationRequiresEmailVerification(baseUrl: String): Boolean = publicRequestMapped(
        baseUrl,
        "GET",
        "/api/config/public",
        mapper = AuthApiResponseMapper::registrationConfig
    ).requiresEmailVerification

    override fun sendEmailCode(
        baseUrl: String,
        email: String,
        purpose: String,
        captchaId: String,
        captchaCode: String
    ): EmailCodeSession = publicRequestMapped(
        baseUrl,
        "POST",
        "/api/auth/send-code",
        JSONObject()
            .put("email", email.trim())
            .put("purpose", purpose)
            .put("captcha_id", captchaId)
            .put("captcha_code", captchaCode.trim()),
        mapper = AuthApiResponseMapper::emailCode
    ).toDomain()

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
        return publicRequestMapped(
            request.baseUrl,
            "POST",
            "/api/auth/register",
            body,
            AuthApiResponseMapper::registration
        ).toDomain()
    }

    override fun resetPassword(baseUrl: String, sessionId: String, code: String, newPassword: String) {
        publicRequestMapped(
            baseUrl,
            "POST",
            "/api/auth/reset-password",
            JSONObject()
                .put("session_id", sessionId)
                .put("code", code.trim())
                .put("new_password", newPassword)
        ) { Unit }
    }

    private fun <T> publicRequestMapped(
        baseUrl: String,
        method: String,
        path: String,
        body: JSONObject? = null,
        mapper: (JSONObject) -> T
    ): T = decodeApiResponse(
        method,
        path,
        { requests.executeJson(ApiClient.normalizeBaseUrl(baseUrl), method, path, body, accessToken = null) },
        mapper
    )
}

private const val HTTP_FORBIDDEN = 403
private const val MILLIS_PER_SECOND = 1_000L
