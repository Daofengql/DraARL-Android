package cn.silverdragon.draarl.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MultipleStop
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.tools.ToolDestination
import cn.silverdragon.draarl.ui.theme.appMotion

@Composable
fun ToolsScreen(controller: AppController) {
    val tools = controller.tools
    BackHandler(enabled = tools.canGoBack, onBack = tools::back)
    val motion = MaterialTheme.appMotion
    AnimatedContent(
        targetState = tools.destination,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val direction = if (toolDestinationDepth(targetState) >= toolDestinationDepth(initialState)) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            }
            (
                (
                    slideIntoContainer(direction, tween(motion.medium, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(motion.short))
                    ) togetherWith
                    (
                        slideOutOfContainer(direction, tween(motion.medium, easing = FastOutSlowInEasing)) +
                            fadeOut(tween(motion.short))
                        )
                ).using(SizeTransform(clip = true) { _, _ -> snap() })
        },
        contentKey = { it }
    ) { destination ->
        when (destination) {
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

            ToolDestination.CONVERTER -> RadioConverterScreen(onBack = tools::back)

            ToolDestination.FEEDLINE -> FeedlineCalculatorScreen(onBack = tools::back)

            ToolDestination.FREQUENCY_PLAN -> FrequencyPlanScreen(onBack = tools::back)

            ToolDestination.CW_CODEC -> CwCodecScreen(onBack = tools::back)

            ToolDestination.PROPAGATION -> PropagationCalculatorScreen(onBack = tools::back)

            ToolDestination.ZONE_REFERENCE -> ZoneReferenceScreen(onBack = tools::back)

            ToolDestination.ANTENNA_LENGTH -> AntennaLengthCalculatorScreen(onBack = tools::back)

            ToolDestination.SWR_CALCULATOR -> SwrCalculatorScreen(onBack = tools::back)

            ToolDestination.REPEATER_PLANNER -> RepeaterPlannerScreen(onBack = tools::back)

            ToolDestination.APRS_CODEC -> AprsPacketCodecScreen(
                onBack = tools::back,
                config = controller.aprs.uiState.config
            )
        }
    }
}

private fun toolDestinationDepth(destination: ToolDestination): Int = when (destination) {
    ToolDestination.HOME -> 0
    ToolDestination.LOGBOOK_EDITOR -> 2
    else -> 1
}

@Composable
internal fun ToolsHome(approved: Boolean, error: String, onClearError: () -> Unit, onOpen: (ToolDestination) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (error.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ToolError(error, onClearError, Modifier.padding(bottom = 2.dp))
            }
        }
        TOOL_SECTIONS.forEach { section ->
            item(key = "header-${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            items(section.entries, key = ToolEntry::destination) { item ->
                val enabled = !item.requiresApproval || approved
                ToolTile(item = item, enabled = enabled) { onOpen(item.destination) }
            }
        }
    }
}

@Composable
private fun ToolTile(item: ToolEntry, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Text(
                item.label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!enabled) {
                Text("需审核", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private data class ToolEntry(
    val label: String,
    val icon: ImageVector,
    val destination: ToolDestination,
    val requiresApproval: Boolean = false
)

private data class ToolSection(val title: String, val entries: List<ToolEntry>)

private val TOOL_SECTIONS = listOf(
    ToolSection(
        "设备与记录",
        listOf(
            ToolEntry("蓝牙配置", Icons.AutoMirrored.Filled.BluetoothSearching, ToolDestination.BLE),
            ToolEntry("中继台查询", Icons.Default.SettingsInputAntenna, ToolDestination.RELAYS),
            ToolEntry("通联日志", Icons.AutoMirrored.Filled.MenuBook, ToolDestination.LOGBOOK, true)
        )
    ),
    ToolSection(
        "射频计算",
        listOf(
            ToolEntry("单位与信号", Icons.Default.MultipleStop, ToolDestination.CONVERTER),
            ToolEntry("馈线计算", Icons.Default.Cable, ToolDestination.FEEDLINE),
            ToolEntry("天线长度", Icons.Default.Radio, ToolDestination.ANTENNA_LENGTH),
            ToolEntry("SWR / 回波损耗", Icons.Default.NetworkCheck, ToolDestination.SWR_CALCULATOR),
            ToolEntry("传播与功率", Icons.Default.WifiTethering, ToolDestination.PROPAGATION),
        )
    ),
    ToolSection(
        "频率与中继",
        listOf(
            ToolEntry("频率划分", Icons.Default.TableChart, ToolDestination.FREQUENCY_PLAN),
            ToolEntry("中继频点规划", Icons.Default.SettingsInputAntenna, ToolDestination.REPEATER_PLANNER),
            ToolEntry("CW 编解码", Icons.AutoMirrored.Filled.ShowChart, ToolDestination.CW_CODEC),
        )
    ),
    ToolSection(
        "APRS 与定位",
        listOf(
            ToolEntry("APRS 报文编解码", Icons.Default.Map, ToolDestination.APRS_CODEC),
            ToolEntry("CQ / ITU 分区图", Icons.Default.Map, ToolDestination.ZONE_REFERENCE),
            ToolEntry("梅登海德网格", Icons.Default.GridOn, ToolDestination.MAIDENHEAD)
        )
    )
)
