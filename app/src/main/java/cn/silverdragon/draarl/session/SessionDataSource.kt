package cn.silverdragon.draarl.session

import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.network.ApiClient

internal interface SessionRemoteDataSource {
    fun prepareStoredSession(baseUrl: String): Session?

    fun login(baseUrl: String, username: String, password: String, captchaId: String, captchaCode: String): Session

    fun restoreAndValidate(): Session

    fun detachSessionForLogout(expected: Session? = null): Session?

    fun revokeSession(session: Session): Result<Unit>
}

internal class ApiSessionRemoteDataSource(private val api: ApiClient) : SessionRemoteDataSource {
    override fun prepareStoredSession(baseUrl: String): Session? = api.prepareCurrentSession(baseUrl)

    override fun login(
        baseUrl: String,
        username: String,
        password: String,
        captchaId: String,
        captchaCode: String
    ): Session = api.login(baseUrl, username, password, captchaId, captchaCode)

    override fun restoreAndValidate(): Session = api.restoreAndValidate()

    override fun detachSessionForLogout(expected: Session?): Session? = api.detachSessionForLogout(expected)

    override fun revokeSession(session: Session): Result<Unit> = api.revokeSession(session)
}
