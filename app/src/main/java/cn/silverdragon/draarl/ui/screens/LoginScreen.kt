package cn.silverdragon.draarl.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.RegistrationResult
import kotlinx.coroutines.delay

private enum class AuthMode { LOGIN, REGISTER, FORGOT }

@Composable
fun LoginScreen(controller: AppController) {
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    val captchaBitmap = remember(controller.captchaImageBase64) {
        decodeCaptcha(controller.captchaImageBase64)?.asImageBitmap()
    }

    BackHandler(enabled = mode != AuthMode.LOGIN) {
        mode = AuthMode.LOGIN
        controller.clearPublicAuthState()
        controller.loadCaptcha()
    }
    LaunchedEffect(Unit) {
        controller.loadRegistrationConfig()
        controller.loadCaptcha()
    }
    LaunchedEffect(mode) {
        controller.clearPublicAuthState()
        controller.loadCaptcha()
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "DraARL 麟链",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = when (mode) {
                        AuthMode.LOGIN -> "通信客户端"
                        AuthMode.REGISTER -> "创建新账号"
                        AuthMode.FORGOT -> "找回密码"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(28.dp))
                when (mode) {
                    AuthMode.LOGIN -> LoginForm(
                        controller = controller,
                        captchaBitmap = captchaBitmap,
                        onRegister = { mode = AuthMode.REGISTER },
                        onForgot = { mode = AuthMode.FORGOT },
                    )
                    AuthMode.REGISTER -> RegisterFormStepped(
                        controller = controller,
                        captchaBitmap = captchaBitmap,
                        onLogin = {
                            mode = AuthMode.LOGIN
                            controller.loadCaptcha()
                        },
                    )
                    AuthMode.FORGOT -> ForgotPasswordFormStepped(
                        controller = controller,
                        captchaBitmap = captchaBitmap,
                        onLogin = {
                            mode = AuthMode.LOGIN
                            controller.loadCaptcha()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    controller: AppController,
    captchaBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captchaCode by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val submit = {
        if (username.isNotBlank() && password.isNotBlank() && captchaCode.isNotBlank()) {
            controller.login(username, password, captchaCode)
        }
    }
    LaunchedEffect(controller.captchaId) { captchaCode = "" }

    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("账号或邮箱") },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    Spacer(Modifier.height(12.dp))
    PasswordField(
        value = password,
        onValueChange = { password = it },
        label = "密码",
        visible = passwordVisible,
        onVisibleChange = { passwordVisible = it },
        imeAction = ImeAction.Next,
    )
    Spacer(Modifier.height(12.dp))
    CaptchaRow(
        value = captchaCode,
        onValueChange = { captchaCode = it },
        captchaBitmap = captchaBitmap,
        loading = controller.captchaLoading,
        enabled = !controller.loginBusy,
        onRefresh = controller::loadCaptcha,
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
    if (controller.loginError.isNotBlank()) ErrorText(controller.loginError)
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = submit,
        enabled = !controller.loginBusy &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            controller.captchaId.isNotBlank() &&
            captchaCode.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) {
        BusyButtonContent(controller.loginBusy, "登录")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onForgot) { Text("忘记密码") }
        TextButton(onClick = onRegister) { Text("注册账号") }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    steps: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, title ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = MaterialTheme.shapes.small,
                    color = when {
                        index < currentStep -> MaterialTheme.colorScheme.primary
                        index == currentStep -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (index < currentStep) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (index == currentStep)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index <= currentStep)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .padding(horizontal = 4.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (index < currentStep)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun RegisterFormStepped(
    controller: AppController,
    captchaBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onLogin: () -> Unit,
) {
    val needsEmailCode = controller.registrationRequiresEmailVerification
    val steps = if (needsEmailCode)
        listOf("基本信息", "联系方式", "设置密码", "邮箱验证")
    else
        listOf("基本信息", "联系方式", "设置密码")
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

    LaunchedEffect(controller.captchaId) { captchaCode = "" }
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

    StepIndicator(currentStep = currentStep, steps = steps)
    Spacer(Modifier.height(20.dp))

    when (currentStep) {
        0 -> StepBasicInfo(
            username = username,
            onUsernameChange = { username = it },
            callsign = callsign,
            onCallsignChange = { callsign = it },
            nickname = nickname,
            onNicknameChange = { nickname = it },
        )
        1 -> StepContactInfo(
            email = email,
            onEmailChange = { email = it },
            phone = phone,
            onPhoneChange = { phone = it },
        )
        2 -> StepPassword(
            password = password,
            onPasswordChange = { password = it },
            confirmPassword = confirmPassword,
            onConfirmPasswordChange = { confirmPassword = it },
            passwordVisible = passwordVisible,
            onPasswordVisibleChange = { passwordVisible = it },
        )
        3 -> StepEmailVerification(
            email = email,
            captchaBitmap = captchaBitmap,
            captchaCode = captchaCode,
            onCaptchaCodeChange = { captchaCode = it },
            emailCode = emailCode,
            onEmailCodeChange = { emailCode = it },
            countdown = countdown,
            captchaLoading = controller.captchaLoading,
            onRefreshCaptcha = controller::loadCaptcha,
            onSendCode = {
                controller.sendPublicEmailCode(email, "register", captchaCode) { session ->
                    emailSessionId = session.sessionId
                    countdown = 60
                }
            },
            busy = controller.publicAuthBusy,
        )
    }

    if (stepError.isNotBlank()) ErrorText(stepError)
    if (controller.publicAuthError.isNotBlank()) ErrorText(controller.publicAuthError)

    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (currentStep > 0) {
            OutlinedButton(
                onClick = {
                    stepError = ""
                    currentStep--
                },
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text("上一步")
            }
        }
        val isLastStep = currentStep == steps.lastIndex
        Button(
            onClick = {
                stepError = ""
                when (currentStep) {
                    0 -> {
                        if (username.isBlank()) {
                            stepError = "请输入用户名"
                            return@Button
                        }
                        if (username.length < 3 || username.length > 20) {
                            stepError = "用户名长度需为 3-20 个字符"
                            return@Button
                        }
                        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                            stepError = "用户名只能包含字母、数字和下划线"
                            return@Button
                        }
                        if (callsign.isBlank()) {
                            stepError = "请输入呼号"
                            return@Button
                        }
                        if (!callsign.matches(Regex("^[A-Za-z][A-Za-z0-9]{2,9}$"))) {
                            stepError = "呼号需以字母开头，3-10 个字符"
                            return@Button
                        }
                        currentStep++
                    }
                    1 -> {
                        if (email.isBlank()) {
                            stepError = "请输入邮箱"
                            return@Button
                        }
                        if (!email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                            stepError = "邮箱格式不正确"
                            return@Button
                        }
                        if (phone.isNotBlank() && !phone.matches(Regex("^1[3-9]\\d{9}$"))) {
                            stepError = "手机号格式不正确"
                            return@Button
                        }
                        currentStep++
                    }
                    2 -> {
                        if (password.length < 6) {
                            stepError = "密码长度至少 6 位"
                            return@Button
                        }
                        if (password != confirmPassword) {
                            stepError = "两次密码不一致"
                            return@Button
                        }
                        if (needsEmailCode) {
                            controller.loadCaptcha()
                        }
                        currentStep++
                    }
                    3 -> {
                        controller.registerAccount(
                            username = username,
                            callsign = callsign,
                            nickname = nickname,
                            email = email,
                            phone = phone,
                            password = password,
                            confirmPassword = confirmPassword,
                            sessionId = emailSessionId,
                            emailCode = emailCode,
                        ) { registered -> result = registered }
                    }
                }
            },
            enabled = !controller.publicAuthBusy,
            modifier = Modifier.weight(if (currentStep > 0) 1f else 1f).height(50.dp),
        ) {
            BusyButtonContent(
                controller.publicAuthBusy,
                if (isLastStep) "完成注册" else "下一步",
            )
        }
    }
    TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("已有账号？返回登录")
    }
}

@Composable
private fun StepBasicInfo(
    username: String,
    onUsernameChange: (String) -> Unit,
    callsign: String,
    onCallsignChange: (String) -> Unit,
    nickname: String,
    onNicknameChange: (String) -> Unit,
) {
    Text(
        "基本信息",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("用户名") },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = callsign,
        onValueChange = { onCallsignChange(it.uppercase()) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("呼号") },
        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = nickname,
        onValueChange = onNicknameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("昵称（可选）") },
        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}

@Composable
private fun StepContactInfo(
    email: String,
    onEmailChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
) {
    Text(
        "联系方式",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱") },
        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("手机号（可选）") },
        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
    )
}

@Composable
private fun StepPassword(
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
) {
    Text(
        "设置密码",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    PasswordField(
        value = password,
        onValueChange = onPasswordChange,
        label = "密码",
        visible = passwordVisible,
        onVisibleChange = onPasswordVisibleChange,
        imeAction = ImeAction.Next,
    )
    Spacer(Modifier.height(10.dp))
    PasswordField(
        value = confirmPassword,
        onValueChange = onConfirmPasswordChange,
        label = "确认密码",
        visible = passwordVisible,
        onVisibleChange = onPasswordVisibleChange,
        imeAction = ImeAction.Done,
    )
}

@Composable
private fun StepEmailVerification(
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
    busy: Boolean,
) {
    Text(
        "邮箱验证",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
    CaptchaRow(
        value = captchaCode,
        onValueChange = onCaptchaCodeChange,
        captchaBitmap = captchaBitmap,
        loading = captchaLoading,
        enabled = !busy,
        onRefresh = onRefreshCaptcha,
        keyboardActions = KeyboardActions.Default,
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = emailCode,
            onValueChange = onEmailCodeChange,
            modifier = Modifier.weight(1f),
            label = { Text("邮箱验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Spacer(Modifier.width(10.dp))
        OutlinedButton(
            onClick = onSendCode,
            enabled = !busy && countdown == 0 && captchaCode.isNotBlank(),
            modifier = Modifier.height(56.dp),
        ) {
            Text(if (countdown > 0) "${countdown}s" else "发验证码")
        }
    }
}

@Composable
private fun ForgotPasswordFormStepped(
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

    LaunchedEffect(controller.captchaId) { captchaCode = "" }
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

    if (controller.passwordResetComplete) {
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

    StepIndicator(currentStep = currentStep, steps = steps)
    Spacer(Modifier.height(20.dp))

    when (currentStep) {
        0 -> {
            Text(
                "验证邮箱",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                loading = controller.captchaLoading,
                enabled = !controller.publicAuthBusy,
                onRefresh = controller::loadCaptcha,
                keyboardActions = KeyboardActions.Default,
            )
        }
        1 -> {
            Text(
                "输入验证码",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                        controller.loadCaptcha()
                        captchaCode = ""
                        currentStep = 0
                    },
                    enabled = countdown == 0,
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(if (countdown > 0) "${countdown}s" else "重发")
                }
            }
        }
        2 -> {
            Text(
                "重置密码",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PasswordField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = "新密码",
                visible = passwordVisible,
                onVisibleChange = { passwordVisible = it },
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(10.dp))
            PasswordField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "确认新密码",
                visible = passwordVisible,
                onVisibleChange = { passwordVisible = it },
                imeAction = ImeAction.Done,
            )
        }
    }

    if (stepError.isNotBlank()) ErrorText(stepError)
    if (controller.publicAuthError.isNotBlank()) ErrorText(controller.publicAuthError)

    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (currentStep > 0) {
            OutlinedButton(
                onClick = {
                    stepError = ""
                    currentStep--
                },
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Text("上一步")
            }
        }
        val isLastStep = currentStep == steps.lastIndex
        Button(
            onClick = {
                stepError = ""
                when (currentStep) {
                    0 -> {
                        if (email.isBlank()) {
                            stepError = "请输入邮箱"
                            return@Button
                        }
                        if (!email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                            stepError = "邮箱格式不正确"
                            return@Button
                        }
                        if (captchaCode.isBlank()) {
                            stepError = "请输入图片验证码"
                            return@Button
                        }
                        controller.sendPublicEmailCode(email, "reset_password", captchaCode) { session ->
                            sessionId = session.sessionId
                            countdown = 60
                            currentStep = 1
                        }
                    }
                    1 -> {
                        if (emailCode.isBlank()) {
                            stepError = "请输入邮箱验证码"
                            return@Button
                        }
                        currentStep = 2
                    }
                    2 -> {
                        if (newPassword.length < 6) {
                            stepError = "密码长度至少 6 位"
                            return@Button
                        }
                        if (newPassword != confirmPassword) {
                            stepError = "两次密码不一致"
                            return@Button
                        }
                        controller.resetPassword(
                            sessionId = sessionId,
                            emailCode = emailCode,
                            newPassword = newPassword,
                            confirmPassword = confirmPassword,
                            onSuccess = {},
                        )
                    }
                }
            },
            enabled = !controller.publicAuthBusy,
            modifier = Modifier.weight(1f).height(50.dp),
        ) {
            BusyButtonContent(
                controller.publicAuthBusy,
                when {
                    currentStep == 0 -> "发送验证码"
                    isLastStep -> "重置密码"
                    else -> "下一步"
                },
            )
        }
    }
    TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("返回登录")
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { onVisibleChange(!visible) }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "隐藏密码" else "显示密码",
                )
            }
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
    )
}

@Composable
private fun CaptchaRow(
    value: String,
    onValueChange: (String) -> Unit,
    captchaBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    loading: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    keyboardActions: KeyboardActions,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text("图片验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = keyboardActions,
        )
        Spacer(Modifier.width(12.dp))
        Surface(
            modifier = Modifier.width(150.dp).height(56.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    enabled = enabled && !loading,
                    role = Role.Button,
                    onClickLabel = "刷新图片验证码",
                    onClick = onRefresh,
                ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                    captchaBitmap != null -> Image(
                        bitmap = captchaBitmap,
                        contentDescription = "图片验证码",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("获取验证码", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationSuccess(result: RegistrationResult?, onLogin: () -> Unit) {
    Text("注册成功", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        "账号已创建，请等待管理员审核。",
        modifier = Modifier.padding(top = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!result?.devicePassword.isNullOrBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("设备准入密码（仅显示一次）", fontWeight = FontWeight.SemiBold)
                Text(result?.devicePassword.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text("请立即保存，审核通过后可在设备管理中重新生成。")
            }
        }
    }
    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(50.dp)) {
        Text("返回登录")
    }
}

@Composable
private fun BusyButtonContent(busy: Boolean, text: String) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.height(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        Text(text)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp),
    )
}

private fun decodeCaptcha(value: String): Bitmap? {
    if (value.isBlank()) return null
    return runCatching {
        val encoded = value.substringAfter("base64,", value)
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
