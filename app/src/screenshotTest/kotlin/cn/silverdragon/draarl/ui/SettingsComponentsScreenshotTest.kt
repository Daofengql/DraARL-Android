package cn.silverdragon.draarl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.data.StorageUsage
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlDialogContent
import cn.silverdragon.draarl.ui.components.DraarlSettings
import cn.silverdragon.draarl.ui.components.DraarlSettingsGroup
import cn.silverdragon.draarl.ui.components.DraarlSettingsRow
import cn.silverdragon.draarl.ui.components.DraarlSettingsSectionTitle
import cn.silverdragon.draarl.ui.components.InlineNotice
import cn.silverdragon.draarl.ui.components.StatusTone
import cn.silverdragon.draarl.ui.screens.StorageSettingsContent
import cn.silverdragon.draarl.ui.theme.DraarlTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Settings Rows Light", widthDp = 360, heightDp = 520, showBackground = true)
@Composable
fun SettingsRowsLightBaseline() {
    SettingsRowsBaseline(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "Settings Rows Dark Large Text",
    widthDp = 360,
    heightDp = 620,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun SettingsRowsDarkLargeTextBaseline() {
    SettingsRowsBaseline(darkTheme = true)
}

@PreviewTest
@Preview(name = "Storage Content Dark", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
fun StorageContentDarkBaseline() {
    DraarlTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            StorageSettingsContent(
                usage = StorageUsage(
                    audioBytes = 18_432_000,
                    avatarBytes = 3_980_000,
                    messageBytes = 42_740_000
                ),
                busy = false,
                onClear = {}
            )
        }
    }
}

@PreviewTest
@Preview(name = "Danger Dialog Light", widthDp = 411, heightDp = 520, showBackground = true)
@Composable
fun DangerDialogLightBaseline() {
    DraarlTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                DraarlDialogContent(
                    title = "清理全部缓存？",
                    dismissAction = DraarlAction("取消", {}),
                    confirmAction = DraarlAction("清理", {}, style = CommandStyle.DANGER)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "本地消息会被移除，之后进入 PTT 页面时会重新从服务器同步。登录信息不会被删除。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        InlineNotice(
                            text = "当前仍有语音正在播放，清理后本次播放会停止。",
                            tone = StatusTone.ERROR
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRowsBaseline(darkTheme: Boolean) {
    DraarlTheme(darkTheme = darkTheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                DraarlSettingsSectionTitle("应用", detail = "所有设置均保存在当前设备")
                DraarlSettingsGroup {
                    DraarlSettingsRow(
                        item = DraarlSettings(
                            Icons.Default.Settings,
                            "应用设置",
                            "外观、通联、音频、后台运行与客户端更新",
                            onClick = {}
                        ),
                        showDivider = true
                    )
                    DraarlSettingsRow(
                        item = DraarlSettings(
                            Icons.Default.Storage,
                            "存储管理",
                            "查看并清理语音、头像和消息缓存",
                            onClick = {}
                        )
                    )
                }
                DraarlSettingsSectionTitle("账户")
                DraarlSettingsGroup {
                    DraarlSettingsRow(
                        item = DraarlSettings(
                            Icons.Default.Lock,
                            "账号与安全",
                            "密码、邮箱与最近登录来源",
                            onClick = {}
                        ),
                        showDivider = true
                    )
                    DraarlSettingsRow(
                        item = DraarlSettings(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            "退出登录",
                            "断开当前通信会话",
                            onClick = {},
                            danger = true,
                            showChevron = false
                        )
                    )
                }
            }
        }
    }
}
