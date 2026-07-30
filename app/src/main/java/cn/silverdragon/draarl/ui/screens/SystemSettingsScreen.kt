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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage
import cn.silverdragon.draarl.data.AppDisplayScale
import cn.silverdragon.draarl.data.AppThemeMode
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SystemSettingsScreen(controller: AppController) {
    val context = LocalContext.current
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            controller.setPttOverlayEnabled(true)
        } else {
            controller.showNotice("需要麦克风权限才能使用悬浮 PTT")
        }
    }
    val overlayPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!controller.canDrawPttOverlay()) {
            controller.showNotice("需要悬浮窗权限才能显示 PTT 按钮")
        } else if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            controller.setPttOverlayEnabled(true)
        }
    }

    fun requestOverlayEnabled() {
        when {
            !controller.canDrawPttOverlay() -> overlayPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri(),
                ),
            )
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED -> microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            else -> controller.setPttOverlayEnabled(true)
        }
    }

    LaunchedEffect(Unit) { controller.reconcilePttOverlayPermission() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统设置") },
                navigationIcon = {
                    IconButton(onClick = { controller.navigate(AppPage.SETTINGS) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSectionHeader("显示")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FormatSize,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(16.dp))
                            Text("界面缩放", style = MaterialTheme.typography.bodyLarge)
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AppDisplayScale.entries.forEachIndexed { index, scale ->
                                SegmentedButton(
                                    selected = controller.appDisplayScale == scale,
                                    onClick = { controller.updateAppDisplayScale(scale) },
                                    shape = SegmentedButtonDefaults.itemShape(index, AppDisplayScale.entries.size),
                                ) {
                                    Text(scale.displayName())
                                }
                            }
                        }
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("外观模式", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "同步调整界面、系统栏和地图明暗",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AppThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = controller.appThemeMode == mode,
                                    onClick = { controller.updateAppThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, AppThemeMode.entries.size),
                                ) {
                                    Text(mode.displayName())
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingsSectionHeader("通信")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("发射超时", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "持续发射达到此时长后自动结束，范围 10–600 秒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${controller.transmitTimeoutSeconds} 秒",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = controller.transmitTimeoutSeconds.toFloat(),
                            onValueChange = {
                                controller.updateTransmitTimeoutSeconds((it / 10f).roundToInt() * 10)
                            },
                            valueRange = 10f..600f,
                            steps = 58,
                        )
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("降噪强度", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "播放降噪开启时生效",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${controller.playbackDenoiseStrengthPercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Slider(
                            value = controller.playbackDenoiseStrengthPercent.toFloat(),
                            onValueChange = {
                                controller.updatePlaybackDenoiseStrengthPercent((it / 5f).roundToInt() * 5)
                            },
                            valueRange = 0f..100f,
                            steps = 19,
                        )
                    }
                }
            }
            item {
                SettingsSectionHeader("后台通信")
                Card(modifier = Modifier.fillMaxWidth()) {
                    OverlaySettingItem(
                        checked = controller.pttOverlayEnabled,
                        enabled = controller.user?.isApproved == true,
                        onCheckedChange = { enabled ->
                            if (enabled) requestOverlayEnabled() else controller.setPttOverlayEnabled(false)
                        },
                    )
                }
            }
        }
    }
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

@Composable
private fun OverlaySettingItem(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PictureInPictureAlt,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("悬浮 PTT", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (enabled) "切到后台后显示可拖动的 PTT 按钮" else "账号审核通过后可用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
        )
    }
}
