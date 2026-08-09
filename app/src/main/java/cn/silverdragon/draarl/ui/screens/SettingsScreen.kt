package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.components.DraarlSettings
import cn.silverdragon.draarl.ui.components.DraarlSettingsGroup
import cn.silverdragon.draarl.ui.components.DraarlSettingsRow
import cn.silverdragon.draarl.ui.components.DraarlSettingsSectionTitle

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onAction: (SettingsMenuAction) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { onAction(SettingsMenuAction.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
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
                DraarlSettingsSectionTitle("账户")
                DraarlSettingsGroup {
                    DraarlSettingsRow(
                        item = DraarlSettings(
                            icon = Icons.Default.Lock,
                            title = "账号与安全",
                            detail = "密码、邮箱与登录信息",
                            onClick = { onAction(SettingsMenuAction.OpenAccountSecurity) }
                        )
                    )
                }
            }

            item {
                DraarlSettingsSectionTitle("应用")
                DraarlSettingsGroup {
                    DraarlSettingsRow(
                        item = DraarlSettings(
                            icon = Icons.Default.Settings,
                            title = "应用设置",
                            detail = "外观、通联、音频、后台与更新",
                            onClick = { onAction(SettingsMenuAction.OpenSystemSettings) }
                        ),
                        showDivider = true
                    )
                    DraarlSettingsRow(
                        item = DraarlSettings(
                            icon = Icons.Default.Storage,
                            title = "存储管理",
                            detail = "查看并清理语音、头像和消息缓存",
                            onClick = { onAction(SettingsMenuAction.OpenStorageSettings) }
                        ),
                        showDivider = true
                    )
                    DraarlSettingsRow(
                        item = DraarlSettings(
                            icon = Icons.Default.MyLocation,
                            title = "APRS 设置",
                            detail = "APRS-IS 位置上报与自动上报",
                            onClick = { onAction(SettingsMenuAction.OpenAprsSettings) }
                        )
                    )
                }
            }

            item {
                DraarlSettingsSectionTitle("会话")
                DraarlSettingsGroup {
                    DraarlSettingsRow(
                        item = DraarlSettings(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            title = "退出登录",
                            detail = "清除本机登录状态并断开当前通信会话",
                            onClick = { onAction(SettingsMenuAction.Logout) },
                            danger = true,
                            showChevron = false
                        )
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsSectionHeader(title: String) {
    DraarlSettingsSectionTitle(title)
}
