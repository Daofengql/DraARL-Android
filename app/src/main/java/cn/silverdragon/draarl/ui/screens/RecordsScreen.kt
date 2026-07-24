package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.CommunicationRecord
import cn.silverdragon.draarl.data.formatRadioIdentity
import cn.silverdragon.draarl.ui.components.EmptyState
import cn.silverdragon.draarl.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RecordsScreen(controller: AppController) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = controller::goBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            IconButton(onClick = controller::refreshRecords) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }
        if (controller.records.isEmpty() && !controller.contentLoading) {
            EmptyState(
                Icons.Default.History,
                "暂无通信记录",
                "当前账号还没有平台通信记录",
                Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(controller.records, key = { _, record -> record.id }) { index, record ->
                    val previous = controller.records.getOrNull(index - 1)
                    val showDivider = previous == null || kotlin.math.abs(
                        parseRecordTime(record.startedAt) - parseRecordTime(previous.startedAt),
                    ) >= RECORD_TIME_DIVIDER_MS
                    RecordItem(controller, record, showDivider)
                }
            }
        }
    }
}

@Composable
private fun RecordItem(controller: AppController, record: CommunicationRecord, showTimeDivider: Boolean) {
    val ssid = if (record.model in 100..105) record.model else record.deviceName.substringAfterLast('-').toIntOrNull() ?: 0
    val callsign = record.deviceName.removeSuffix(if (ssid > 0) "-$ssid" else "").ifBlank { record.username }
    val profile = controller.publicProfile(record.username)
    if (showTimeDivider) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    record.startedAt,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
    Card(shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            UserAvatar(profile?.avatarUrl.orEmpty(), Modifier.size(42.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.nickname.ifBlank { profile?.nickname.orEmpty() }.ifBlank { record.username },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(formatRadioIdentity(callsign, ssid), fontWeight = FontWeight.SemiBold)
                }
                if (record.messageType == 1) {
                    Text(record.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { controller.toggleRecordPlayback(record) },
                            enabled = record.audioUrl.isNotBlank(),
                        ) {
                            Icon(
                                if (controller.playingMessageId == "record-${record.id}") Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "播放语音",
                            )
                        }
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(AppController.formatDuration(record.durationMs))
                    }
                }
                Text(
                    listOf(record.groupName, record.startedAt).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun parseRecordTime(value: String): Long = runCatching {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).parse(value)?.time
}.getOrNull() ?: 0L

private const val RECORD_TIME_DIVIDER_MS = 10 * 60 * 1_000L
