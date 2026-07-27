package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import kotlinx.coroutines.delay

@Composable
internal fun ForgotPasswordFormStepped(
    controller: AppController,
    captchaBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onLogin: () -> Unit,
) {
    val steps = listOf("验证邮箱", "输入验证码", "重置密码")
    var currentStep by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var captchaCode by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var sessionId by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var passwordVisible by remember { mutableStateOf(false) }
    var stepError by remember { mutableStateOf("") }

    LaunchedEffect(controller.publicAuth.captchaId) { captchaCode = "" }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }
    LaunchedEffect(email) {
        sessionId = ""
        emailCode = ""
    }

    if (controller.publicAuth.passwordResetComplete) {
        Text("密码已重置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "现在可以使用新密码登录。",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(50.dp)) {
            Text("返回登录")
        }
        return
    }

    AuthStepIndicator(currentStep = currentStep, steps = steps)
    Spacer(Modifier.height(20.dp))

    when (currentStep) {
        0 -> {
            Text("验证邮箱", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("注册邮箱") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(12.dp))
            CaptchaRow(
                value = captchaCode,
                onValueChange = { captchaCode = it },
                captchaBitmap = captchaBitmap,
                loading = controller.publicAuth.captchaLoading,
                enabled = !controller.publicAuth.busy,
                onRefresh = controller.publicAuth::loadCaptcha,
                keyboardActions = KeyboardActions.Default,
            )
        }
        1 -> {
            Text("输入验证码", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("邮箱") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                enabled = false,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = emailCode,
                    onValueChange = { emailCode = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("邮箱验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = {
                        controller.publicAuth.loadCaptcha()
                        captchaCode = ""
                        currentStep = 0
                    },
                    enabled = countdown == 0,
                    modifier = Modifier.height(56.dp),
                ) { Text(if (countdown > 0) "${countdown}s" else "重发") }
            }
        }
        2 -> {
            Text("重置密码", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            PasswordField(newPassword, { newPassword = it }, "新密码", passwordVisible, { passwordVisible = it }, ImeAction.Next)
            Spacer(Modifier.height(10.dp))
            PasswordField(confirmPassword, { confirmPassword = it }, "确认新密码", passwordVisible, { passwordVisible = it }, ImeAction.Done)
        }
    }

    if (stepError.isNotBlank()) ErrorText(stepError)
    if (controller.publicAuth.error.isNotBlank()) ErrorText(controller.publicAuth.error)

    Spacer(Modifier.height(18.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (currentStep > 0) {
            OutlinedButton(
                onClick = {
                    stepError = ""
                    currentStep--
                },
                modifier = Modifier.weight(1f).height(50.dp),
            ) { Text("上一步") }
        }
        val isLastStep = currentStep == steps.lastIndex
        Button(
            onClick = {
                stepError = ""
                when (currentStep) {
                    0 -> {
                        stepError = PasswordResetValidation.emailStep(email, captchaCode).orEmpty()
                        if (stepError.isBlank()) {
                            controller.publicAuth.sendEmailCode(email, "reset_password", captchaCode) { session ->
                                sessionId = session.sessionId
                                countdown = 60
                                currentStep = 1
                            }
                        }
                    }
                    1 -> {
                        if (emailCode.isBlank()) stepError = "请输入邮箱验证码" else currentStep = 2
                    }
                    2 -> {
                        stepError = RegistrationValidation.password(newPassword, confirmPassword).orEmpty()
                        if (stepError.isBlank()) {
                            controller.publicAuth.resetPassword(
                                sessionId = sessionId,
                                emailCode = emailCode,
                                newPassword = newPassword,
                                confirmPassword = confirmPassword,
                                onSuccess = {},
                            )
                        }
                    }
                }
            },
            enabled = !controller.publicAuth.busy,
            modifier = Modifier.weight(1f).height(50.dp),
        ) {
            BusyButtonContent(
                controller.publicAuth.busy,
                when {
                    currentStep == 0 -> "发送验证码"
                    isLastStep -> "重置密码"
                    else -> "下一步"
                },
            )
        }
    }
    TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("返回登录") }
}
