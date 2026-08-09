package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import cn.silverdragon.draarl.ui.components.InlineNotice
import cn.silverdragon.draarl.ui.components.PageFeedback
import cn.silverdragon.draarl.ui.components.PageFeedbackKind
import cn.silverdragon.draarl.ui.components.StatusTone

@Composable
internal fun ToolHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DraarlIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            label = "返回",
            onClick = onBack
        )
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

@Composable
internal fun ToolError(message: String, onDismiss: () -> Unit) {
    InlineNotice(
        text = message,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        tone = StatusTone.ERROR,
        onDismiss = onDismiss
    )
}

@Composable
internal fun ToolListFeedback(loading: Boolean, title: String, detail: String, modifier: Modifier = Modifier) {
    PageFeedback(
        kind = if (loading) PageFeedbackKind.LOADING else PageFeedbackKind.EMPTY,
        title = title,
        detail = detail,
        modifier = modifier
    )
}
