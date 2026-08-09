package cn.silverdragon.draarl.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.AppConfig
import cn.silverdragon.draarl.concurrency.ControllerTaskRunner
import cn.silverdragon.draarl.data.EmailCodeSession
import cn.silverdragon.draarl.data.RegistrationResult
import cn.silverdragon.draarl.network.AuthApi
import cn.silverdragon.draarl.network.RegistrationRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class PublicAuthController(
    private val api: AuthApi,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    private val showLoginError: (String) -> Unit,
    private val friendlyError: (Throwable) -> String
) {
    private var closed = false

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var registrationRequiresEmailVerification by mutableStateOf(true)
        private set
    var registrationConfigLoading by mutableStateOf(false)
        private set
    var passwordResetComplete by mutableStateOf(false)
        private set
    var captchaId by mutableStateOf("")
        private set
    var captchaImageBase64 by mutableStateOf("")
        private set
    var captchaLoading by mutableStateOf(false)
        private set
    private val operationTasks = ControllerTaskRunner(scope, ioDispatcher) { busy = it }
    private val captchaTasks = ControllerTaskRunner(scope, ioDispatcher) { captchaLoading = it }
    private val registrationConfigTasks = ControllerTaskRunner(scope, ioDispatcher) { registrationConfigLoading = it }

    fun loadCaptcha() {
        if (closed) return
        captchaId = ""
        captchaImageBase64 = ""
        captchaTasks.replace(
            operation = { api.getCaptcha(AppConfig.BASE_URL) },
            onSuccess = { challenge ->
                captchaId = challenge.id
                captchaImageBase64 = challenge.imageBase64
            },
            onFailure = { failure ->
                captchaId = ""
                captchaImageBase64 = ""
                showLoginError(friendlyError(failure))
            }
        )
    }

    fun loadRegistrationConfig() {
        if (closed) return
        registrationConfigTasks.replace(
            operation = { api.getRegistrationRequiresEmailVerification(AppConfig.BASE_URL) },
            onSuccess = { required -> registrationRequiresEmailVerification = required },
            onFailure = {
                // A failed public-config request must not force a flow the server may have disabled.
                registrationRequiresEmailVerification = false
            }
        )
    }

    fun clearFlowState() {
        operationTasks.cancel()
        registrationConfigTasks.cancel()
        error = ""
        passwordResetComplete = false
    }

    fun sendEmailCode(email: String, purpose: String, captchaCode: String, onSuccess: (EmailCodeSession) -> Unit) {
        if (busy || closed) return
        val trimmedEmail = email.trim()
        val submittedCaptchaId = captchaId
        val normalizedPurpose = purpose.trim()
        val validationError = when {
            !trimmedEmail.matches(EMAIL_PATTERN) -> "请输入正确的邮箱地址"
            normalizedPurpose !in PURPOSES -> "邮箱验证码用途不正确"
            submittedCaptchaId.isBlank() || captchaCode.isBlank() -> "请输入图片验证码"
            else -> null
        }
        if (validationError != null) {
            error = validationError
            if (submittedCaptchaId.isBlank()) loadCaptcha()
            return
        }
        error = ""
        operationTasks.launch(
            operation = {
                api.sendEmailCode(
                    AppConfig.BASE_URL,
                    trimmedEmail,
                    normalizedPurpose,
                    submittedCaptchaId,
                    captchaCode
                )
            },
            onSuccess = { session ->
                error = ""
                onSuccess(session)
                loadCaptcha()
            },
            onFailure = { failure ->
                error = friendlyError(failure)
                loadCaptcha()
            }
        )
    }

    fun register(
        username: String,
        callsign: String,
        nickname: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
        sessionId: String,
        emailCode: String,
        onSuccess: (RegistrationResult) -> Unit
    ) {
        if (busy || closed) return
        val trimmedUsername = username.trim()
        val normalizedCallsign = callsign.trim().uppercase()
        val trimmedEmail = email.trim()
        val trimmedPhone = phone.trim()
        val requiresEmailVerification = registrationRequiresEmailVerification
        val validationError = when {
            !trimmedUsername.matches(USERNAME_PATTERN) -> "用户名必须是 3-20 位字母、数字或下划线"

            !normalizedCallsign.matches(CALLSIGN_PATTERN) -> "呼号格式不正确，应以字母开头，3-10 个字符"

            !trimmedEmail.matches(EMAIL_PATTERN) -> "请输入正确的邮箱地址"

            trimmedPhone.isNotBlank() && !trimmedPhone.matches(PHONE_PATTERN) -> "手机号格式不正确"

            password.length < 6 -> "密码长度至少 6 位"

            password != confirmPassword -> "两次输入的密码不一致"

            requiresEmailVerification && (sessionId.isBlank() || emailCode.isBlank()) ->
                "请先获取并填写邮箱验证码"

            else -> null
        }
        if (validationError != null) {
            error = validationError
            return
        }
        error = ""
        operationTasks.launch(
            operation = {
                api.register(
                    RegistrationRequest(
                        baseUrl = AppConfig.BASE_URL,
                        username = trimmedUsername,
                        password = password,
                        callsign = normalizedCallsign,
                        phone = trimmedPhone,
                        nickname = nickname.trim().ifBlank { trimmedUsername },
                        email = trimmedEmail,
                        sessionId = if (requiresEmailVerification) sessionId else "",
                        emailCode = if (requiresEmailVerification) emailCode else ""
                    )
                )
            },
            onSuccess = { result ->
                error = ""
                onSuccess(result)
            },
            onFailure = { failure -> error = friendlyError(failure) }
        )
    }

    fun resetPassword(
        sessionId: String,
        emailCode: String,
        newPassword: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        if (busy || closed) return
        val validationError = when {
            sessionId.isBlank() -> "请先获取邮箱验证码"
            emailCode.isBlank() -> "请输入邮箱验证码"
            newPassword.length < 6 -> "新密码长度至少 6 位"
            newPassword != confirmPassword -> "两次输入的密码不一致"
            else -> null
        }
        if (validationError != null) {
            error = validationError
            return
        }
        error = ""
        passwordResetComplete = false
        operationTasks.launch(
            operation = { api.resetPassword(AppConfig.BASE_URL, sessionId, emailCode, newPassword) },
            onSuccess = {
                error = ""
                passwordResetComplete = true
                onSuccess()
            },
            onFailure = { failure -> error = friendlyError(failure) }
        )
    }

    fun reset() {
        clearFlowState()
        captchaTasks.cancel()
        captchaId = ""
        captchaImageBase64 = ""
    }

    fun close() {
        if (closed) return
        closed = true
        operationTasks.close()
        captchaTasks.close()
        registrationConfigTasks.close()
        error = ""
        passwordResetComplete = false
        captchaId = ""
        captchaImageBase64 = ""
    }

    private companion object {
        val PURPOSES = setOf("register", "reset_password", "change_email")
        val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,20}$")
        val CALLSIGN_PATTERN = Regex("^[A-Za-z][A-Za-z0-9]{2,9}$")
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        val PHONE_PATTERN = Regex("^1[3-9]\\d{9}$")
    }
}
