package cn.silverdragon.draarl.profile

import android.os.Handler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.network.ApiClient
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ProfileController(
    private val api: ApiClient,
    private val executor: Executor,
    private val mainHandler: Handler,
    private val currentUser: () -> User?,
    private val updateUser: (User) -> Unit,
    private val showNotice: (String) -> Unit,
    private val friendlyError: (Throwable) -> String,
) {
    private val closed = AtomicBoolean(false)
    private val generation = AtomicInteger(0)

    var busy by mutableStateOf(false)
        private set

    fun updateProfile(
        nickname: String,
        phone: String,
        address: String,
        introduction: String,
        birthday: String = "",
        sex: Int = 0,
        dmrid: Int = 0,
        mdcid: String = "",
        alarmMsg: Boolean = false,
        onSuccess: () -> Unit = {},
    ) = launch(
        operation = {
            api.updateProfile(nickname, phone, address, introduction, birthday, sex, dmrid, mdcid, alarmMsg)
        },
        onSuccess = {
            updateUser(it)
            showNotice("个人资料已保存")
            onSuccess()
        },
    )

    fun uploadAvatar(fileBytes: ByteArray, fileName: String, onSuccess: () -> Unit = {}) = launch(
        operation = {
            api.uploadFile(fileBytes, fileName, "avatar")
            api.getMe()
        },
        onSuccess = {
            updateUser(it)
            showNotice("头像已更新")
            onSuccess()
        },
    )

    fun changePassword(oldPassword: String, newPassword: String) = launch(
        operation = { api.changePassword(oldPassword, newPassword) },
        onSuccess = { showNotice("密码已修改") },
    )

    fun changeEmail(
        oldSessionId: String,
        oldCode: String,
        newSessionId: String,
        newCode: String,
        onSuccess: () -> Unit = {},
    ) {
        val user = currentUser() ?: return
        val validationError = EmailChangeValidation.validate(
            hasVerifiedEmail = user.email.isNotBlank() && user.emailVerified,
            oldSessionId = oldSessionId,
            oldCode = oldCode,
            newSessionId = newSessionId,
            newCode = newCode,
        )
        if (validationError != null) {
            showNotice(validationError)
            return
        }
        launch(
            operation = { api.changeEmail(oldSessionId, oldCode, newSessionId, newCode) },
            onSuccess = {
                updateUser(it)
                showNotice("邮箱已更新")
                onSuccess()
            },
        )
    }

    fun reset() {
        generation.incrementAndGet()
        busy = false
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        reset()
    }

    private fun <T> launch(operation: () -> T, onSuccess: (T) -> Unit) {
        if (busy || closed.get()) return
        val requestGeneration = generation.incrementAndGet()
        busy = true
        executor.execute {
            runCatching(operation)
                .onSuccess { result ->
                    mainHandler.post {
                        if (closed.get() || requestGeneration != generation.get()) return@post
                        busy = false
                        onSuccess(result)
                    }
                }
                .onFailure { error ->
                    mainHandler.post {
                        if (closed.get() || requestGeneration != generation.get()) return@post
                        busy = false
                        showNotice(friendlyError(error))
                    }
                }
        }
    }
}

internal object EmailChangeValidation {
    fun validate(
        hasVerifiedEmail: Boolean,
        oldSessionId: String,
        oldCode: String,
        newSessionId: String,
        newCode: String,
    ): String? = when {
        hasVerifiedEmail && (oldSessionId.isBlank() || oldCode.isBlank()) -> "请先完成当前邮箱验证"
        newSessionId.isBlank() -> "请先向新邮箱发送验证码"
        newCode.isBlank() -> "请输入新邮箱验证码"
        else -> null
    }
}
