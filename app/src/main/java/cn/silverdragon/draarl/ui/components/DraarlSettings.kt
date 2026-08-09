package cn.silverdragon.draarl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.theme.appColors
import cn.silverdragon.draarl.ui.theme.appDimensions

data class DraarlSettings(
    val icon: ImageVector,
    val title: String,
    val detail: String? = null,
    val onClick: (() -> Unit)? = null,
    val danger: Boolean = false,
    val showChevron: Boolean = onClick != null
)

@Composable
fun DraarlSettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.appColors.divider),
        tonalElevation = 0.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun DraarlSettingsRow(
    item: DraarlSettings,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showDivider: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val contentColor = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = if (item.onClick == null) {
        Modifier
    } else {
        Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = item.onClick)
            .semantics(mergeDescendants = true) {}
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = interaction.fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = if (item.danger) {
                    MaterialTheme.appColors.transmitContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = iconColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(MaterialTheme.appDimensions.icon)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing?.invoke(this)
            if (item.showChevron) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 58.dp),
                color = MaterialTheme.appColors.divider
            )
        }
    }
}

@Composable
fun DraarlSettingsSectionTitle(title: String, modifier: Modifier = Modifier, detail: String? = null) {
    SectionHeader(
        title = title,
        detail = detail,
        modifier = modifier.padding(start = 2.dp, bottom = 7.dp)
    )
}
