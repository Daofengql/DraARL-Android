package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerAction(
                onClick = {
                    if (textMode) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                    onTextModeChange(!textMode)
                },
                contentDescription = if (textMode) "切换到语音" else "切换到文本",
            ) {
                Icon(
                    if (textMode) Icons.Default.Mic else Icons.Default.Keyboard,
                    contentDescription = null,
                )
            }

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
                ComposerAction(
                    onClick = onSendText,
                    enabled = sendEnabled,
                    primary = sendEnabled,
                    contentDescription = "发送",
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                }
            } else {
                ComposerAction(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onMoreMessage()
                    },
                    contentDescription = "更多消息类型",
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun ComposerAction(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    primary: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.size(COMPOSER_ACTION_SIZE),
        shape = CircleShape,
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    }
}

private val COMPOSER_ACTION_SIZE = 48.dp
