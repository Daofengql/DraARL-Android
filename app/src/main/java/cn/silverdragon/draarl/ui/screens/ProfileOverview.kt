package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.data.DashboardData
import cn.silverdragon.draarl.ui.components.CommunicationTrendChart
import cn.silverdragon.draarl.ui.theme.appColors

@Composable
internal fun ProfileOverview(stats: DashboardData) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("动态概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        StatRow(
            left = StatValue(
                "设备在线 / 总数",
                "${stats.onlineDevices} / ${stats.devices}",
                Icons.Default.Devices,
                MaterialTheme.appColors.statDevices,
            ),
            right = StatValue(
                "累计时长 · ${stats.communications} 条记录",
                formatCompactDuration(stats.communicationDurationMs),
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
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(left, Modifier.weight(1f).fillMaxHeight())
        StatCard(right, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun StatCard(stat: StatValue, modifier: Modifier = Modifier) {
    Card(modifier.heightIn(min = 124.dp), shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(stat.icon, contentDescription = null, tint = stat.color)
            Text(
                stat.value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stat.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun formatCompactDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000
    val totalHours = totalSeconds / 3_600
    val days = totalHours / 24
    val hours = totalHours % 24
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> if (hours > 0) "${days}天 ${hours}时" else "${days}天"
        totalHours > 0 -> if (minutes > 0) "${totalHours}时 ${minutes}分" else "${totalHours}时"
        minutes > 0 -> "${minutes}分"
        else -> "${seconds}秒"
    }
}
