package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.deviceModelName
import cn.silverdragon.draarl.ui.components.EmptyState
import cn.silverdragon.draarl.ui.components.StatusPill

@Composable
fun DevicesScreen(controller: AppController) {
    if (controller.devices.isEmpty() && !controller.contentLoading) {
        EmptyState(Icons.Default.Devices, "暂无设备", "设备首次通过 UDP 接入后会显示在这里")
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(controller.devices, key = Device::id) { device ->
            DeviceItem(device, controller.groups.firstOrNull { it.id == device.groupId }?.name.orEmpty())
        }
    }
}

@Composable
private fun DeviceItem(device: Device, groupName: String) {
    Card(shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.name.ifBlank { "${device.callsign}-${device.ssid}" }, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${deviceModelName(device.model)} · SSID ${device.ssid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    if (device.online) "在线" else "离线",
                    if (device.online) Color(0xFF087F5B) else MaterialTheme.colorScheme.outline,
                )
            }
            HorizontalDivider()
            Text(
                "群组：${groupName.ifBlank { if (device.groupId == 0) "未分组" else device.groupId.toString() }}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (device.qth.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    Text(" ${device.qth}", style = MaterialTheme.typography.bodySmall)
                }
            }
            val restrictions = buildList {
                if (device.disableSend) add("禁止发送")
                if (device.disableReceive) add("禁止接收")
                if (!device.enabled) add("已停用")
            }
            if (restrictions.isNotEmpty()) {
                Text(restrictions.joinToString(" · "), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (device.entryName.isNotBlank() && device.online) {
                Text("入口：${device.entryName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
