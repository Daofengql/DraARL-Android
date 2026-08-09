package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.radio.session.RadioSessionUiState
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlDialog
import cn.silverdragon.draarl.ui.state.availableRadioGroups
import cn.silverdragon.draarl.ui.theme.appColors

@Composable
internal fun AccessPointDialog(state: RadioSessionUiState, onSelect: (AccessPoint) -> Unit, onDismiss: () -> Unit) {
    val probesByAccessPointId = remember(state.accessPointProbes) {
        state.accessPointProbes.associateBy { it.accessPoint.id }
    }
    DraarlDialog(
        title = "选择边缘节点",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("关闭", onDismiss)
    ) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(state.accessPoints, key = AccessPoint::id) { point ->
                val selected = point.id == state.selectedAccessPoint?.id
                val probe = probesByAccessPointId[point.id]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable {
                            if (!selected) onSelect(point)
                            onDismiss()
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(point.displayName, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        val meta = listOf(point.region, point.network).filter(String::isNotBlank).joinToString(" · ")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            LatencyText(probe?.latencyMs, prefix = "ICMP ")
                        }
                    }
                    if (selected) Icon(Icons.Default.Check, contentDescription = "当前节点")
                }
            }
        }
    }
}

@Composable
internal fun GroupDialog(groups: List<Group>, selectedGroupId: Int, onSelect: (Group) -> Unit, onDismiss: () -> Unit) {
    val availableGroups = remember(groups) { availableRadioGroups(groups) }
    DraarlDialog(
        title = "发送/日志频道",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("关闭", onDismiss)
    ) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(availableGroups, key = Group::id) { group ->
                val selected = group.id == selectedGroupId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable {
                            if (!selected) onSelect(group)
                            onDismiss()
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(group.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        Text(
                            listOf(
                                "${group.onlineCount} 在线",
                                if (group.isPrivate) "私有群组" else "公开群组"
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selected) Icon(Icons.Default.Check, contentDescription = "当前群组")
                }
            }
        }
    }
}

@Composable
internal fun RoutingDialog(
    groups: List<Group>,
    state: RadioSessionUiState,
    onApply: (Int, Collection<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val availableGroups = remember(groups) { availableRadioGroups(groups) }
    val primaryGroupId = state.selectedGroupId
    var rxGroupIds by remember(state.status.sessionId, primaryGroupId) {
        mutableStateOf(state.receiveGroupIds + primaryGroupId)
    }
    DraarlDialog(
        title = "收听频道",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction("取消", onDismiss),
        confirmAction = DraarlAction(
            label = "应用",
            onClick = {
                onApply(primaryGroupId, rxGroupIds)
                onDismiss()
            },
            enabled = state.status.connected && !state.routingUpdating && primaryGroupId > 0,
            style = CommandStyle.PRIMARY
        )
    ) {
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(availableGroups, key = Group::id) { group ->
                val transmitting = group.id == primaryGroupId
                val receiving = group.id in rxGroupIds
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = receiving,
                        enabled = !transmitting,
                        onCheckedChange = { checked ->
                            rxGroupIds = if (checked) rxGroupIds + group.id else rxGroupIds - group.id
                        }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(group.name, fontWeight = if (transmitting) FontWeight.SemiBold else FontWeight.Normal)
                        Text(
                            when {
                                transmitting -> "发送/日志频道，必须收听"
                                receiving -> "收听"
                                else -> "未订阅"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LatencyText(latencyMs: Int?, modifier: Modifier = Modifier, prefix: String = "") {
    Text(
        text = latencyMs?.let { "$prefix$it ms" } ?: "${prefix}不可达",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            latencyMs == null -> MaterialTheme.colorScheme.onSurfaceVariant
            latencyMs <= 80 -> MaterialTheme.appColors.latencyGood
            latencyMs <= 180 -> MaterialTheme.appColors.latencyWarn
            else -> MaterialTheme.appColors.latencyPoor
        },
        maxLines = 1
    )
}
