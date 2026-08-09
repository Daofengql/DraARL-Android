package cn.silverdragon.draarl.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.tools.ToolDestination

@Composable
fun ToolsScreen(controller: AppController) {
    val tools = controller.tools
    BackHandler(enabled = tools.canGoBack, onBack = tools::back)
    when (tools.destination) {
        ToolDestination.HOME -> ToolsHome(
            approved = controller.session.uiState.user?.isApproved == true,
            error = tools.error,
            onClearError = tools::clearError,
            onOpen = { tools.open(it, controller.session.uiState.user) }
        )

        ToolDestination.BLE -> BleProvisionScreen(tools = tools, onBack = tools::back)

        ToolDestination.RELAYS -> RelaySearchScreen(tools = tools, onBack = tools::back)

        ToolDestination.LOGBOOK -> LogbookScreen(controller = controller, tools = tools, onBack = tools::back)

        ToolDestination.LOGBOOK_EDITOR -> LogbookEditorScreen(tools = tools, onBack = tools::back)

        ToolDestination.MAIDENHEAD -> MaidenheadToolScreen(onBack = tools::back)
    }
}

@Composable
internal fun ToolsHome(approved: Boolean, error: String, onClearError: () -> Unit, onOpen: (ToolDestination) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        if (error.isNotBlank()) {
            item { ToolError(error, onClearError) }
        }
        TOOL_SECTIONS.forEach { section ->
            item(key = "header-${section.title}") {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            items(section.entries, key = ToolEntry::destination) { item ->
                val enabled = !item.requiresApproval || approved
                ToolRow(item = item, enabled = enabled) {
                    onOpen(item.destination)
                }
                if (item != section.entries.last()) HorizontalDivider(Modifier.padding(start = 76.dp))
            }
        }
    }
}

@Composable
private fun ToolRow(item: ToolEntry, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.titleSmall)
            Text(
                if (enabled) item.description else "${item.description} · 需要账号审核",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

private data class ToolEntry(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val destination: ToolDestination,
    val requiresApproval: Boolean = false
)

private data class ToolSection(val title: String, val entries: List<ToolEntry>)

private val TOOL_SECTIONS = listOf(
    ToolSection(
        "设备与电台",
        listOf(
            ToolEntry("蓝牙配置", "配置兼容蓝牙电台", Icons.AutoMirrored.Filled.BluetoothSearching, ToolDestination.BLE),
            ToolEntry("中继台查询", "按位置和频率检索中继台", Icons.Default.SettingsInputAntenna, ToolDestination.RELAYS)
        )
    ),
    ToolSection(
        "记录与计算",
        listOf(
            ToolEntry("通联日志", "查看与维护通联记录", Icons.AutoMirrored.Filled.MenuBook, ToolDestination.LOGBOOK, true),
            ToolEntry("梅登海德网格", "坐标与网格定位换算", Icons.Default.GridOn, ToolDestination.MAIDENHEAD)
        )
    )
)
