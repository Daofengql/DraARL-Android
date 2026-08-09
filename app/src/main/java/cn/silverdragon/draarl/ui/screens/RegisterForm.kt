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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.RegistrationResult
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.CommandStyle
import kotlinx.coroutines.delay

@Composable
internal fun RegisterFormStepped(
    controller: AppController,
    captchaBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onLogin: () -> Unit
) {
    val needsEmailCode = controller.publicAuth.registrationRequiresEmailVerification
    val steps = if (needsEmailCode) {
        listOf("基本信息", "联系方式", "设置密码", "邮箱验证")
    } else {
        listOf("基本信息", "联系方式", "设置密码")
    }
    var currentStep by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }
    var callsign by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var captchaCode by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var emailSessionId by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var passwordVisible by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RegistrationResult?>(null) }
    var stepError by remember { mutableStateOf("") }

    LaunchedEffect(controller.publicAuth.captchaId) { captchaCode = "" }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }
    LaunchedEffect(email) {
        emailSessionId = ""
        emailCode = ""
    }

    if (result != null) {
        RegistrationSuccess(result = result, onLogin = onLogin)
        return
    }

    AuthStepIndicator(currentStep = currentStep, steps = steps)
    Spacer(Modifier.height(20.dp))

    when (currentStep) {
        0 -> RegisterBasicInfo(
            username = username,
            onUsernameChange = { username = it },
            callsign = callsign,
            onCallsignChange = { callsign = it },
            nickname = nickname,
            onNicknameChange = { nickname = it }
        )

        1 -> RegisterContactInfo(
            email = email,
            onEmailChange = { email = it },
            phone = phone,
            onPhoneChange = { phone = it }
        )

        2 -> RegisterPassword(
            password = password,
            onPasswordChange = { password = it },
            confirmPassword = confirmPassword,
            onConfirmPasswordChange = { confirmPassword = it },
            passwordVisible = passwordVisible,
            onPasswordVisibleChange = { passwordVisible = it }
        )

        3 -> RegisterEmailVerification(
            email = email,
            captchaBitmap = captchaBitmap,
            captchaCode = captchaCode,
            onCaptchaCodeChange = { captchaCode = it },
            emailCode = emailCode,
            onEmailCodeChange = { emailCode = it },
            countdown = countdown,
            captchaLoading = controller.publicAuth.captchaLoading,
            onRefreshCaptcha = controller.publicAuth::loadCaptcha,
            onSendCode = {
                controller.publicAuth.sendEmailCode(email, "register", captchaCode) { session ->
                    emailSessionId = session.sessionId
                    countdown = 60
                }
            },
            busy = controller.publicAuth.busy
        )
    }

    if (stepError.isNotBlank()) AuthErrorNotice(stepError)
    if (controller.publicAuth.error.isNotBlank()) AuthErrorNotice(controller.publicAuth.error)

    Spacer(Modifier.height(18.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (currentStep > 0) {
            CommandButton(
                label = "上一步",
                onClick = {
                    stepError = ""
                    currentStep--
                },
                modifier = Modifier.weight(1f)
            )
        }
        val isLastStep = currentStep == steps.lastIndex
        CommandButton(
            label = if (isLastStep) "完成注册" else "下一步",
            onClick = {
                stepError = ""
                when (currentStep) {
                    0 -> {
                        stepError = RegistrationValidation.basicInfo(username, callsign).orEmpty()
                        if (stepError.isBlank()) currentStep++
                    }

                    1 -> {
                        stepError = RegistrationValidation.contactInfo(email, phone).orEmpty()
                        if (stepError.isBlank()) currentStep++
                    }

                    2 -> {
                        stepError = RegistrationValidation.password(password, confirmPassword).orEmpty()
                        if (stepError.isBlank()) {
                            if (needsEmailCode) controller.publicAuth.loadCaptcha()
                            currentStep++
                        }
                    }

                    3 -> controller.publicAuth.register(
                        username = username,
                        callsign = callsign,
                        nickname = nickname,
                        email = email,
                        phone = phone,
                        password = password,
                        confirmPassword = confirmPassword,
                        sessionId = emailSessionId,
                        emailCode = emailCode
                    ) { registered -> result = registered }
                }
            },
            enabled = !controller.publicAuth.busy,
            loading = controller.publicAuth.busy,
            style = CommandStyle.PRIMARY,
            modifier = Modifier.weight(1f)
        )
    }
    TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("已有账号？返回登录")
    }
}

@Composable
private fun RegisterBasicInfo(
    username: String,
    onUsernameChange: (String) -> Unit,
    callsign: String,
    onCallsignChange: (String) -> Unit,
    nickname: String,
    onNicknameChange: (String) -> Unit
) {
    Text("基本信息", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("用户名") },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = callsign,
        onValueChange = { onCallsignChange(it.uppercase()) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("呼号") },
        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = nickname,
        onValueChange = onNicknameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("昵称（可选）") },
        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
    )
}

@Composable
private fun RegisterContactInfo(
    email: String,
    onEmailChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit
) {
    Text("联系方式", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱") },
        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("手机号（可选）") },
        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
    )
}

@Composable
private fun RegisterPassword(
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit
) {
    Text("设置密码", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    PasswordField(password, onPasswordChange, "密码", passwordVisible, onPasswordVisibleChange, ImeAction.Next)
    Spacer(Modifier.height(10.dp))
    PasswordField(
        confirmPassword,
        onConfirmPasswordChange,
        "确认密码",
        passwordVisible,
        onPasswordVisibleChange,
        ImeAction.Done
    )
}

@Composable
private fun RegisterEmailVerification(
    email: String,
    captchaBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    captchaCode: String,
    onCaptchaCodeChange: (String) -> Unit,
    emailCode: String,
    onEmailCodeChange: (String) -> Unit,
    countdown: Int,
    captchaLoading: Boolean,
    onRefreshCaptcha: () -> Unit,
    onSendCode: () -> Unit,
    busy: Boolean
) {
    Text("邮箱验证", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = email,
        onValueChange = {},
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱") },
        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
        singleLine = true,
        enabled = false
    )
    Spacer(Modifier.height(10.dp))
    CaptchaRow(
        value = captchaCode,
        onValueChange = onCaptchaCodeChange,
        captchaBitmap = captchaBitmap,
        loading = captchaLoading,
        enabled = !busy,
        onRefresh = onRefreshCaptcha,
        keyboardActions = KeyboardActions.Default
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = emailCode,
            onValueChange = onEmailCodeChange,
            modifier = Modifier.weight(1f),
            label = { Text("邮箱验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        Spacer(Modifier.width(10.dp))
        OutlinedButton(
            onClick = onSendCode,
            enabled = !busy && countdown == 0 && captchaCode.isNotBlank(),
            modifier = Modifier.height(56.dp)
        ) { Text(if (countdown > 0) "${countdown}s" else "发验证码") }
    }
}
