package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.RelayStation
import cn.silverdragon.draarl.tools.ToolsController
import cn.silverdragon.draarl.ui.components.RegionPicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RelaySearchScreen(tools: ToolsController, onBack: () -> Unit) {
    var location by remember(tools.relayLocation) { mutableStateOf(tools.relayLocation) }
    Column(Modifier.fillMaxSize()) {
        ToolHeader("中继台查询", onBack)
        if (tools.error.isNotBlank()) ToolError(tools.error, tools::clearError)
        RegionPicker(value = location, onValueChange = { location = it })
        Button(
            onClick = { tools.searchRelays(location) },
            enabled = !tools.relayBusy && location.split(' ').size >= 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (tools.relayBusy) CircularProgressIndicator(strokeWidth = 2.dp)
            else {
                Icon(Icons.Default.Search, contentDescription = null)
                Text("查询", modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (tools.relayCacheTime > 0L) {
            Text(
                "${tools.relayLocation} · ${formatCacheTime(tools.relayCacheTime)}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!tools.relayBusy && tools.relays.isEmpty() && tools.relayLocation.isNotBlank()) {
                item { Text("该地区暂无中继台", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(tools.relays.size, key = { tools.relays[it].id }) { index -> RelayCard(tools.relays[index]) }
        }
    }
}

@Composable
private fun RelayCard(relay: RelayStation) {
    Card(shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(relay.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (relay.status == 1) "可用" else "停用", color = if (relay.status == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Text("下行 ${relay.downlinkFrequency} MHz   上行 ${relay.uplinkFrequency} MHz")
            if (relay.transmitTone.isNotBlank() || relay.receiveTone.isNotBlank()) {
                Text("发射亚音 ${relay.transmitTone.ifBlank { "-" }}   接收亚音 ${relay.receiveTone.ifBlank { "-" }}")
            }
            Text(relay.location, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (relay.ownerCallsign.isNotBlank()) Text("维护 ${relay.ownerCallsign}", style = MaterialTheme.typography.bodySmall)
            if (relay.note.isNotBlank()) Text(relay.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatCacheTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
