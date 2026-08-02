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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun RadioExtraPanel(
    locating: Boolean,
    cwEnabled: Boolean,
    cwTransmitting: Boolean,
    onLocationClick: () -> Unit,
    onCwClick: () -> Unit,
    onStopCw: () -> Unit,
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
            Spacer(Modifier.size(18.dp))
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
                    IconButton(
                        onClick = if (cwTransmitting) onStopCw else onCwClick,
                        enabled = cwEnabled || cwTransmitting,
                    ) {
                        Icon(
                            if (cwTransmitting) Icons.Default.Stop else Icons.Default.GraphicEq,
                            contentDescription = if (cwTransmitting) "停止 CW" else "CW 自动发送",
                        )
                    }
                }
                Text(if (cwTransmitting) "停止 CW" else "CW", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CwSendSheet(
    text: String,
    wordsPerMinute: Int,
    toneHz: Int,
    enabled: Boolean,
    previewEnabled: Boolean,
    transmitting: Boolean,
    previewing: Boolean,
    onDismiss: () -> Unit,
    onTextChange: (String) -> Unit,
    onWordsPerMinuteChange: (Int) -> Unit,
    onToneHzChange: (Int) -> Unit,
    onPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("CW 自动发送", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("内容") },
                placeholder = { Text("CQ CQ DE ...") },
                minLines = 2,
                maxLines = 3,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("速度", style = MaterialTheme.typography.bodyMedium)
                Text("$wordsPerMinute WPM", style = MaterialTheme.typography.labelLarge)
            }
            Slider(
                value = wordsPerMinute.toFloat(),
                onValueChange = { onWordsPerMinuteChange(it.roundToInt()) },
                valueRange = 8f..40f,
                steps = 31,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("音调", style = MaterialTheme.typography.bodyMedium)
                Text("$toneHz Hz", style = MaterialTheme.typography.labelLarge)
            }
            Slider(
                value = toneHz.toFloat(),
                onValueChange = { onToneHzChange((it / 50f).roundToInt() * 50) },
                valueRange = 400f..1_000f,
                steps = 11,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = if (previewing) onStopPreview else onPreview,
                    enabled = previewing || (previewEnabled && !transmitting && text.isNotBlank()),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(if (previewing) Icons.Default.Stop else Icons.Default.GraphicEq, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (previewing) "停止试听" else "试听")
                }
                Button(
                    onClick = if (transmitting) onStop else onSend,
                    enabled = transmitting || (enabled && !previewing && text.isNotBlank()),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(if (transmitting) Icons.Default.Stop else Icons.Default.GraphicEq, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (transmitting) "停止 CW" else "发送 CW")
                }
            }
            Spacer(Modifier.height(20.dp))
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
