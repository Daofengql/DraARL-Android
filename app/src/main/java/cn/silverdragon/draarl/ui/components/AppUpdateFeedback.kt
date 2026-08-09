package cn.silverdragon.draarl.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.silverdragon.draarl.update.AppUpdateStatus

@Composable
fun AppUpdateFeedback(status: AppUpdateStatus, message: String, modifier: Modifier = Modifier) {
    val permissionHelp = if (status == AppUpdateStatus.INSTALL_PERMISSION_REQUIRED) {
        "请在系统页面允许 DraARL 安装未知应用，返回后会继续更新。"
    } else {
        ""
    }
    val text = listOf(message, permissionHelp).filter(String::isNotBlank).joinToString("\n")
    if (text.isBlank()) return

    InlineNotice(
        text = text,
        modifier = modifier,
        tone = when (status) {
            AppUpdateStatus.CHECKING,
            AppUpdateStatus.DOWNLOADING
            -> StatusTone.CONNECTING

            AppUpdateStatus.UP_TO_DATE,
            AppUpdateStatus.AVAILABLE,
            AppUpdateStatus.READY_TO_INSTALL
            -> StatusTone.CONNECTED

            AppUpdateStatus.INSTALL_PERMISSION_REQUIRED -> StatusTone.WARNING

            AppUpdateStatus.ERROR -> StatusTone.ERROR

            AppUpdateStatus.IDLE -> StatusTone.NEUTRAL
        }
    )
}
