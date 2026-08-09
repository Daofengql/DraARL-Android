package cn.silverdragon.draarl.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun DraarlIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    options: DraarlIconButtonOptions = DraarlIconButtonOptions()
) {
    DraarlTooltip(label, modifier = options.modifier) {
        IconButton(onClick = onClick, enabled = options.enabled) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = options.iconModifier,
                tint = options.tint ?: LocalContentColor.current
            )
        }
    }
}
