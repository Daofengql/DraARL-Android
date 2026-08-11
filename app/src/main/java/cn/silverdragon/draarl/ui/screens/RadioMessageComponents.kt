package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.AppController
import cn.silverdragon.draarl.data.RadioMessage
import cn.silverdragon.draarl.data.RadioMessageType
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.data.VoicePlaybackQueue
import cn.silverdragon.draarl.data.Wgs84LocationMessage
import cn.silverdragon.draarl.data.decodeLocationMessage
import cn.silverdragon.draarl.data.formatRadioIdentity
import cn.silverdragon.draarl.ui.components.CommandIconButton
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import cn.silverdragon.draarl.ui.components.DraarlIconButtonOptions
import cn.silverdragon.draarl.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MessageItem(
    state: MessageItemState,
    playing: Boolean,
    onToggleVoicePlayback: (RadioMessage) -> Unit,
    onOpenLocation: (Wgs84LocationMessage) -> Unit
) {
    val message = state.message
    val profile = state.profile
    val callsign = message.senderCallsign.ifBlank { profile?.callsign.orEmpty() }.ifBlank { message.senderUsername }
    val nickname = message.senderNickname.ifBlank { profile?.nickname.orEmpty() }.ifBlank { message.senderUsername }
    val time = remember(message.timestamp) { formatTime(message.timestamp) }
    if (state.showTimeDivider) MessageTimeDivider(message.timestamp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (!message.mine) {
            UserAvatar(profile?.avatarUrl.orEmpty(), Modifier.size(38.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (message.groupId > 0) {
                    Text(
                        state.sourceGroupName.ifBlank { "频道 ${message.groupId}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (nickname.isNotBlank()) {
                    Text(
                        nickname,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    formatRadioIdentity(callsign, message.senderSsid),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            MessageBubble(
                message = message,
                playing = playing,
                onToggleVoicePlayback = onToggleVoicePlayback,
                onOpenLocation = onOpenLocation
            )
            Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (message.mine) {
            Spacer(Modifier.width(8.dp))
            UserAvatar(profile?.avatarUrl.orEmpty(), Modifier.size(38.dp))
        }
    }
}

@Immutable
internal data class MessageItemState(
    val message: RadioMessage,
    val profile: User?,
    val sourceGroupName: String,
    val showTimeDivider: Boolean
)

@Composable
private fun MessageBubble(
    message: RadioMessage,
    playing: Boolean,
    onToggleVoicePlayback: (RadioMessage) -> Unit,
    onOpenLocation: (Wgs84LocationMessage) -> Unit
) {
    val location = remember(message.content) { decodeLocationMessage(message.content) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(8.dp),
            color = if (message.mine) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (message.mine) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            if (message.type == RadioMessageType.VOICE) {
                VoiceMessageContent(
                    message = message,
                    playing = playing,
                    onTogglePlayback = { onToggleVoicePlayback(message) }
                )
            } else if (location != null) {
                LocationMessageContent(location, onOpenLocation)
            } else {
                Text(message.content, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
        }
        if (VoicePlaybackQueue.isUnplayed(message)) {
            Box(
                Modifier
                    .padding(start = 6.dp)
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .semantics { contentDescription = "未听语音" }
            )
        }
    }
}

@Composable
private fun MessageTimeDivider(timestamp: Long) {
    val timeDivider = remember(timestamp) { formatTimeDivider(timestamp) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                timeDivider,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MessageListFloatingActions(
    canScrollToBottom: Boolean,
    onScrollToBottom: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (canScrollToBottom) {
        Box(modifier = modifier) {
            CommandIconButton(
                onClick = onScrollToBottom,
                contentDescription = "滚动到最新记录",
                icon = Icons.Default.VerticalAlignBottom
            )
        }
    }
}

@Composable
internal fun UnreadVoiceJumpAction(
    unplayedCount: Int,
    showJump: Boolean,
    onClick: () -> Unit,
    onMarkAllPlayed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showJump) {
            CommandButton(
                label = "跳转未听 · $unplayedCount",
                onClick = onClick,
                leadingIcon = Icons.Default.ExpandLess,
                modifier = Modifier.widthIn(min = 132.dp, max = 168.dp)
            )
        }
        CommandIconButton(
            onClick = onMarkAllPlayed,
            contentDescription = "全部标为已读",
            icon = Icons.Default.DoneAll
        )
    }
}

@Composable
private fun LocationMessageContent(location: Wgs84LocationMessage, onOpen: (Wgs84LocationMessage) -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(min = 260.dp)
            .clickable { onOpen(location) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (location.kind == cn.silverdragon.draarl.data.LocationMessageKind.CURRENT) "当前位置" else "标点位置",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    String.format(Locale.US, "WGS-84  %.6f, %.6f", location.latitude, location.longitude),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    location.altitudeMeters?.let { String.format(Locale.US, "海拔 %.1f 米", it) } ?: "未提供海拔",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        AmapLocationPreview(
            location = location,
            onOpen = { onOpen(location) },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun VoiceMessageContent(message: RadioMessage, playing: Boolean, onTogglePlayback: () -> Unit) {
    val playable = VoicePlaybackQueue.isPlayable(message)
    val contentColor = LocalContentColor.current
    Row(
        modifier = Modifier.widthIn(min = 170.dp).padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DraarlIconButton(
            icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            label = if (playing) "暂停语音" else "播放语音",
            onClick = onTogglePlayback,
            options = DraarlIconButtonOptions(enabled = playable)
        )
        Row(
            modifier = Modifier.width(82.dp).height(24.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VOICE_BAR_HEIGHTS.forEach { height ->
                Box(
                    Modifier.width(3.dp).height(height.dp).background(
                        color = contentColor,
                        shape = RoundedCornerShape(2.dp)
                    )
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(AppController.formatDuration(message.durationMs), style = MaterialTheme.typography.labelMedium)
    }
}

private val TIME_FORMATTER = SimpleDateFormat("HH:mm", Locale.CHINA)
private val TIME_DIVIDER_FORMATTER = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.CHINA)

private fun formatTime(timestamp: Long): String = TIME_FORMATTER.format(Date(timestamp))

private fun formatTimeDivider(timestamp: Long): String = TIME_DIVIDER_FORMATTER.format(Date(timestamp))

internal const val RADIO_TIME_DIVIDER_MS = 10 * 60 * 1_000L
private val VOICE_BAR_HEIGHTS = listOf(7, 14, 20, 11, 18, 24, 13, 20, 9, 16, 22, 12)
