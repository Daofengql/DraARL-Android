package cn.silverdragon.draarl.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.components.DraarlScreenHeader
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import cn.silverdragon.draarl.ui.components.DraarlIconButtonOptions
import cn.silverdragon.draarl.ui.components.InlineNotice
import cn.silverdragon.draarl.ui.components.PageFeedback
import cn.silverdragon.draarl.ui.components.PageFeedbackKind
import cn.silverdragon.draarl.ui.components.StatusTone

@Composable
internal fun ToolHeader(
    title: String,
    onBack: () -> Unit,
    applyWindowInsets: Boolean = false,
    action: (@Composable () -> Unit)? = null
) {
    DraarlScreenHeader(
        title = title,
        onBack = onBack,
        applyWindowInsets = applyWindowInsets,
        action = action
    )
}

@Composable
internal fun ToolError(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    InlineNotice(
        text = message,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 6.dp),
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

internal fun copyToolText(context: Context, label: String, value: String) {
    if (value.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
internal fun ToolCopyButton(context: Context, label: String, value: String) {
    DraarlIconButton(
        icon = Icons.Default.ContentCopy,
        label = label,
        onClick = { copyToolText(context, label, value) },
        options = DraarlIconButtonOptions(enabled = value.isNotBlank())
    )
}
