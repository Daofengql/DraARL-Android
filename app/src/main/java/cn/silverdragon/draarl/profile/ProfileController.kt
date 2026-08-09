package cn.silverdragon.draarl.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.silverdragon.draarl.concurrency.ControllerTaskRunner
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.network.ProfileApi
import cn.silverdragon.draarl.network.ProfileUpdateRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

class ProfileController(
    private val api: ProfileApi,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    private val currentUser: () -> User?,
    private val updateUser: (User) -> Unit,
    private val showNotice: (String) -> Unit,
    private val friendlyError: (Throwable) -> String
) {
    private var closed = false

    var busy by mutableStateOf(false)
        private set
    private val tasks = ControllerTaskRunner(scope, ioDispatcher) { busy = it }

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
        onSuccess: () -> Unit = {}
    ) = launch(
        operation = {
            api.updateProfile(
                ProfileUpdateRequest(nickname, phone, address, introduction, birthday, sex, dmrid, mdcid, alarmMsg)
            )
        },
        onSuccess = {
            updateUser(it)
            showNotice("个人资料已保存")
            onSuccess()
        }
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
        }
    )

    fun changePassword(oldPassword: String, newPassword: String) = launch(
        operation = { api.changePassword(oldPassword, newPassword) },
        onSuccess = { showNotice("密码已修改") }
    )

    fun changeEmail(
        oldSessionId: String,
        oldCode: String,
        newSessionId: String,
        newCode: String,
        onSuccess: () -> Unit = {}
    ) {
        val user = currentUser() ?: return
        val validationError = EmailChangeValidation.validate(
            hasVerifiedEmail = user.email.isNotBlank() && user.emailVerified,
            oldSessionId = oldSessionId,
            oldCode = oldCode,
            newSessionId = newSessionId,
            newCode = newCode
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
            }
        )
    }

    fun reset() {
        tasks.cancel()
    }

    fun close() {
        if (closed) return
        closed = true
        tasks.close()
    }

    private fun <T> launch(operation: () -> T, onSuccess: (T) -> Unit) {
        if (closed) return
        tasks.launch(operation, onSuccess) { error -> showNotice(friendlyError(error)) }
    }
}

internal object EmailChangeValidation {
    fun validate(
        hasVerifiedEmail: Boolean,
        oldSessionId: String,
        oldCode: String,
        newSessionId: String,
        newCode: String
    ): String? = when {
        hasVerifiedEmail && (oldSessionId.isBlank() || oldCode.isBlank()) -> "请先完成当前邮箱验证"
        newSessionId.isBlank() -> "请先向新邮箱发送验证码"
        newCode.isBlank() -> "请输入新邮箱验证码"
        else -> null
    }
}
