package cn.silverdragon.draarl.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.silverdragon.draarl.AppController

private enum class AuthMode { LOGIN, REGISTER, FORGOT }

@Composable
fun LoginScreen(controller: AppController) {
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    val captchaBitmap = remember(controller.publicAuth.captchaImageBase64) {
        decodeCaptcha(controller.publicAuth.captchaImageBase64)?.asImageBitmap()
    }

    BackHandler(enabled = mode != AuthMode.LOGIN) {
        mode = AuthMode.LOGIN
        controller.publicAuth.clearFlowState()
        controller.publicAuth.loadCaptcha()
    }
    LaunchedEffect(Unit) {
        controller.publicAuth.loadRegistrationConfig()
        controller.publicAuth.loadCaptcha()
    }
    LaunchedEffect(mode) {
        controller.publicAuth.clearFlowState()
        controller.publicAuth.loadCaptcha()
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
                            controller.publicAuth.loadCaptcha()
                        },
                    )
                    AuthMode.FORGOT -> ForgotPasswordFormStepped(
                        controller = controller,
                        captchaBitmap = captchaBitmap,
                        onLogin = {
                            mode = AuthMode.LOGIN
                            controller.publicAuth.loadCaptcha()
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
    LaunchedEffect(controller.publicAuth.captchaId) { captchaCode = "" }

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
        loading = controller.publicAuth.captchaLoading,
        enabled = !controller.loginBusy,
        onRefresh = controller.publicAuth::loadCaptcha,
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
    if (controller.loginError.isNotBlank()) ErrorText(controller.loginError)
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = submit,
        enabled = !controller.loginBusy &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            controller.publicAuth.captchaId.isNotBlank() &&
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
