package cn.silverdragon.draarl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.silverdragon.draarl.ui.theme.appColors

data class DraarlAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val style: CommandStyle = CommandStyle.SECONDARY,
)

@Composable
fun DraarlDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissAction: DraarlAction? = null,
    confirmAction: DraarlAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        DraarlDialogContent(
            title = title,
            modifier = modifier,
            dismissAction = dismissAction,
            confirmAction = confirmAction,
            content = content,
        )
    }
}

@Composable
internal fun DraarlDialogContent(
    title: String,
    modifier: Modifier = Modifier,
    dismissAction: DraarlAction? = null,
    confirmAction: DraarlAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = 560.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.appColors.divider),
        shadowElevation = 12.dp,
    ) {
        Column {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
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
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = { DraarlSheetHandle() },
    ) {
        DraarlSheetContent(
            title = title,
            dismissAction = dismissAction,
            confirmAction = confirmAction,
            content = content,
        )
    }
}

@Composable
internal fun DraarlSheetContent(
    title: String,
    modifier: Modifier = Modifier,
    dismissAction: DraarlAction? = null,
    confirmAction: DraarlAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dismissAction?.let { action ->
            CommandButton(
                label = action.label,
                onClick = action.onClick,
                enabled = action.enabled,
                style = action.style,
                modifier = Modifier.widthIn(min = 88.dp),
            )
        }
        if (dismissAction != null && confirmAction != null) {
            Box(Modifier.size(8.dp))
        }
        confirmAction?.let { action ->
            CommandButton(
                label = action.label,
                onClick = action.onClick,
                enabled = action.enabled,
                style = action.style,
                modifier = Modifier.widthIn(min = 88.dp),
            )
        }
    }
}

@Composable
private fun DraarlSheetHandle() {
    Box(
        modifier = Modifier.padding(vertical = 8.dp).size(width = 36.dp, height = 3.dp).background(
            color = MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(2.dp),
        ),
    )
}
