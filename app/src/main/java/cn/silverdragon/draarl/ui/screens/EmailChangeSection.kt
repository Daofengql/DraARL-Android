package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.ui.components.CaptchaInput
import kotlinx.coroutines.delay

@Composable
internal fun ChangeEmailSection(controller: AppController, onDone: () -> Unit) {
    val user = controller.user ?: return
    val verifyCurrentEmail = user.email.isNotBlank() && user.emailVerified
    var newEmail by remember(user.id) { mutableStateOf("") }
    var oldSessionId by remember { mutableStateOf("") }
    var oldCode by remember { mutableStateOf("") }
    var newSessionId by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var captchaCode by remember { mutableStateOf("") }
    var oldCooldown by remember { mutableIntStateOf(0) }
    var newCooldown by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { controller.publicAuth.loadCaptcha() }
    LaunchedEffect(oldCooldown) {
        if (oldCooldown > 0) {
            delay(1_000)
            oldCooldown--
        }
    }
    LaunchedEffect(newCooldown) {
        if (newCooldown > 0) {
            delay(1_000)
            newCooldown--
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (verifyCurrentEmail && oldSessionId.isBlank()) {
            Text("先验证当前邮箱 ${user.email}", style = MaterialTheme.typography.bodyMedium)
            CaptchaInput(
                value = captchaCode,
                onValueChange = { captchaCode = it },
                imageBase64 = controller.publicAuth.captchaImageBase64,
                loading = controller.publicAuth.captchaLoading,
                enabled = !controller.publicAuth.busy,
                onRefresh = controller.publicAuth::loadCaptcha,
            )
            Button(
                onClick = {
                    controller.publicAuth.sendEmailCode(user.email, "change_email", captchaCode) { session ->
                        oldSessionId = session.sessionId
                        oldCooldown = 60
                        captchaCode = ""
                    }
                },
                enabled = !controller.publicAuth.busy && captchaCode.isNotBlank() && oldCooldown == 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (oldCooldown > 0) "${oldCooldown}秒后可重发" else "向当前邮箱发送验证码")
            }
        }

        if (verifyCurrentEmail && oldSessionId.isNotBlank()) {
            VerificationCodeField(oldCode, { oldCode = it }, "当前邮箱验证码")
            TextButton(
                onClick = {
                    oldSessionId = ""
                    oldCode = ""
                    captchaCode = ""
                    controller.publicAuth.loadCaptcha()
                },
                enabled = !controller.publicAuth.busy && oldCooldown == 0,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (oldCooldown > 0) "${oldCooldown}秒后可重新获取" else "重新获取当前邮箱验证码")
            }
        }

        if (!verifyCurrentEmail || oldSessionId.isNotBlank()) {
            OutlinedTextField(
                value = newEmail,
                onValueChange = {
                    newEmail = it
                    newSessionId = ""
                    newCode = ""
                },
                label = { Text("新邮箱地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            CaptchaInput(
                value = captchaCode,
                onValueChange = { captchaCode = it },
                imageBase64 = controller.publicAuth.captchaImageBase64,
                loading = controller.publicAuth.captchaLoading,
                enabled = !controller.publicAuth.busy && newSessionId.isBlank(),
                onRefresh = controller.publicAuth::loadCaptcha,
            )
            Button(
                onClick = {
                    controller.publicAuth.sendEmailCode(newEmail, "change_email", captchaCode) { session ->
                        newSessionId = session.sessionId
                        newCooldown = 60
                        captchaCode = ""
                    }
                },
                enabled = !controller.publicAuth.busy &&
                    newEmail.isNotBlank() && captchaCode.isNotBlank() && newCooldown == 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (newCooldown > 0) "${newCooldown}秒后可重发" else "向新邮箱发送验证码")
            }
        }

        if (newSessionId.isNotBlank()) {
            VerificationCodeField(newCode, { newCode = it }, "新邮箱验证码")
            TextButton(
                onClick = {
                    newSessionId = ""
                    newCode = ""
                    captchaCode = ""
                    controller.publicAuth.loadCaptcha()
                },
                enabled = !controller.publicAuth.busy && newCooldown == 0,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (newCooldown > 0) "${newCooldown}秒后可重新获取" else "重新获取新邮箱验证码")
            }
        }

        if (controller.publicAuth.error.isNotBlank()) {
            Text(
                controller.publicAuth.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    controller.profile.changeEmail(
                        oldSessionId = oldSessionId,
                        oldCode = oldCode,
                        newSessionId = newSessionId,
                        newCode = newCode,
                        onSuccess = onDone,
                    )
                },
                enabled = !controller.profile.busy && newSessionId.isNotBlank() && newCode.isNotBlank() &&
                    (!verifyCurrentEmail || oldCode.isNotBlank()),
            ) {
                if (controller.profile.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("确认修改")
                }
            }
        }
    }
}

@Composable
private fun VerificationCodeField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(8)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
