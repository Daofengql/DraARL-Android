package cn.silverdragon.draarl.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.data.RegistrationResult
import cn.silverdragon.draarl.ui.components.DraarlIconButton

@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    imeAction: ImeAction
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        trailingIcon = {
            DraarlIconButton(
                icon = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                label = if (visible) "隐藏密码" else "显示密码",
                onClick = { onVisibleChange(!visible) }
            )
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction)
    )
}

@Composable
internal fun CaptchaRow(
    value: String,
    onValueChange: (String) -> Unit,
    captchaBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    loading: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    keyboardActions: KeyboardActions
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text("图片验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = keyboardActions
        )
        Spacer(Modifier.width(12.dp))
        Surface(
            modifier = Modifier.width(150.dp).height(56.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    enabled = enabled && !loading,
                    role = Role.Button,
                    onClickLabel = "刷新图片验证码",
                    onClick = onRefresh
                ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)

                    captchaBitmap != null -> Image(
                        bitmap = captchaBitmap,
                        contentDescription = "图片验证码",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
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
internal fun RegistrationSuccess(result: RegistrationResult?, onLogin: () -> Unit) {
    Text("注册成功", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        "账号已创建，请等待管理员审核。",
        modifier = Modifier.padding(top = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!result?.devicePassword.isNullOrBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
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
internal fun BusyButtonContent(busy: Boolean, text: String) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.height(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        Text(text)
    }
}

@Composable
internal fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp)
    )
}

internal fun decodeCaptcha(value: String): Bitmap? {
    if (value.isBlank()) return null
    return runCatching {
        val encoded = value.substringAfter("base64,", value)
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
