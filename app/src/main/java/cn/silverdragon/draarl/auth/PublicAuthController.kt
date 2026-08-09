package cn.silverdragon.draarl.auth

import android.os.Handler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.AppConfig
import cn.silverdragon.draarl.data.EmailCodeSession
import cn.silverdragon.draarl.data.RegistrationResult
import cn.silverdragon.draarl.network.AuthApi
import cn.silverdragon.draarl.network.RegistrationRequest
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PublicAuthController(
    private val api: AuthApi,
    private val executor: Executor,
    private val mainHandler: Handler,
    private val showLoginError: (String) -> Unit,
    private val friendlyError: (Throwable) -> String
) {
    private val closed = AtomicBoolean(false)
    private val captchaGeneration = AtomicInteger(0)
    private val operationGeneration = AtomicInteger(0)

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

    fun loadCaptcha() {
        if (closed.get()) return
        val generation = captchaGeneration.incrementAndGet()
        captchaId = ""
        captchaImageBase64 = ""
        captchaLoading = true
        executor.execute {
            runCatching { api.getCaptcha(AppConfig.BASE_URL) }
                .onSuccess { challenge ->
                    mainHandler.post {
                        if (closed.get() || generation != captchaGeneration.get()) return@post
                        captchaId = challenge.id
                        captchaImageBase64 = challenge.imageBase64
                        captchaLoading = false
                    }
                }
                .onFailure { failure ->
                    mainHandler.post {
                        if (closed.get() || generation != captchaGeneration.get()) return@post
                        captchaId = ""
                        captchaImageBase64 = ""
                        captchaLoading = false
                        showLoginError(friendlyError(failure))
                    }
                }
        }
    }

    fun loadRegistrationConfig() {
        if (closed.get()) return
        val generation = operationGeneration.get()
        registrationConfigLoading = true
        executor.execute {
            runCatching { api.getRegistrationRequiresEmailVerification(AppConfig.BASE_URL) }
                .onSuccess { required ->
                    mainHandler.post {
                        if (closed.get() || generation != operationGeneration.get()) return@post
                        registrationConfigLoading = false
                        registrationRequiresEmailVerification = required
                    }
                }
                .onFailure {
                    mainHandler.post {
                        if (closed.get() || generation != operationGeneration.get()) return@post
                        // The server remains authoritative during registration. A
                        // failed public-config request should not trap users in an
                        // email-verification flow that the server may have disabled.
                        registrationConfigLoading = false
                        registrationRequiresEmailVerification = false
                    }
                }
        }
    }

    fun clearFlowState() {
        operationGeneration.incrementAndGet()
        busy = false
        error = ""
        registrationConfigLoading = false
        passwordResetComplete = false
    }

    fun sendEmailCode(email: String, purpose: String, captchaCode: String, onSuccess: (EmailCodeSession) -> Unit) {
        if (busy || closed.get()) return
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
        val generation = operationGeneration.incrementAndGet()
        busy = true
        error = ""
        executor.execute {
            runCatching {
                api.sendEmailCode(
                    AppConfig.BASE_URL,
                    trimmedEmail,
                    normalizedPurpose,
                    submittedCaptchaId,
                    captchaCode
                )
            }.onSuccess { session ->
                mainHandler.post {
                    if (closed.get() || generation != operationGeneration.get()) return@post
                    busy = false
                    error = ""
                    onSuccess(session)
                    loadCaptcha()
                }
            }.onFailure { failure ->
                mainHandler.post {
                    if (closed.get() || generation != operationGeneration.get()) return@post
                    busy = false
                    error = friendlyError(failure)
                    loadCaptcha()
                }
            }
        }
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
        if (busy || closed.get()) return
        val trimmedUsername = username.trim()
        val normalizedCallsign = callsign.trim().uppercase()
        val trimmedEmail = email.trim()
        val trimmedPhone = phone.trim()
        val validationError = when {
            !trimmedUsername.matches(USERNAME_PATTERN) -> "用户名必须是 3-20 位字母、数字或下划线"

            !normalizedCallsign.matches(CALLSIGN_PATTERN) -> "呼号格式不正确，应以字母开头，3-10 个字符"

            !trimmedEmail.matches(EMAIL_PATTERN) -> "请输入正确的邮箱地址"

            trimmedPhone.isNotBlank() && !trimmedPhone.matches(PHONE_PATTERN) -> "手机号格式不正确"

            password.length < 6 -> "密码长度至少 6 位"

            password != confirmPassword -> "两次输入的密码不一致"

            registrationRequiresEmailVerification && (sessionId.isBlank() || emailCode.isBlank()) ->
                "请先获取并填写邮箱验证码"

            else -> null
        }
        if (validationError != null) {
            error = validationError
            return
        }
        val generation = operationGeneration.incrementAndGet()
        busy = true
        error = ""
        executor.execute {
            runCatching {
                api.register(
                    RegistrationRequest(
                        baseUrl = AppConfig.BASE_URL,
                        username = trimmedUsername,
                        password = password,
                        callsign = normalizedCallsign,
                        phone = trimmedPhone,
                        nickname = nickname.trim().ifBlank { trimmedUsername },
                        email = trimmedEmail,
                        sessionId = if (registrationRequiresEmailVerification) sessionId else "",
                        emailCode = if (registrationRequiresEmailVerification) emailCode else ""
                    )
                )
            }.onSuccess { result ->
                mainHandler.post {
                    if (closed.get() || generation != operationGeneration.get()) return@post
                    busy = false
                    error = ""
                    onSuccess(result)
                }
            }.onFailure { failure -> postOperationFailure(generation, failure) }
        }
    }

    fun resetPassword(
        sessionId: String,
        emailCode: String,
        newPassword: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        if (busy || closed.get()) return
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
        val generation = operationGeneration.incrementAndGet()
        busy = true
        error = ""
        passwordResetComplete = false
        executor.execute {
            runCatching { api.resetPassword(AppConfig.BASE_URL, sessionId, emailCode, newPassword) }
                .onSuccess {
                    mainHandler.post {
                        if (closed.get() || generation != operationGeneration.get()) return@post
                        busy = false
                        error = ""
                        passwordResetComplete = true
                        onSuccess()
                    }
                }
                .onFailure { failure -> postOperationFailure(generation, failure) }
        }
    }

    fun reset() {
        clearFlowState()
        captchaGeneration.incrementAndGet()
        captchaLoading = false
        captchaId = ""
        captchaImageBase64 = ""
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        reset()
    }

    private fun postOperationFailure(generation: Int, failure: Throwable) {
        mainHandler.post {
            if (closed.get() || generation != operationGeneration.get()) return@post
            busy = false
            error = friendlyError(failure)
        }
    }

    private companion object {
        val PURPOSES = setOf("register", "reset_password", "change_email")
        val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,20}$")
        val CALLSIGN_PATTERN = Regex("^[A-Za-z][A-Za-z0-9]{2,9}$")
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        val PHONE_PATTERN = Regex("^1[3-9]\\d{9}$")
    }
}
