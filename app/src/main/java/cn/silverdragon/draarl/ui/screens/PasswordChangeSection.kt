package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.CommandStyle

@Composable
internal fun ChangePasswordSection(controller: AppController, onDone: () -> Unit) {
    ChangePasswordContent(
        busy = controller.profile.busy,
        onValidationError = controller::showNotice,
        onSubmit = { oldPassword, newPassword ->
            controller.profile.changePassword(oldPassword, newPassword)
            onDone()
        }
    )
}

@Composable
internal fun ChangePasswordContent(
    busy: Boolean,
    onValidationError: (String) -> Unit,
    onSubmit: (oldPassword: String, newPassword: String) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = oldPassword,
            onValueChange = { oldPassword = it },
            label = { Text("当前密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("新密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = { Text("密码长度至少6位") }
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("确认新密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        CommandButton(
            label = "确认修改",
            onClick = {
                when {
                    newPassword != confirmPassword -> onValidationError("两次输入的密码不一致")
                    newPassword.length < 6 -> onValidationError("密码长度至少6位")
                    else -> onSubmit(oldPassword, newPassword)
                }
            },
            enabled = !busy,
            loading = busy,
            style = CommandStyle.PRIMARY,
            leadingIcon = Icons.Default.Save,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
