package cn.silverdragon.draarl.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cn.silverdragon.draarl.data.AppDisplayScale
import cn.silverdragon.draarl.data.AppThemeMode
import cn.silverdragon.draarl.radio.TransmitTailTone
import cn.silverdragon.draarl.settings.SettingsController
import cn.silverdragon.draarl.settings.SettingsEvent
import cn.silverdragon.draarl.ui.components.AppUpdateFeedback
import cn.silverdragon.draarl.ui.components.DraarlScreenHeader
import cn.silverdragon.draarl.update.AppUpdateInfo
import cn.silverdragon.draarl.update.AppUpdateStatus
import kotlin.math.roundToInt

data class SystemSettingsUpdateState(
    val currentVersionName: String,
    val status: AppUpdateStatus,
    val info: AppUpdateInfo?,
    val message: String,
    val progress: () -> Float
)

sealed interface SystemSettingsAction {
    data object Back : SystemSettingsAction
    data class ShowNotice(val message: String) : SystemSettingsAction
    data object CheckAppUpdate : SystemSettingsAction
    data object DownloadAndInstallUpdate : SystemSettingsAction
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SystemSettingsScreen(
    settings: SettingsController,
    userApproved: Boolean,
    update: SystemSettingsUpdateState,
    onAction: (SystemSettingsAction) -> Unit
) {
    val context = LocalContext.current
    val state by remember(settings) {
        derivedStateOf(structuralEqualityPolicy()) {
            SystemSettingsRootState.from(settings.uiState)
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            settings.setPttOverlayEnabled(true)
        } else {
            onAction(SystemSettingsAction.ShowNotice("需要麦克风权限才能使用悬浮 PTT"))
        }
    }
    val overlayPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!settings.canDrawPttOverlay()) {
            onAction(SystemSettingsAction.ShowNotice("需要悬浮窗权限才能显示 PTT 按钮"))
        } else if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            settings.setPttOverlayEnabled(true)
        }
    }

    fun requestOverlayEnabled() {
        when {
            !settings.canDrawPttOverlay() -> overlayPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()
                )
            )

            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED -> microphonePermission.launch(Manifest.permission.RECORD_AUDIO)

            else -> settings.setPttOverlayEnabled(true)
        }
    }

    LaunchedEffect(settings) { settings.reconcilePttOverlayPermission() }

    Scaffold(
        topBar = {
            DraarlScreenHeader(
                title = "应用设置",
                onBack = { onAction(SystemSettingsAction.Back) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SettingsGroup("外观") {
                    SettingsControlHeader(
                        icon = Icons.Default.FormatSize,
                        title = "界面缩放",
                        summary = "调整内容密度与控件尺寸"
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)
                    ) {
                        AppDisplayScale.entries.forEachIndexed { index, scale ->
                            SegmentedButton(
                                selected = state.appDisplayScale == scale,
                                onClick = { settings.onEvent(SettingsEvent.DisplayScaleChanged(scale)) },
                                shape = SegmentedButtonDefaults.itemShape(index, AppDisplayScale.entries.size)
                            ) {
                                Text(scale.displayName())
                            }
                        }
                    }
                    SettingsDivider()
                    SettingsControlHeader(
                        icon = Icons.Default.Palette,
                        title = "外观模式",
                        summary = "界面、系统栏和地图使用相同明暗模式"
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)
                    ) {
                        AppThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.appThemeMode == mode,
                                onClick = { settings.onEvent(SettingsEvent.ThemeModeChanged(mode)) },
                                shape = SegmentedButtonDefaults.itemShape(index, AppThemeMode.entries.size)
                            ) {
                                Text(mode.displayName())
                            }
                        }
                    }
                }
            }

            item {
                ControllerTransmitTimeoutSettings(settings)
            }

            item {
                val tailToneEnabled = state.transmitTailTone != TransmitTailTone.OFF
                SettingsGroup("音频") {
                    SettingsControlHeader(
                        icon = Icons.Default.MusicNote,
                        title = "结束尾音",
                        summary = "发射结束时在本机播放所选设备风格尾音",
                        value = state.transmitTailTone.displayName()
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TransmitTailTone.entries.forEach { tone ->
                            FilterChip(
                                selected = state.transmitTailTone == tone,
                                onClick = { settings.onEvent(SettingsEvent.TransmitTailToneChanged(tone)) },
                                label = { Text(tone.displayName()) }
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Filled.Send,
                        title = "发送至频道",
                        summary = if (tailToneEnabled) {
                            "开启后对方也会收到尾音；关闭时仅本机播放"
                        } else {
                            "选择尾音样式后可用"
                        },
                        checked = state.transmitTailToneToRemoteEnabled,
                        enabled = tailToneEnabled,
                        onCheckedChange = {
                            settings.onEvent(SettingsEvent.TransmitTailToneToRemoteChanged(it))
                        }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Filled.CallReceived,
                        title = "接收结束提示",
                        summary = if (tailToneEnabled) {
                            "每段接收语音结束后，在本机追加一次所选尾音"
                        } else {
                            "选择尾音样式后可用"
                        },
                        checked = state.receiveTailToneEnabled,
                        enabled = tailToneEnabled,
                        onCheckedChange = { settings.onEvent(SettingsEvent.ReceiveTailToneChanged(it)) }
                    )
                    SettingsDivider()
                    ControllerPlaybackDenoiseStrengthSetting(settings)
                }
            }

            item {
                SettingsGroup("后台与权限") {
                    SettingsToggleRow(
                        icon = Icons.Default.PictureInPictureAlt,
                        title = "悬浮 PTT",
                        summary = if (userApproved) {
                            "切到后台后显示可拖动的 PTT 按钮"
                        } else {
                            "账号审核通过后可用"
                        },
                        checked = state.pttOverlayEnabled,
                        enabled = userApproved,
                        onCheckedChange = { enabled ->
                            if (enabled) requestOverlayEnabled() else settings.setPttOverlayEnabled(false)
                        }
                    )
                }
            }

            item {
                SettingsGroup("软件更新") {
                    SettingsControlHeader(
                        icon = Icons.Default.SystemUpdate,
                        title = "DraARL Android",
                        summary = "当前安装版本",
                        value = update.currentVersionName
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.Refresh,
                        title = "自动检查更新",
                        summary = "登录或启动后检查可用的新版本",
                        checked = state.autoCheckAppUpdate,
                        onCheckedChange = { settings.onEvent(SettingsEvent.AutoCheckAppUpdateChanged(it)) }
                    )
                    if (
                        update.message.isNotBlank() || update.info != null ||
                        update.status == AppUpdateStatus.INSTALL_PERMISSION_REQUIRED
                    ) {
                        SettingsDivider()
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppUpdateFeedback(status = update.status, message = update.message)
                            update.info?.changelog?.takeIf(String::isNotBlank)?.let { changelog ->
                                Text(
                                    changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (update.status == AppUpdateStatus.DOWNLOADING) {
                                LinearProgressIndicator(
                                    progress = update.progress,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val busy = update.status == AppUpdateStatus.CHECKING ||
                            update.status == AppUpdateStatus.DOWNLOADING
                        OutlinedButton(
                            onClick = { onAction(SystemSettingsAction.CheckAppUpdate) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (update.status == AppUpdateStatus.CHECKING) "检查中" else "检查更新")
                        }
                        if (update.info != null) {
                            Button(
                                onClick = { onAction(SystemSettingsAction.DownloadAndInstallUpdate) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (update.status == AppUpdateStatus.READY_TO_INSTALL) {
                                        "重新安装"
                                    } else {
                                        "安装更新"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControllerTransmitTimeoutSettings(settings: SettingsController) {
    val timeoutSeconds by remember(settings) {
        derivedStateOf(structuralEqualityPolicy()) { settings.uiState.transmitTimeoutSeconds }
    }
    SettingsGroup("通联") {
        SettingsControlHeader(
            icon = Icons.Default.Timer,
            title = "发射超时",
            summary = "达到设定时长后自动结束发射",
            value = "$timeoutSeconds 秒"
        )
        Slider(
            value = timeoutSeconds.toFloat(),
            onValueChange = {
                settings.onEvent(SettingsEvent.TransmitTimeoutChanged((it / 10f).roundToInt() * 10))
            },
            valueRange = 10f..600f,
            steps = 58,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun ControllerPlaybackDenoiseStrengthSetting(settings: SettingsController) {
    val strengthPercent by remember(settings) {
        derivedStateOf(structuralEqualityPolicy()) { settings.uiState.playbackDenoiseStrengthPercent }
    }
    SettingsControlHeader(
        icon = Icons.Default.GraphicEq,
        title = "接收降噪强度",
        summary = "PTT 页面开启播放降噪时生效",
        value = "$strengthPercent%"
    )
    Slider(
        value = strengthPercent.toFloat(),
        onValueChange = {
            settings.onEvent(SettingsEvent.PlaybackDenoiseStrengthChanged((it / 5f).roundToInt() * 5))
        },
        valueRange = 0f..100f,
        steps = 19,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SettingsSectionHeader(title)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            content = { Column(content = content) }
        )
    }
}

@Composable
private fun SettingsControlHeader(icon: ImageVector, title: String, summary: String, value: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        value?.let {
            Spacer(Modifier.width(12.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon, enabled)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null
        )
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector, enabled: Boolean = true) {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = MaterialTheme.shapes.small,
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.outline
        }
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(7.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
}

private fun AppDisplayScale.displayName(): String = when (this) {
    AppDisplayScale.COMPACT -> "紧凑"
    AppDisplayScale.STANDARD -> "标准"
    AppDisplayScale.COMFORTABLE -> "宽松"
}

private fun AppThemeMode.displayName(): String = when (this) {
    AppThemeMode.FOLLOW_SYSTEM -> "跟随"
    AppThemeMode.LIGHT -> "日间"
    AppThemeMode.DARK -> "夜间"
}

private fun TransmitTailTone.displayName(): String = when (this) {
    TransmitTailTone.OFF -> "关闭"
    TransmitTailTone.SHORT_BEEP -> "短鸣"
    TransmitTailTone.MOTOROLA_STYLE -> "摩托罗拉风格"
    TransmitTailTone.DOUBLE_BEEP -> "双音"
    TransmitTailTone.RISING_TRIPLE -> "上行三音"
}
