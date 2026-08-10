package cn.silverdragon.draarl.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.theme.appColors
import cn.silverdragon.draarl.ui.theme.appMotion
import cn.silverdragon.draarl.ui.theme.dataTypography
import kotlin.math.ceil

@Immutable
data class RadioStatusStripState(
    val stationIdentity: String,
    val radioIdentifiers: String,
    val connectionText: String,
    val connectionTone: StatusTone,
    val nodeSelectionEnabled: Boolean,
    val onlineCount: Int,
    val receiving: Boolean,
    val transmitting: Boolean,
    val denoiseEnabled: Boolean,
    val muted: Boolean,
    val sendChannel: String,
    val sendChannelEnabled: Boolean,
    val receiveChannelCount: Int,
    val receiveChannelsEnabled: Boolean,
    val speaker: String,
    val error: String
)

@Composable
fun RadioStatusStrip(
    state: RadioStatusStripState,
    avatar: @Composable () -> Unit,
    audioLevel: @Composable (Modifier) -> Unit,
    onSelectNode: () -> Unit,
    onShowOnlineDevices: () -> Unit,
    onToggleDenoise: () -> Unit,
    onToggleMuted: () -> Unit,
    onSelectSendChannel: () -> Unit,
    onSelectReceiveChannels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { avatar() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = state.stationIdentity,
                        style = MaterialTheme.dataTypography.identity,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.radioIdentifiers.isNotBlank()) {
                        Text(
                            text = state.radioIdentifiers,
                            style = MaterialTheme.dataTypography.compact,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.clickable(
                            enabled = state.nodeSelectionEnabled,
                            onClick = onSelectNode
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusIndicator(
                            text = state.connectionText,
                            tone = state.connectionTone,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "选择边缘节点",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CommandIconButton(
                        onClick = onToggleDenoise,
                        contentDescription = if (state.denoiseEnabled) "关闭神经网络降噪" else "开启神经网络降噪",
                        icon = Icons.Default.GraphicEq,
                        selected = state.denoiseEnabled
                    )
                    CommandIconButton(
                        onClick = onToggleMuted,
                        contentDescription = if (state.muted) "开启接收音频" else "关闭接收音频",
                        icon = if (state.muted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        danger = state.muted
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicator(
                    text = "${state.onlineCount} 在线",
                    tone = if (state.onlineCount > 0) StatusTone.CONNECTED else StatusTone.NEUTRAL,
                    modifier = Modifier.clickable(onClick = onShowOnlineDevices).padding(vertical = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        state.transmitting -> "TX"
                        state.receiving -> "RX"
                        else -> "RX / TX"
                    },
                    style = MaterialTheme.dataTypography.compact,
                    color = when {
                        state.transmitting -> MaterialTheme.appColors.transmit
                        state.receiving -> MaterialTheme.appColors.receive
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(8.dp))
                audioLevel(Modifier.requiredWidth(120.dp).height(20.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommandButton(
                    label = "发送/日志",
                    supportingText = state.sendChannel,
                    leadingIcon = Icons.Default.Mic,
                    trailingIcon = Icons.Default.KeyboardArrowDown,
                    onClick = onSelectSendChannel,
                    modifier = Modifier.weight(1f),
                    enabled = state.sendChannelEnabled
                )
                CommandButton(
                    label = "收听频道",
                    supportingText = "${state.receiveChannelCount} 个频道",
                    leadingIcon = Icons.Default.Headphones,
                    trailingIcon = Icons.Default.KeyboardArrowDown,
                    onClick = onSelectReceiveChannels,
                    modifier = Modifier.weight(1f),
                    enabled = state.receiveChannelsEnabled
                )
            }
            if (state.speaker.isNotBlank()) {
                InlineNotice(
                    text = "${state.speaker} 正在发言",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    tone = StatusTone.RECEIVE
                )
            }
            if (state.error.isNotBlank()) {
                InlineNotice(
                    text = state.error,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    tone = StatusTone.ERROR
                )
            }
            HorizontalDivider(color = MaterialTheme.appColors.divider)
        }
    }
}

@Composable
internal fun RadioAudioLevelMeter(
    receiveLevel: Float,
    transmitLevel: Float,
    receiving: Boolean,
    transmitting: Boolean,
    modifier: Modifier = Modifier
) {
    val active = transmitting || receiving
    val level = if (transmitting) transmitLevel else receiveLevel
    val targetLevel = if (active) level.coerceIn(0f, 1f) else 0f
    val animatedLevel = animateFloatAsState(
        targetValue = targetLevel,
        animationSpec = tween(durationMillis = MaterialTheme.appMotion.quick),
        label = "audioLevel"
    )
    val primary = if (transmitting) MaterialTheme.appColors.transmit else MaterialTheme.appColors.receive
    val warning = MaterialTheme.appColors.warning
    val error = MaterialTheme.appColors.transmit
    val inactive = MaterialTheme.colorScheme.outlineVariant
    val currentLevel = animatedLevel.value
    val selectedSegments = if (active) ceil(currentLevel * SEGMENT_COUNT).toInt() else 0
    Row(
        modifier = modifier
            .requiredWidth(120.dp)
            .height(20.dp)
            .semantics {
                contentDescription = when {
                    transmitting -> "发送电平 ${(targetLevel * 100).toInt()}%"
                    receiving -> "接收电平 ${(targetLevel * 100).toInt()}%"
                    else -> "当前没有收发音频"
                }
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(SEGMENT_COUNT) { index ->
            val progress = (index + 1f) / SEGMENT_COUNT
            val selected = index < selectedSegments
            val color = if (!selected) {
                inactive
            } else {
                when {
                    progress > 0.84f -> error
                    progress > 0.66f -> warning
                    else -> primary
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

private const val SEGMENT_COUNT = 12
