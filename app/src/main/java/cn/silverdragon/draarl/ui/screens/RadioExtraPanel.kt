package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.components.CommandIconButton
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlSheet
import cn.silverdragon.draarl.ui.theme.appColors
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.appColors.divider)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Start,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    CommandIconButton(
                        onClick = onLocationClick,
                        contentDescription = "位置",
                        icon = Icons.Default.LocationOn,
                        enabled = !locating,
                    )
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
                    CommandIconButton(
                        onClick = if (cwTransmitting) onStopCw else onCwClick,
                        contentDescription = if (cwTransmitting) "停止 CW" else "CW 自动发送",
                        icon = if (cwTransmitting) Icons.Default.Stop else Icons.Default.GraphicEq,
                        enabled = cwEnabled || cwTransmitting,
                        danger = cwTransmitting,
                    )
                    Text(if (cwTransmitting) "停止 CW" else "CW", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

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
    DraarlSheet(
        title = "CW 自动发送",
        onDismissRequest = onDismiss,
        dismissAction = DraarlAction(
            label = if (previewing) "停止试听" else "试听",
            onClick = if (previewing) onStopPreview else onPreview,
            enabled = previewing || (previewEnabled && !transmitting && text.isNotBlank()),
        ),
        confirmAction = DraarlAction(
            label = if (transmitting) "停止 CW" else "发送 CW",
            onClick = if (transmitting) onStop else onSend,
            enabled = transmitting || (enabled && !previewing && text.isNotBlank()),
            style = if (transmitting) CommandStyle.DANGER else CommandStyle.PRIMARY,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
        }
    }
}

@Composable
internal fun LocationTypeSheet(
    locating: Boolean,
    onDismiss: () -> Unit,
    onCurrentLocation: () -> Unit,
    onPickLocation: () -> Unit,
) {
    DraarlSheet(
        title = "发送位置",
        onDismissRequest = onDismiss,
    ) {
        LocationChoiceRow(
            icon = Icons.Default.MyLocation,
            title = if (locating) "正在获取当前位置" else "发送当前位置",
            detail = "使用设备当前的 WGS-84 坐标",
            enabled = !locating,
            onClick = onCurrentLocation,
        )
        HorizontalDivider(color = MaterialTheme.appColors.divider)
        LocationChoiceRow(
            icon = Icons.Default.Map,
            title = "选择标点位置",
            detail = "在地图上选择一个位置后发送",
            onClick = onPickLocation,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LocationChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
