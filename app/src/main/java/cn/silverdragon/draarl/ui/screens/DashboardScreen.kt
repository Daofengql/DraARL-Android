package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.ui.components.CommunicationTrendChart
import cn.silverdragon.draarl.ui.components.StatusPill

@Composable
fun DashboardScreen(controller: AppController) {
    val currentUser = controller.user ?: return
    val stats = controller.dashboard
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${timeGreeting()}，${currentUser.displayName}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                StatusPill(
                    text = when (currentUser.approvalStatus) {
                        1 -> "账号已审核"
                        2 -> "审核未通过"
                        else -> "等待账号审核"
                    },
                    color = when (currentUser.approvalStatus) {
                        1 -> Color(0xFF087F5B)
                        2 -> MaterialTheme.colorScheme.error
                        else -> Color(0xFF9A6700)
                    },
                    modifier = Modifier.align(Alignment.Start),
                )
            }
        }
        if (!currentUser.isApproved) {
            item {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (currentUser.approvalStatus == 2) "账号审核未通过" else "账号正在等待审核",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            currentUser.reviewNote.ifBlank { "审核通过后可使用在线收发、设备、群组和通信记录。" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            StatRow(
                left = StatValue(
                    "设备在线 / 总数",
                    "${stats.onlineDevices} / ${stats.devices}",
                    Icons.Default.Devices,
                    Color(0xFF087F5B),
                ),
                right = StatValue("可用群组", stats.groups.toString(), Icons.Default.Groups, Color(0xFF4C5D95)),
            )
        }
        item {
            StatRow(
                left = StatValue("通信记录", stats.communications.toString(), Icons.Default.Forum, Color(0xFF9A6700)),
                right = StatValue(
                    "累计通信",
                    AppController.formatDuration(stats.communicationDurationMs),
                    Icons.Default.AccessTime,
                    Color(0xFF765B00),
                ),
            )
        }
        item {
            CommunicationTrendChart(stats.communicationTrend)
        }
    }
}

private data class StatValue(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
private fun StatRow(left: StatValue, right: StatValue) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(left, Modifier.weight(1f))
        StatCard(right, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(stat: StatValue, modifier: Modifier = Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(stat.icon, contentDescription = null, tint = stat.color)
            Text(stat.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(stat.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun timeGreeting(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "早上好"
    in 12..17 -> "下午好"
    else -> "晚上好"
}
