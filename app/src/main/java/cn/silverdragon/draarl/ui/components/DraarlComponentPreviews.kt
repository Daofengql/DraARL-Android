package cn.silverdragon.draarl.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.theme.DraarlTheme

private val PreviewNavigationItems = listOf(
    DraarlBottomBarItem("devices", "设备", Icons.Default.Devices),
    DraarlBottomBarItem("groups", "群组", Icons.Default.Groups),
    DraarlBottomBarItem("radio", "PTT", Icons.Default.Mic, prominent = true),
    DraarlBottomBarItem("tools", "工具", Icons.Default.Build),
    DraarlBottomBarItem("profile", "我的", Icons.Default.Person),
)

private val PreviewRadioStatus = RadioStatusStripState(
    stationIdentity = "BH1ABC-7",
    radioIdentifiers = "MDC 1234 / DMR 4600123",
    connectionText = "UDP 已连接 · 华东低延迟入口",
    connectionTone = StatusTone.CONNECTED,
    nodeSelectionEnabled = true,
    onlineCount = 18,
    receiving = true,
    transmitting = false,
    denoiseEnabled = true,
    muted = false,
    sendChannel = "全国业余无线电综合交流频道",
    sendChannelEnabled = true,
    receiveChannelCount = 3,
    receiveChannelsEnabled = true,
    speaker = "BH3XYZ-2",
    error = "",
)

@Preview(name = "Components Light", widthDp = 360, showBackground = true)
@Composable
private fun ComponentsLightPreview() {
    DraarlTheme {
        ComponentPreviewContent()
    }
}

@Preview(
    name = "Components Dark Large Text",
    widthDp = 360,
    fontScale = 1.5f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun ComponentsDarkPreview() {
    DraarlTheme(darkTheme = true) {
        ComponentPreviewContent()
    }
}

@Preview(name = "Bottom Bar 2x Text", widthDp = 360, heightDp = 100, fontScale = 2f, showBackground = true)
@Composable
private fun BottomBarLargeTextPreview() {
    DraarlTheme {
        DraarlBottomBar(PreviewNavigationItems, "radio", {})
    }
}

@Preview(name = "Radio Status Narrow", widthDp = 360, showBackground = true)
@Composable
private fun RadioStatusNarrowPreview() {
    DraarlTheme {
        RadioStatusPreviewContent(PreviewRadioStatus)
    }
}

@Preview(
    name = "Radio Status Dark Large Text",
    widthDp = 360,
    fontScale = 1.5f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun RadioStatusDarkPreview() {
    DraarlTheme(darkTheme = true) {
        RadioStatusPreviewContent(
            PreviewRadioStatus.copy(
                connectionText = "连接中断，正在重新选择可用的低延迟入口",
                connectionTone = StatusTone.CONNECTING,
                nodeSelectionEnabled = false,
                sendChannelEnabled = false,
                receiveChannelsEnabled = false,
                speaker = "",
                error = "当前链路不可用，系统将在网络恢复后自动重试。",
            ),
        )
    }
}

@Preview(name = "Dialog And Sheet", widthDp = 360, showBackground = true)
@Composable
private fun ContainersPreview() {
    DraarlTheme {
        ContainersPreviewContent()
    }
}

@Preview(
    name = "Dialog And Sheet Dark Large Text",
    widthDp = 360,
    fontScale = 1.5f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun ContainersDarkPreview() {
    DraarlTheme(darkTheme = true) {
        ContainersPreviewContent()
    }
}

@Composable
private fun ComponentPreviewContent() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "链路状态", detail = "长文本、大字体和禁用状态基线")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusIndicator("已连接", StatusTone.CONNECTED)
                    StatusIndicator("正在发射", StatusTone.TRANSMIT)
                }
                DataRow("当前入口", "CN-EAST-01 / 42 ms", technical = true, leadingIcon = Icons.Default.Devices)
                InlineNotice("链路波动，系统正在自动选择延迟更低的入口。", tone = StatusTone.WARNING)
                DraarlSegmentedControl(
                    segments = listOf(DraarlSegment("map", "地图"), DraarlSegment("messages", "通联日志")),
                    selectedKey = "messages",
                    onSelect = {},
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommandButton("收听", {}, Modifier.weight(1f), leadingIcon = Icons.Default.Groups)
                    CommandButton("发射", {}, Modifier.weight(1f), style = CommandStyle.PRIMARY, leadingIcon = Icons.Default.Mic)
                    CommandIconButton({}, "不可用工具", Icons.Default.Build, enabled = false)
                }
            }
            DraarlBottomBar(PreviewNavigationItems, "radio", {})
        }
    }
}

@Composable
private fun RadioStatusPreviewContent(state: RadioStatusStripState) {
    RadioStatusStrip(
        state = state,
        avatar = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("BH", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        audioLevel = { modifier ->
            RadioAudioLevelMeter(
                receiveLevel = 0.62f,
                transmitLevel = 0f,
                receiving = state.receiving,
                transmitting = state.transmitting,
                modifier = modifier,
            )
        },
        onSelectNode = {},
        onToggleDenoise = {},
        onToggleMuted = {},
        onSelectSendChannel = {},
        onSelectReceiveChannels = {},
    )
}

@Composable
private fun ContainersPreviewContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DraarlDialogContent(
            title = "选择用于实时通联的低延迟边缘节点",
            dismissAction = DraarlAction("关闭", {}),
        ) {
            DataRow("华东入口", "42 ms", technical = true)
            DataRow("备用入口", "不可达", technical = true)
        }
        Surface(shape = MaterialTheme.shapes.large) {
            DraarlSheetContent(
                title = "CW 自动发送",
                dismissAction = DraarlAction("取消", {}),
                confirmAction = DraarlAction("发送 CW", {}, enabled = false, style = CommandStyle.PRIMARY),
            ) {
                DataRow("速度", "18 WPM", technical = true)
                DataRow("音调", "700 Hz", technical = true)
            }
        }
    }
}
