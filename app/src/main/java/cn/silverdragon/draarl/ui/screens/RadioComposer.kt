package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.components.CommandIconButton
import cn.silverdragon.draarl.ui.theme.appColors

@Composable
internal fun RadioComposer(
    textMode: Boolean,
    text: String,
    connected: Boolean,
    transmitting: Boolean,
    receiving: Boolean,
    canSendText: Boolean,
    onTextModeChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onTextInputFocused: () -> Unit,
    onSendText: () -> Unit,
    onMoreMessage: () -> Unit,
    onStartPtt: () -> Boolean,
    onStopPtt: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sendEnabled = canSendText && text.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.appColors.divider)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommandIconButton(
                    onClick = {
                        if (textMode) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                        onTextModeChange(!textMode)
                    },
                    contentDescription = if (textMode) "切换到语音" else "切换到文本",
                    icon = if (textMode) Icons.Default.Mic else Icons.Default.Keyboard,
                    selected = textMode,
                )

                Spacer(Modifier.size(8.dp))
                if (textMode) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f).onFocusChanged {
                            if (it.isFocused) onTextInputFocused()
                        },
                        placeholder = { Text("发送文本消息") },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        enabled = connected,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (sendEnabled) onSendText()
                        }),
                    )
                } else {
                    PttButton(
                        modifier = Modifier.weight(1f),
                        transmitting = transmitting,
                        enabled = connected && !receiving,
                        onStart = onStartPtt,
                        onStop = onStopPtt,
                    )
                }
                Spacer(Modifier.size(8.dp))

                if (textMode && text.isNotBlank()) {
                    CommandIconButton(
                        onClick = onSendText,
                        enabled = sendEnabled,
                        contentDescription = "发送",
                        icon = Icons.AutoMirrored.Filled.Send,
                        selected = sendEnabled,
                    )
                } else {
                    CommandIconButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onMoreMessage()
                        },
                        contentDescription = "更多消息类型",
                        icon = Icons.Default.Add,
                    )
                }
            }
        }
    }
}
