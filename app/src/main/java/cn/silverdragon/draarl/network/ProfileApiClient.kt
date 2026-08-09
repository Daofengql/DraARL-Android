package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.auth.accountLoginRejection
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.profile.emailChangeRequestJson
import org.json.JSONObject

internal class ProfileApiClient(
    private val requester: ApiJsonRequester,
    private val requests: ApiRequestExecutor,
    private val sessions: ApiSessionManager,
    private val users: UserJsonMapper
) : ProfileApi {
    override fun getMe(updateSession: Boolean): User {
        val requestSession = sessions.sessionExpectation()
        val user = users.fromJson(requester.execute("GET", "/api/me").requireObject("data"))
        accountLoginRejection(user)?.let { message ->
            if (requestSession != null) sessions.clearSession(requestSession)
            throw ApiException(HTTP_FORBIDDEN, message)
        }
        if (updateSession) sessions.acceptUser(user, requestSession)
        return user
    }

    override fun acceptCurrentUser(user: User) {
        val expectation = sessions.sessionExpectation()
        when {
            expectation == null || expectation.userId != user.id -> Unit
            accountLoginRejection(user) != null -> sessions.clearSession(expectation)
            else -> sessions.acceptUser(user, expectation)
        }
    }

    override fun updateProfile(request: ProfileUpdateRequest): User {
        val body = JSONObject()
            .put("nickname", request.nickname)
            .put("phone", request.phone)
            .put("address", request.address)
            .put("introduction", request.introduction)
            .put("birthday", request.birthday)
            .put("sex", request.sex)
            .put("dmrid", request.dmrid)
            .put("mdcid", request.mdcid)
            .put("alarm_msg", request.alarmMsg)
        requester.execute("PUT", "/api/me", body)
        return getMe(updateSession = true)
    }

    override fun uploadFile(fileBytes: ByteArray, fileName: String, fileType: String): String {
        val session = sessions.currentSession() ?: throw ApiException(HTTP_UNAUTHORIZED, "请先登录")
        val baseUrl = ApiClient.normalizeBaseUrl(session.baseUrl)
        val response = requests.execute(
            HttpRequest(
                url = "$baseUrl/api/upload/file",
                method = "POST",
                headers = mapOf("Authorization" to "Bearer ${session.accessToken}"),
                body = HttpRequestBody.Multipart(
                    listOf(
                        HttpPart(
                            name = "file",
                            content = fileBytes,
                            fileName = fileName,
                            mediaType = "application/octet-stream"
                        ),
                        HttpPart(name = "file_type", content = fileType.toByteArray())
                    )
                ),
                connectTimeoutMillis = UPLOAD_TIMEOUT_MILLIS,
                readTimeoutMillis = UPLOAD_TIMEOUT_MILLIS,
                writeTimeoutMillis = UPLOAD_TIMEOUT_MILLIS
            )
        )
        if (response.status !in HTTP_SUCCESS_RANGE) {
            throw ApiException(response.status, "上传失败: ${response.bodyText()}")
        }
        val responseJson = response.toApiJson()
        val data = responseJson.optJSONObject("data") ?: responseJson
        return ApiClient.resolveHttpsUrl(
            baseUrl,
            data.optStringClean("url").ifBlank { data.optStringClean("file_url") }
        )
    }

    override fun changePassword(oldPassword: String, newPassword: String) {
        requester.execute(
            "PUT",
            "/api/me/password",
            JSONObject().put("old_password", oldPassword).put("new_password", newPassword)
        )
    }

    override fun changeEmail(oldSessionId: String, oldCode: String, newSessionId: String, newCode: String): User {
        requester.execute(
            "PUT",
            "/api/me/email",
            emailChangeRequestJson(oldSessionId, oldCode, newSessionId, newCode)
        )
        return getMe(updateSession = true)
    }
}

private const val HTTP_FORBIDDEN = 403
private const val HTTP_SUCCESS_MAX = 299
private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_UNAUTHORIZED = 401
private const val UPLOAD_TIMEOUT_MILLIS = 30_000L
private val HTTP_SUCCESS_RANGE = HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
