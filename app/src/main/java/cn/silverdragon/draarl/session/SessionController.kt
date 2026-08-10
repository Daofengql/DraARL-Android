package cn.silverdragon.draarl.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.network.ApiException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class SessionController internal constructor(
    private val remote: SessionRemoteDataSource,
    private val effects: SessionEffects,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    private val serverUrl: String,
    private val restoreTimeoutMillis: Long = SESSION_RESTORE_TIMEOUT_MILLIS
) {
    private val operations = SessionOperationRunner(scope, ioDispatcher)
    private var started = false
    private var closed = false
    private var activeSession: Session? = null

    var uiState by mutableStateOf(SessionUiState())
        private set

    fun start() {
        if (started || closed) return
        started = true
        val stored = remote.prepareStoredSession(serverUrl)
        if (stored == null) {
            uiState = uiState.copy(initializing = false)
            return
        }
        uiState = uiState.copy(user = stored.user)
        effects.onStoredSessionPrepared(stored)
        operations.launch(
            operation = remote::restoreAndValidate,
            timeoutMillis = restoreTimeoutMillis,
            onResult = { result ->
                result.onSuccess { activate(it, SessionEntryPoint.RESTORE) }
                    .onFailure { error ->
                        if (error is SessionOperationTimeoutException) {
                            remote.detachSessionForLogout(stored)
                        }
                        val message = if (error is ApiException && error.code == 403) {
                            effects.friendlyError(error)
                        } else if (error is SessionOperationTimeoutException) {
                            "恢复通信会话超时，请重新登录"
                        } else {
                            "登录状态已过期，请重新登录"
                        }
                        publishCleared(message)
                    }
            }
        )
    }

    fun login(username: String, password: String, captchaId: String, captchaCode: String) {
        if (closed || uiState.loginBusy) return
        if (captchaId.isBlank() || captchaCode.isBlank()) {
            uiState = uiState.copy(loginError = "请输入图片验证码")
            if (captchaId.isBlank()) effects.requestLoginCaptcha()
            return
        }
        uiState = uiState.copy(loginBusy = true, loginError = "")
        operations.launch(
            operation = { remote.login(serverUrl, username, password, captchaId, captchaCode) },
            onResult = { result ->
                result.onSuccess { activate(it, SessionEntryPoint.LOGIN) }
                    .onFailure { error ->
                        uiState = uiState.copy(loginBusy = false, loginError = effects.friendlyError(error))
                        effects.requestLoginCaptcha()
                    }
            }
        )
    }

    fun logout() {
        if (closed) return
        operations.invalidate()
        val detached = remote.detachSessionForLogout()
        publishCleared()
        if (detached != null) revokeInBackground(detached)
    }

    internal fun onRemoteSessionChanged(session: Session?) {
        if (closed) return
        val current = activeSession
        when {
            session == null -> {
                val hasActiveState = current != null || uiState.initializing || uiState.authenticated
                if (hasActiveState || uiState.user != null) {
                    operations.invalidate()
                    publishCleared()
                }
            }

            current == null || current.accountKey() != session.accountKey() -> {
                if (!uiState.initializing && !uiState.loginBusy) discardUnexpectedSession(session)
            }

            current != session -> {
                activeSession = session
                uiState = uiState.copy(user = session.user)
                effects.onSessionUpdated(session)
            }
        }
    }

    internal fun acceptUser(user: User) {
        val current = activeSession ?: return
        if (current.user.id != user.id || current.user == user) return
        val updated = current.copy(user = user)
        activeSession = updated
        uiState = uiState.copy(user = user)
        effects.onSessionUpdated(updated)
    }

    internal fun reportLoginError(message: String) {
        if (!closed && !uiState.authenticated) uiState = uiState.copy(loginError = message)
    }

    fun close() {
        if (closed) return
        closed = true
        operations.close()
        activeSession = null
    }

    private fun activate(session: Session, entryPoint: SessionEntryPoint) {
        activeSession = session
        uiState = SessionUiState(authenticated = true, user = session.user)
        effects.onSessionActivated(session, entryPoint)
    }

    private fun discardUnexpectedSession(session: Session) {
        val detached = remote.detachSessionForLogout(session) ?: return
        operations.invalidate()
        publishCleared()
        revokeInBackground(detached)
    }

    private fun revokeInBackground(session: Session) {
        operations.runInBackground { remote.revokeSession(session) }
    }

    private fun publishCleared(error: String = "") {
        val hadSessionContext = uiState.initializing || uiState.authenticated || uiState.user != null
        activeSession = null
        uiState = SessionUiState(initializing = false, loginError = error)
        if (hadSessionContext) effects.onSessionCleared()
    }
}

private fun Session.accountKey(): String = "${baseUrl.trimEnd('/')}#${user.id}"

private const val SESSION_RESTORE_TIMEOUT_MILLIS = 20_000L
