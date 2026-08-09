package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.silverdragon.draarl.data.StorageCategory
import cn.silverdragon.draarl.data.StorageUsage
import cn.silverdragon.draarl.settings.SettingsController
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.CommandIconButton
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlDialog
import cn.silverdragon.draarl.ui.components.DraarlScreenHeader
import cn.silverdragon.draarl.ui.components.DraarlSettings
import cn.silverdragon.draarl.ui.components.DraarlSettingsGroup
import cn.silverdragon.draarl.ui.components.DraarlSettingsRow
import cn.silverdragon.draarl.ui.components.DraarlSettingsSectionTitle
import cn.silverdragon.draarl.ui.theme.appColors
import cn.silverdragon.draarl.ui.theme.dataTypography
import java.util.Locale

@Composable
fun StorageSettingsScreen(settings: SettingsController, onBack: () -> Unit) {
    var pendingClear by remember { mutableStateOf<StorageCategory?>(null) }
    val state = settings.uiState
    LaunchedEffect(settings) { settings.refreshStorageUsage() }

    Scaffold(
        topBar = {
            DraarlScreenHeader(title = "存储管理", onBack = onBack)
        }
    ) { innerPadding ->
        StorageSettingsContent(
            usage = state.storageUsage,
            busy = state.storageBusy,
            onClear = { pendingClear = it },
            modifier = Modifier.padding(innerPadding)
        )
    }

    pendingClear?.let { category ->
        StorageClearDialog(
            category = category,
            onDismiss = { pendingClear = null },
            onConfirm = {
                pendingClear = null
                settings.clearStorage(category)
            }
        )
    }
}

@Composable
internal fun StorageSettingsContent(
    usage: StorageUsage,
    busy: Boolean,
    onClear: (StorageCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { StorageSummary(usage.totalBytes, busy) }
        item {
            DraarlSettingsSectionTitle("本机缓存", detail = "按数据类型查看占用并单独清理")
            DraarlSettingsGroup {
                StorageCategoryRow(
                    item = StorageCategoryItem(
                        Icons.Default.GraphicEq,
                        StorageCategory.AUDIO,
                        "已下载的 raw 语音，用于离线回放",
                        usage.audioBytes
                    ),
                    enabled = !busy,
                    showDivider = true,
                    onClear = onClear
                )
                StorageCategoryRow(
                    item = StorageCategoryItem(
                        Icons.Default.Image,
                        StorageCategory.AVATARS,
                        "用户头像的内存和磁盘缓存",
                        usage.avatarBytes
                    ),
                    enabled = !busy,
                    showDivider = true,
                    onClear = onClear
                )
                StorageCategoryRow(
                    item = StorageCategoryItem(
                        Icons.AutoMirrored.Filled.Message,
                        StorageCategory.MESSAGES,
                        "本地通联记录与同步索引",
                        usage.messageBytes
                    ),
                    enabled = !busy,
                    onClear = onClear
                )
            }
        }
        item {
            CommandButton(
                label = "清理全部缓存",
                onClick = { onClear(StorageCategory.ALL) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                style = CommandStyle.DANGER,
                supportingText = "登录信息与服务器数据不会被删除",
                leadingIcon = Icons.Default.DeleteSweep
            )
        }
    }
}

@Composable
private fun StorageSummary(totalBytes: Long, busy: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.appColors.divider)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("本机缓存占用", style = MaterialTheme.typography.labelLarge)
                Text(
                    formatBytes(totalBytes),
                    style = MaterialTheme.dataTypography.value.copy(fontSize = 24.sp, lineHeight = 30.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "清理不会影响服务器上的通联记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun StorageCategoryRow(
    item: StorageCategoryItem,
    enabled: Boolean,
    showDivider: Boolean = false,
    onClear: (StorageCategory) -> Unit
) {
    DraarlSettingsRow(
        item = DraarlSettings(
            icon = item.icon,
            title = item.category.displayName(),
            detail = item.description,
            showChevron = false
        ),
        enabled = enabled,
        showDivider = showDivider,
        trailing = {
            Text(
                formatBytes(item.size),
                style = MaterialTheme.dataTypography.value,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CommandIconButton(
                onClick = { onClear(item.category) },
                contentDescription = "清理${item.category.displayName()}",
                icon = Icons.Default.DeleteSweep,
                enabled = enabled,
                danger = item.category == StorageCategory.MESSAGES,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    )
}

private data class StorageCategoryItem(
    val icon: ImageVector,
    val category: StorageCategory,
    val description: String,
    val size: Long
)

@Composable
private fun StorageClearDialog(category: StorageCategory, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val isDestructive = category == StorageCategory.MESSAGES || category == StorageCategory.ALL
    DraarlDialog(
        title = if (category == StorageCategory.ALL) "清理全部缓存？" else "清理${category.displayName()}？",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("取消", onDismiss),
        confirmAction = DraarlAction(
            label = "清理",
            onClick = onConfirm,
            style = if (isDestructive) CommandStyle.DANGER else CommandStyle.PRIMARY
        )
    ) {
        Text(
            text = if (isDestructive) {
                "本地消息会被移除，之后进入 PTT 页面时会重新从服务器同步。登录信息不会被删除。"
            } else {
                "只会清理本机缓存，不会影响服务器数据。"
            },
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun StorageCategory.displayName(): String = when (this) {
    StorageCategory.AUDIO -> "语音缓存"
    StorageCategory.AVATARS -> "头像缓存"
    StorageCategory.MESSAGES -> "消息记录缓存"
    StorageCategory.ALL -> "全部缓存"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < BYTES_PER_UNIT) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= BYTES_PER_UNIT && index < units.lastIndex) {
        value /= BYTES_PER_UNIT
        index++
    }
    return String.format(Locale.CHINA, "%.1f %s", value, units[index])
}

private const val BYTES_PER_UNIT = 1_024L
