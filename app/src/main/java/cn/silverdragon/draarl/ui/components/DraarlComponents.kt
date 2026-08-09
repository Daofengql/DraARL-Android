package cn.silverdragon.draarl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.theme.appColors
import cn.silverdragon.draarl.ui.theme.appDimensions
import cn.silverdragon.draarl.ui.theme.dataTypography

enum class StatusTone {
    NEUTRAL,
    CONNECTED,
    CONNECTING,
    RECEIVE,
    TRANSMIT,
    WARNING,
    ERROR
}

enum class CommandStyle {
    PRIMARY,
    SECONDARY,
    DANGER
}

@Composable
fun StatusIndicator(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val color = statusColor(tone)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CommandButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: CommandStyle = CommandStyle.SECONDARY,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null
) {
    val colors = commandColors(style, enabled)
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Button }.heightIn(
            min = if (supportingText == null) {
                MaterialTheme.appDimensions.controlHeight
            } else {
                MaterialTheme.appDimensions.largeControlHeight
            }
        ),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.content,
        border = colors.border?.let { BorderStroke(1.dp, it) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(MaterialTheme.appDimensions.icon))
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.Start) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                supportingText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.dataTypography.compact,
                        color = colors.secondaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailingIcon?.let {
                Spacer(Modifier.width(8.dp))
                Icon(it, contentDescription = null, modifier = Modifier.size(MaterialTheme.appDimensions.iconSmall))
            }
        }
    }
}

@Composable
fun CommandIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    danger: Boolean = false
) {
    val style = when {
        danger -> CommandStyle.DANGER
        selected -> CommandStyle.PRIMARY
        else -> CommandStyle.SECONDARY
    }
    val colors = commandColors(style, enabled)
    DraarlTooltip(contentDescription, modifier = modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier.semantics { role = Role.Button }.size(MaterialTheme.appDimensions.controlHeight),
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            color = colors.container,
            contentColor = colors.content,
            border = colors.border?.let { BorderStroke(1.dp, it) }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(MaterialTheme.appDimensions.iconLarge)
                )
            }
        }
    }
}

@Composable
fun DataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    technical: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.appDimensions.icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            modifier = Modifier.weight(0.8f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1.2f),
            style = if (technical) MaterialTheme.dataTypography.value else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.NEUTRAL,
    onDismiss: (() -> Unit)? = null
) {
    val colors = noticeColors(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.content,
        border = BorderStroke(1.dp, colors.border ?: MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.padding(top = 5.dp).size(7.dp).background(colors.content, CircleShape))
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            onDismiss?.let {
                DraarlIconButton(
                    icon = Icons.Default.Close,
                    label = "关闭提示",
                    onClick = it,
                    options = DraarlIconButtonOptions(
                        modifier = Modifier.size(30.dp),
                        iconModifier = Modifier.size(18.dp)
                    )
                )
            }
        }
    }
}

private data class ComponentColors(
    val container: Color,
    val content: Color,
    val secondaryContent: Color,
    val border: Color?
)

@Composable
private fun commandColors(style: CommandStyle, enabled: Boolean): ComponentColors {
    if (!enabled) {
        return ComponentColors(
            container = MaterialTheme.appColors.disabled,
            content = MaterialTheme.appColors.onDisabled,
            secondaryContent = MaterialTheme.appColors.onDisabled,
            border = null
        )
    }
    return when (style) {
        CommandStyle.PRIMARY -> ComponentColors(
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            secondaryContent = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            border = null
        )

        CommandStyle.SECONDARY -> ComponentColors(
            container = MaterialTheme.colorScheme.surfaceContainerLow,
            content = MaterialTheme.colorScheme.onSurface,
            secondaryContent = MaterialTheme.colorScheme.onSurfaceVariant,
            border = MaterialTheme.colorScheme.outlineVariant
        )

        CommandStyle.DANGER -> ComponentColors(
            container = MaterialTheme.appColors.transmitContainer,
            content = MaterialTheme.appColors.onTransmitContainer,
            secondaryContent = MaterialTheme.appColors.onTransmitContainer.copy(alpha = 0.8f),
            border = MaterialTheme.appColors.transmit.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun statusColor(tone: StatusTone): Color = when (tone) {
    StatusTone.NEUTRAL -> MaterialTheme.appColors.statusOffline
    StatusTone.CONNECTED -> MaterialTheme.appColors.statusConnected
    StatusTone.CONNECTING -> MaterialTheme.appColors.statusConnecting
    StatusTone.RECEIVE -> MaterialTheme.appColors.receive
    StatusTone.TRANSMIT -> MaterialTheme.appColors.transmit
    StatusTone.WARNING -> MaterialTheme.appColors.warning
    StatusTone.ERROR -> MaterialTheme.colorScheme.error
}

@Composable
private fun noticeColors(tone: StatusTone): ComponentColors = when (tone) {
    StatusTone.TRANSMIT,
    StatusTone.ERROR
    -> ComponentColors(
        container = MaterialTheme.appColors.transmitContainer,
        content = MaterialTheme.appColors.onTransmitContainer,
        secondaryContent = MaterialTheme.appColors.onTransmitContainer,
        border = MaterialTheme.appColors.transmit
    )

    StatusTone.RECEIVE,
    StatusTone.CONNECTED
    -> ComponentColors(
        container = MaterialTheme.appColors.receiveContainer,
        content = MaterialTheme.appColors.onReceiveContainer,
        secondaryContent = MaterialTheme.appColors.onReceiveContainer,
        border = MaterialTheme.appColors.receive
    )

    StatusTone.CONNECTING,
    StatusTone.WARNING
    -> ComponentColors(
        container = MaterialTheme.appColors.warningContainer,
        content = MaterialTheme.appColors.onWarningContainer,
        secondaryContent = MaterialTheme.appColors.onWarningContainer,
        border = MaterialTheme.appColors.warning
    )

    StatusTone.NEUTRAL -> ComponentColors(
        container = MaterialTheme.colorScheme.surfaceContainerLow,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        secondaryContent = MaterialTheme.colorScheme.onSurfaceVariant,
        border = MaterialTheme.colorScheme.outlineVariant
    )
}
