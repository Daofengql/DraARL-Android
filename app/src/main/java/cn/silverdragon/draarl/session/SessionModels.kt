package cn.silverdragon.draarl.session

import androidx.compose.runtime.Immutable
import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.data.User

@Immutable
data class SessionUiState(
    val initializing: Boolean = true,
    val authenticated: Boolean = false,
    val loginBusy: Boolean = false,
    val loginError: String = "",
    val user: User? = null
)

internal enum class SessionEntryPoint {
    LOGIN,
    RESTORE
}

internal interface SessionEffects {
    fun onStoredSessionPrepared(session: Session)

    fun onSessionActivated(session: Session, entryPoint: SessionEntryPoint)

    fun onSessionUpdated(session: Session)

    fun onSessionCleared()

    fun requestLoginCaptcha()

    fun friendlyError(error: Throwable): String
}
