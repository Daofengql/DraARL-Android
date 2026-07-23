package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.CommunicationRecord
import cn.silverdragon.draarl.ui.components.EmptyState

@Composable
fun RecordsScreen(controller: AppController) {
    if (controller.records.isEmpty() && !controller.contentLoading) {
        EmptyState(Icons.Default.History, "暂无通信记录", "当前账号还没有平台通信记录")
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(controller.records, key = CommunicationRecord::id) { record -> RecordItem(record) }
    }
}

@Composable
private fun RecordItem(record: CommunicationRecord) {
    Card(shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (record.messageType == 1) Icons.Default.Message else Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(record.deviceName.ifBlank { record.nickname.ifBlank { "未知台站" } }, fontWeight = FontWeight.SemiBold)
                Text(
                    if (record.messageType == 1) record.text else "语音 ${AppController.formatDuration(record.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                )
                Text(
                    listOf(record.groupName, record.startedAt).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
