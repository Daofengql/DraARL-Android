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
        ToolDestination.HOME -> ToolsHome(controller)
        ToolDestination.BLE -> BleProvisionScreen(tools = tools, onBack = tools::back)
        ToolDestination.RELAYS -> RelaySearchScreen(tools = tools, onBack = tools::back)
        ToolDestination.LOGBOOK -> LogbookScreen(controller = controller, tools = tools, onBack = tools::back)
        ToolDestination.LOGBOOK_EDITOR -> LogbookEditorScreen(tools = tools, onBack = tools::back)
        ToolDestination.MAIDENHEAD -> MaidenheadToolScreen(onBack = tools::back)
    }
}

@Composable
private fun ToolsHome(controller: AppController) {
    val tools = controller.tools
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        if (tools.error.isNotBlank()) {
            item { ToolError(tools.error, tools::clearError) }
        }
        items(TOOL_ENTRIES, key = ToolEntry::destination) { item ->
            val enabled = !item.requiresApproval || controller.user?.isApproved == true
            ToolRow(item = item, enabled = enabled) {
                tools.open(item.destination, controller.user)
            }
            if (item != TOOL_ENTRIES.last()) HorizontalDivider(Modifier.padding(start = 68.dp))
        }
    }
}

@Composable
private fun ToolRow(item: ToolEntry, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.titleMedium)
            if (!enabled) {
                Text("需要账号审核", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

private data class ToolEntry(
    val label: String,
    val icon: ImageVector,
    val destination: ToolDestination,
    val requiresApproval: Boolean = false,
)

private val TOOL_ENTRIES = listOf(
    ToolEntry("蓝牙配置", Icons.AutoMirrored.Filled.BluetoothSearching, ToolDestination.BLE),
    ToolEntry("中继台查询", Icons.Default.SettingsInputAntenna, ToolDestination.RELAYS),
    ToolEntry("通联日志", Icons.AutoMirrored.Filled.MenuBook, ToolDestination.LOGBOOK, true),
    ToolEntry("梅登海德网格", Icons.Default.GridOn, ToolDestination.MAIDENHEAD),
)
