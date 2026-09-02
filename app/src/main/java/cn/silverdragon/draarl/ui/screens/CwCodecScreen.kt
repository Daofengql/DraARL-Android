package cn.silverdragon.draarl.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.radio.CwPreviewPlayer
import cn.silverdragon.draarl.radio.MorseCodec
import cn.silverdragon.draarl.ui.components.DraarlSegment
import cn.silverdragon.draarl.ui.components.DraarlSegmentedControl
import kotlin.math.roundToInt

@Composable
@Suppress("LongMethod", "CognitiveComplexMethod")
internal fun CwCodecScreen(onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf("encode") }
    var plainText by rememberSaveable { mutableStateOf("CQ CQ DE") }
    var morseText by rememberSaveable { mutableStateOf("-.-. --.- / -.-. --.-") }
    var wordsPerMinute by rememberSaveable { mutableIntStateOf(18) }
    var toneHz by rememberSaveable { mutableIntStateOf(700) }
    var previewing by remember { mutableStateOf(false) }
    val player = remember { CwPreviewPlayer() }
    val encoded = MorseCodec.encode(plainText)
    val decoded = MorseCodec.decode(morseText)
    val previewText = if (mode == "encode") plainText else decoded

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Column(Modifier.fillMaxSize()) {
        ToolHeader("CW 编解码", onBack)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DraarlSegmentedControl(
                    segments = listOf(DraarlSegment("encode", "文本转 CW"), DraarlSegment("decode", "CW 转文本")),
                    selectedKey = mode,
                    onSelect = { mode = it }
                )
            }
            item {
                if (mode == "encode") {
                    OutlinedTextField(
                        value = plainText,
                        onValueChange = { plainText = it.uppercase().take(MAX_CW_TEXT_LENGTH) },
                        label = { Text("文本") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = morseText,
                        onValueChange = { morseText = it.filter { character -> character in ".-/| \n\t" } },
                        label = { Text("点划码") },
                        placeholder = { Text("... --- ... / -.-. --.-") },
                        supportingText = { Text("空格分隔字符，斜杠分隔单词") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (mode == "encode") encoded.ifBlank { "-" } else decoded.ifBlank { "-" },
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("速度")
                        Text("$wordsPerMinute WPM", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = wordsPerMinute.toFloat(),
                        onValueChange = { wordsPerMinute = it.roundToInt() },
                        valueRange = 8f..40f,
                        steps = 31
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("音调")
                        Text("$toneHz Hz", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = toneHz.toFloat(),
                        onValueChange = { toneHz = (it / 50f).roundToInt() * 50 },
                        valueRange = 400f..1_000f,
                        steps = 11
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        if (previewing) {
                            player.stop()
                            previewing = false
                        } else {
                            previewing = player.play(previewText, wordsPerMinute, toneHz) { previewing = false }
                        }
                    },
                    enabled = previewing || previewText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (previewing) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Text(if (previewing) "停止试听" else "试听 CW")
                }
            }
        }
    }
}

private const val MAX_CW_TEXT_LENGTH = 80
