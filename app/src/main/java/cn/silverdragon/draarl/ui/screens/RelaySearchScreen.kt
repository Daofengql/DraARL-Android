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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.tools.RelayStation
import cn.silverdragon.draarl.tools.ToolsController
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.RegionPicker
import cn.silverdragon.draarl.ui.components.StatusIndicator
import cn.silverdragon.draarl.ui.components.StatusTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RelaySearchScreen(tools: ToolsController, onBack: () -> Unit) {
    var location by remember(tools.relayLocation) { mutableStateOf(tools.relayLocation) }
    RelaySearchContent(
        state = RelaySearchContentState(
            location = location,
            error = tools.error,
            busy = tools.relayBusy,
            queriedLocation = tools.relayLocation,
            cacheTime = tools.relayCacheTime,
            relays = tools.relays
        ),
        onLocationChange = { location = it },
        onSearch = { tools.searchRelays(location) },
        onClearError = tools::clearError,
        onBack = onBack
    )
}

@Immutable
internal data class RelaySearchContentState(
    val location: String,
    val error: String = "",
    val busy: Boolean = false,
    val queriedLocation: String = "",
    val cacheTime: Long = 0L,
    val relays: List<RelayStation> = emptyList()
)

@Composable
internal fun RelaySearchContent(
    state: RelaySearchContentState,
    onLocationChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ToolHeader("中继台查询", onBack)
        if (state.error.isNotBlank()) ToolError(state.error, onClearError)
        RelaySearchControls(state, onLocationChange, onSearch)
        RelayResults(state)
    }
}

@Composable
private fun RelaySearchControls(
    state: RelaySearchContentState,
    onLocationChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Column {
        RegionPicker(value = state.location, onValueChange = onLocationChange)
        CommandButton(
            label = if (state.busy) "正在查询" else "查询中继台",
            onClick = onSearch,
            enabled = !state.busy && state.location.split(' ').size >= 2,
            leadingIcon = Icons.Default.Search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )
        if (state.cacheTime > 0L) {
            Text(
                "${state.queriedLocation} · ${formatCacheTime(state.cacheTime)}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RelayResults(state: RelaySearchContentState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.relays.isEmpty() && (state.busy || state.queriedLocation.isNotBlank())) {
            item {
                ToolListFeedback(
                    loading = state.busy,
                    title = if (state.busy) "正在查询中继台" else "该地区暂无中继台",
                    detail = if (state.busy) {
                        "正在读取当前地区的公开中继资料"
                    } else {
                        "可切换省市后重新查询"
                    }
                )
            }
        }
        items(state.relays.size, key = { state.relays[it].id }) { index ->
            RelayCard(state.relays[index])
        }
    }
}

@Composable
private fun RelayCard(relay: RelayStation) {
    Card(shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(relay.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusIndicator(
                    text = if (relay.status == 1) "可用" else "停用",
                    tone = if (relay.status == 1) StatusTone.CONNECTED else StatusTone.ERROR
                )
            }
            Text("下行 ${relay.downlinkFrequency} MHz   上行 ${relay.uplinkFrequency} MHz")
            if (relay.transmitTone.isNotBlank() || relay.receiveTone.isNotBlank()) {
                Text("发射亚音 ${relay.transmitTone.ifBlank { "-" }}   接收亚音 ${relay.receiveTone.ifBlank { "-" }}")
            }
            Text(relay.location, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (relay.ownerCallsign.isNotBlank()) {
                Text(
                    "维护 ${relay.ownerCallsign}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (relay.note.isNotBlank()) Text(relay.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatCacheTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
