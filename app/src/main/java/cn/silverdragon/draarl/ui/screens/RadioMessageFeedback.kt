package cn.silverdragon.draarl.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.silverdragon.draarl.ui.components.InlineNotice
import cn.silverdragon.draarl.ui.components.PageFeedback
import cn.silverdragon.draarl.ui.components.PageFeedbackKind
import cn.silverdragon.draarl.ui.components.StatusTone

@Composable
internal fun RadioMessageEmptyFeedback(hasSyncError: Boolean, modifier: Modifier = Modifier) {
    PageFeedback(
        kind = if (hasSyncError) PageFeedbackKind.ERROR else PageFeedbackKind.EMPTY,
        title = if (hasSyncError) "记录同步暂时中断" else "暂无通联记录",
        detail = if (hasSyncError) {
            "请检查网络连接，稍后将自动重试"
        } else {
            "连接后可在这里查看语音、文本和位置消息"
        },
        modifier = modifier,
        icon = if (hasSyncError) null else Icons.Default.History
    )
}

@Composable
internal fun RadioHistoryFeedback(loading: Boolean, hasSyncError: Boolean, modifier: Modifier = Modifier) {
    when {
        loading -> InlineNotice(
            text = "正在加载更早记录",
            modifier = modifier,
            tone = StatusTone.CONNECTING
        )

        hasSyncError -> InlineNotice(
            text = "记录同步暂时中断，稍后自动重试",
            modifier = modifier,
            tone = StatusTone.ERROR
        )
    }
}
