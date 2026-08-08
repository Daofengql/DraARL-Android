package cn.silverdragon.draarl.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
