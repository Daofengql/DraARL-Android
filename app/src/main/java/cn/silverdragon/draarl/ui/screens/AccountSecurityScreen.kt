package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.ui.components.CommandIconButton
import cn.silverdragon.draarl.ui.components.DataRow
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import cn.silverdragon.draarl.ui.components.DraarlSettings
import cn.silverdragon.draarl.ui.components.DraarlSettingsGroup
import cn.silverdragon.draarl.ui.components.DraarlSettingsRow
import cn.silverdragon.draarl.ui.components.DraarlSettingsSectionTitle
import cn.silverdragon.draarl.ui.theme.appColors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(controller: AppController) {
    val user = controller.session.uiState.user ?: return
    var showChangePassword by remember { mutableStateOf(false) }
    var showChangeEmail by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号与安全") },
                navigationIcon = {
                    DraarlIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        onClick = { controller.navigate(AppPage.SETTINGS) }
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                DraarlSettingsSectionTitle("凭据", detail = "修改凭据后，当前设备会继续保持登录")
                DraarlSettingsGroup {
                    if (showChangePassword) {
                        AccountSecurityForm(
                            icon = Icons.Default.Lock,
                            title = "修改登录密码",
                            onClose = { showChangePassword = false }
                        ) {
                            ChangePasswordSection(controller, onDone = { showChangePassword = false })
                        }
                    } else {
                        DraarlSettingsRow(
                            item = DraarlSettings(
                                icon = Icons.Default.Lock,
                                title = "登录密码",
                                detail = "使用当前密码验证后设置新密码",
                                onClick = {
                                    showChangeEmail = false
                                    showChangePassword = true
                                }
                            ),
                            showDivider = true
                        )
                    }

                    if (showChangeEmail) {
                        AccountSecurityForm(
                            icon = Icons.Default.Email,
                            title = if (user.email.isBlank()) "设置邮箱" else "修改邮箱",
                            onClose = { showChangeEmail = false }
                        ) {
                            ChangeEmailSection(controller, onDone = { showChangeEmail = false })
                        }
                    } else {
                        DraarlSettingsRow(
                            item = DraarlSettings(
                                icon = Icons.Default.Email,
                                title = user.email.ifBlank { "未设置邮箱" },
                                detail = if (user.email.isBlank()) {
                                    "设置邮箱后可用于找回密码"
                                } else {
                                    "修改邮箱需要验证当前邮箱"
                                },
                                onClick = {
                                    showChangePassword = false
                                    showChangeEmail = true
                                }
                            )
                        )
                    }
                }
            }

            item {
                DraarlSettingsSectionTitle("登录信息", detail = "用于核对当前账户与最近登录来源")
                LoginInfoGroup(user)
            }
        }
    }
}

@Composable
private fun AccountSecurityForm(
    icon: ImageVector,
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                title,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                style = MaterialTheme.typography.titleSmall
            )
            CommandIconButton(onClick = onClose, contentDescription = "关闭$title", icon = Icons.Default.Close)
        }
        HorizontalDivider(color = MaterialTheme.appColors.divider)
        content()
    }
}

@Composable
private fun LoginInfoGroup(user: User) {
    val rows = buildList {
        add(Triple("用户 ID", user.id.toString(), true))
        add(Triple("用户名", user.username, true))
        add(Triple("角色", if (user.role == "admin") "管理员" else "普通用户", false))
        add(Triple("状态", if (user.status == 1) "正常" else "已禁用", false))
        add(Triple("最后登录时间", user.lastLoginTime.ifBlank { "-" }, true))
        add(Triple("登录 IP", user.lastLoginIp.ifBlank { "-" }, true))
        if (user.lastLoginIpLocation.isNotBlank()) add(Triple("IP 归属地", user.lastLoginIpLocation, false))
    }
    DraarlSettingsGroup {
        rows.forEachIndexed { index, (label, value, technical) ->
            DataRow(
                label = label,
                value = value,
                technical = technical,
                leadingIcon = if (index ==
                    0
                ) {
                    Icons.Default.Person
                } else {
                    null
                }
            )
            if (index < rows.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.appColors.divider)
            }
        }
    }
}
