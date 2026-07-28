package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun RadioExtraPanel(
    locating: Boolean,
    onLocationClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(128.dp).padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    IconButton(onClick = onLocationClick, enabled = !locating) {
                        Icon(Icons.Default.LocationOn, contentDescription = "位置")
                    }
                }
                Text(
                    if (locating) "定位中" else "位置",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocationTypeSheet(
    locating: Boolean,
    onDismiss: () -> Unit,
    onCurrentLocation: () -> Unit,
    onPickLocation: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "发送位置",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        ListItem(
            headlineContent = { Text(if (locating) "正在获取当前位置" else "发送当前位置") },
            supportingContent = { Text("使用设备当前的 WGS-84 坐标") },
            leadingContent = { Icon(Icons.Default.MyLocation, contentDescription = null) },
            modifier = Modifier.clickable(enabled = !locating, onClick = onCurrentLocation),
        )
        ListItem(
            headlineContent = { Text("选择标点位置") },
            supportingContent = { Text("在地图上选择一个位置后发送") },
            leadingContent = { Icon(Icons.Default.Map, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onPickLocation),
        )
        Spacer(Modifier.height(20.dp))
    }
}
