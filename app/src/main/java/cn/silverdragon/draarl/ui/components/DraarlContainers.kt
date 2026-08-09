package cn.silverdragon.draarl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.silverdragon.draarl.ui.theme.appColors

private const val STACKED_ACTION_FONT_SCALE = 1.75f

data class DraarlAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val style: CommandStyle = CommandStyle.SECONDARY
)

data class DraarlConfirmation(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val confirmStyle: CommandStyle = CommandStyle.PRIMARY
)

@Composable
fun DraarlConfirmationDialog(
    confirmation: DraarlConfirmation,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true
) {
    Dialog(onDismissRequest = onDismissRequest) {
        DraarlConfirmationContent(
            confirmation = confirmation,
            onDismiss = onDismissRequest,
            onConfirm = onConfirm,
            confirmEnabled = confirmEnabled
        )
    }
}

@Composable
internal fun DraarlConfirmationContent(
    confirmation: DraarlConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true
) {
    DraarlDialogContent(
        title = confirmation.title,
        dismissAction = DraarlAction("取消", onDismiss),
        confirmAction = DraarlAction(
            label = confirmation.confirmLabel,
            onClick = onConfirm,
            enabled = confirmEnabled,
            style = confirmation.confirmStyle
        )
    ) {
        Text(
            text = confirmation.message,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DraarlDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissAction: DraarlAction? = null,
    confirmAction: DraarlAction? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        DraarlDialogContent(
            title = title,
            modifier = modifier,
            dismissAction = dismissAction,
            confirmAction = confirmAction,
            content = content
        )
    }
}

@Composable
internal fun DraarlDialogContent(
    title: String,
    modifier: Modifier = Modifier,
    dismissAction: DraarlAction? = null,
    confirmAction: DraarlAction? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = 560.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.appColors.divider),
        shadowElevation = 12.dp
    ) {
        Column {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium
            )
            HorizontalDivider(color = MaterialTheme.appColors.divider)
            content()
            if (dismissAction != null || confirmAction != null) {
                HorizontalDivider(color = MaterialTheme.appColors.divider)
                DraarlActionRow(dismissAction, confirmAction)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraarlSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissAction: DraarlAction? = null,
    confirmAction: DraarlAction? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = { DraarlSheetHandle() }
    ) {
        DraarlSheetContent(
            title = title,
            dismissAction = dismissAction,
            confirmAction = confirmAction,
            content = content
        )
    }
}

@Composable
internal fun DraarlSheetContent(
    title: String,
    modifier: Modifier = Modifier,
    dismissAction: DraarlAction? = null,
    confirmAction: DraarlAction? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium
        )
        HorizontalDivider(color = MaterialTheme.appColors.divider)
        content()
        if (dismissAction != null || confirmAction != null) {
            HorizontalDivider(color = MaterialTheme.appColors.divider)
            DraarlActionRow(dismissAction, confirmAction)
        }
    }
}

@Composable
private fun DraarlActionRow(dismissAction: DraarlAction?, confirmAction: DraarlAction?) {
    if (dismissAction != null && confirmAction != null && LocalDensity.current.fontScale >= STACKED_ACTION_FONT_SCALE) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            DraarlActionButton(dismissAction, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            DraarlActionButton(confirmAction, Modifier.fillMaxWidth())
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        dismissAction?.let { action ->
            DraarlActionButton(
                action,
                if (confirmAction == null) Modifier.widthIn(min = 88.dp) else Modifier.weight(1f)
            )
        }
        if (dismissAction != null && confirmAction != null) {
            Box(Modifier.size(8.dp))
        }
        confirmAction?.let { action ->
            DraarlActionButton(
                action,
                if (dismissAction == null) Modifier.widthIn(min = 88.dp) else Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DraarlActionButton(action: DraarlAction, modifier: Modifier) {
    CommandButton(
        label = action.label,
        onClick = action.onClick,
        enabled = action.enabled,
        style = action.style,
        modifier = modifier
    )
}

@Composable
internal fun DraarlSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(vertical = 8.dp).size(width = 36.dp, height = 3.dp).background(
            color = MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(2.dp)
        )
    )
}
