package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.DashboardData
import cn.silverdragon.draarl.ui.components.CommunicationTrendChart
import cn.silverdragon.draarl.ui.theme.appColors

@Composable
internal fun ProfileOverview(stats: DashboardData) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("我的数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        StatRow(
            left = StatValue(
                "设备在线 / 总数",
                "${stats.onlineDevices} / ${stats.devices}",
                Icons.Default.Devices,
                MaterialTheme.appColors.statDevices,
            ),
            right = StatValue("可用群组", stats.groups.toString(), Icons.Default.Groups, MaterialTheme.appColors.statGroups),
        )
        StatRow(
            left = StatValue("通信记录", stats.communications.toString(), Icons.Default.Forum, MaterialTheme.appColors.statComms),
            right = StatValue(
                "累计通信",
                AppController.formatDuration(stats.communicationDurationMs),
                Icons.Default.AccessTime,
                MaterialTheme.appColors.statDuration,
            ),
        )
        CommunicationTrendChart(stats.communicationTrend)
    }
}

private data class StatValue(val label: String, val value: String, val icon: ImageVector, val color: Color)

@Composable
private fun StatRow(left: StatValue, right: StatValue) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(left, Modifier.weight(1f))
        StatCard(right, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(stat: StatValue, modifier: Modifier = Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(stat.icon, contentDescription = null, tint = stat.color)
            Text(stat.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(stat.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
