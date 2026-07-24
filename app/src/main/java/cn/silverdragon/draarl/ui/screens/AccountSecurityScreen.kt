package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(controller: AppController) {
    val user = controller.user ?: return
    var showChangePassword by remember { mutableStateOf(false) }
    var showChangeEmail by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号与安全") },
                navigationIcon = {
                    IconButton(onClick = { controller.navigate(AppPage.SETTINGS) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 修改密码
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(icon = Icons.Default.Lock, title = "修改密码")

                        if (showChangePassword) {
                            ChangePasswordSection(
                                controller = controller,
                                onDone = { showChangePassword = false },
                            )
                        } else {
                            Button(
                                onClick = { showChangePassword = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("修改密码")
                            }
                        }
                    }
                }
            }

            // 修改邮箱
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(icon = Icons.Default.Email, title = "邮箱")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    user.email.ifBlank { "未设置" },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    if (user.email.isBlank()) "设置邮箱可用于找回密码" else "修改邮箱需要验证当前邮箱",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = { showChangeEmail = !showChangeEmail },
                            ) {
                                Text(if (user.email.isBlank()) "设置邮箱" else "修改邮箱")
                            }
                        }

                        if (showChangeEmail) {
                            Spacer(Modifier.height(12.dp))
                            ChangeEmailSection(
                                controller = controller,
                                onDone = { showChangeEmail = false },
                            )
                        }
                    }
                }
            }

            // 登录信息
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(icon = Icons.Default.Person, title = "登录信息")

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoRow("用户ID", user.id.toString())
                            InfoRow("用户名", user.username)
                            InfoRow("角色", if (user.role == "admin") "管理员" else "普通用户")
                            InfoRow("状态", if (user.status == 1) "正常" else "已禁用")
                            InfoRow("最后登录时间", user.lastLoginTime.ifBlank { "-" })
                            InfoRow("登录IP", user.lastLoginIp.ifBlank { "-" })
                            if (user.lastLoginIpLocation.isNotBlank()) {
                                InfoRow("IP归属地", user.lastLoginIpLocation)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ChangePasswordSection(
    controller: AppController,
    onDone: () -> Unit,
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
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("新密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = { Text("密码长度至少6位") },
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("确认新密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    if (newPassword != confirmPassword) {
                        controller.showNotice("两次输入的密码不一致")
                        return@Button
                    }
                    if (newPassword.length < 6) {
                        controller.showNotice("密码长度至少6位")
                        return@Button
                    }
                    controller.changePassword(oldPassword, newPassword)
                    onDone()
                },
                enabled = !controller.contentLoading,
            ) {
                if (controller.contentLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("确认修改")
                }
            }
        }
    }
}

@Composable
private fun ChangeEmailSection(
    controller: AppController,
    onDone: () -> Unit,
) {
    val user = controller.user ?: return
    var email by remember { mutableStateOf(user.email) }
    var code by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("新邮箱地址") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        // TODO: 添加验证码发送和输入
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    // TODO: 实现修改邮箱逻辑
                    controller.showNotice("邮箱修改功能开发中")
                    onDone()
                },
            ) {
                Text("确认修改")
            }
        }
    }
}
