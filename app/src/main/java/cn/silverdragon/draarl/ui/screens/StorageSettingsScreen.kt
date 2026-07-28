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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.AppPage
import cn.silverdragon.draarl.data.StorageCategory
import cn.silverdragon.draarl.data.StorageUsage
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(controller: AppController) {
    var pendingClear by remember { mutableStateOf<StorageCategory?>(null) }
    LaunchedEffect(Unit) { controller.refreshStorageUsage() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("存储管理") },
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("缓存占用", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    formatBytes(controller.storageUsage.totalBytes),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (controller.storageBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        Text(
                            "缓存只保存在本机，清理不会影响服务器上的通联记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                StorageCategoryCard(
                    icon = Icons.Default.GraphicEq,
                    title = "语音缓存",
                    description = "已下载的 raw 语音，用于离线回放",
                    size = controller.storageUsage.audioBytes,
                    enabled = !controller.storageBusy,
                    onClear = { pendingClear = StorageCategory.AUDIO },
                )
            }
            item {
                StorageCategoryCard(
                    icon = Icons.Default.Image,
                    title = "头像缓存",
                    description = "用户头像的内存和磁盘缓存",
                    size = controller.storageUsage.avatarBytes,
                    enabled = !controller.storageBusy,
                    onClear = { pendingClear = StorageCategory.AVATARS },
                )
            }
            item {
                StorageCategoryCard(
                    icon = Icons.AutoMirrored.Filled.Message,
                    title = "消息记录缓存",
                    description = "本地保存的通联记录和同步索引",
                    size = controller.storageUsage.messageBytes,
                    enabled = !controller.storageBusy,
                    onClear = { pendingClear = StorageCategory.MESSAGES },
                )
            }
            item {
                OutlinedButton(
                    onClick = { pendingClear = StorageCategory.ALL },
                    enabled = !controller.storageBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("清理全部缓存")
                }
            }
        }
    }

    pendingClear?.let { category ->
        val isDestructive = category == StorageCategory.MESSAGES || category == StorageCategory.ALL
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text(if (category == StorageCategory.ALL) "清理全部缓存？" else "清理${category.displayName()}？") },
            text = {
                Text(
                    if (isDestructive) "本地消息会被移除，之后进入 PTT 页面时会重新从服务器同步。登录信息不会被删除。"
                    else "只会清理本机缓存，不会影响服务器数据。",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingClear = null
                        controller.clearStorage(category)
                    },
                ) { Text("清理") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingClear = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun StorageCategoryCard(
    icon: ImageVector,
    title: String,
    description: String,
    size: Long,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(formatBytes(size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onClear, enabled = enabled) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "清理$title")
            }
        }
    }
}

private fun StorageCategory.displayName(): String = when (this) {
    StorageCategory.AUDIO -> "语音缓存"
    StorageCategory.AVATARS -> "头像缓存"
    StorageCategory.MESSAGES -> "消息记录缓存"
    StorageCategory.ALL -> "全部缓存"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1_024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1_024.0 && index < units.lastIndex) {
        value /= 1_024.0
        index++
    }
    return String.format(Locale.CHINA, "%.1f %s", value, units[index])
}
