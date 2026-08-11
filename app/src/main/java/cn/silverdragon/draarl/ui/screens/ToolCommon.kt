package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.components.DraarlScreenHeader
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
internal fun ToolError(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
) {
    InlineNotice(
        text = message,
        modifier = modifier,
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
