package cn.silverdragon.draarl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.theme.appColors

enum class PageFeedbackKind {
    EMPTY,
    LOADING,
    ERROR,
    PERMISSION
}

@Composable
fun PageFeedback(
    kind: PageFeedbackKind,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val accent = when (kind) {
        PageFeedbackKind.EMPTY -> MaterialTheme.colorScheme.outline
        PageFeedbackKind.LOADING -> MaterialTheme.colorScheme.primary
        PageFeedbackKind.ERROR -> MaterialTheme.colorScheme.error
        PageFeedbackKind.PERMISSION -> MaterialTheme.appColors.warning
    }
    val stateIcon = icon ?: when (kind) {
        PageFeedbackKind.EMPTY -> null
        PageFeedbackKind.LOADING -> Icons.Outlined.Sync
        PageFeedbackKind.ERROR -> Icons.Outlined.ErrorOutline
        PageFeedbackKind.PERMISSION -> Icons.Outlined.Lock
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        if (stateIcon != null) {
            Icon(stateIcon, contentDescription = null, modifier = Modifier.size(30.dp), tint = accent)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
