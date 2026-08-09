package cn.silverdragon.draarl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.components.DataRow
import cn.silverdragon.draarl.ui.components.DraarlAction
import cn.silverdragon.draarl.ui.components.DraarlIconButton
import cn.silverdragon.draarl.ui.components.DraarlScreenHeader
import cn.silverdragon.draarl.ui.components.DraarlSheetContent
import cn.silverdragon.draarl.ui.components.DraarlSheetHandle
import cn.silverdragon.draarl.ui.theme.DraarlTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "Screen Header Light Large Text",
    widthDp = 360,
    heightDp = 140,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun ScreenHeaderLightLargeTextBaseline() {
    DraarlTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                DraarlScreenHeader(
                    title = "数字中继与互联网链路设置",
                    onBack = {},
                    action = {
                        DraarlIconButton(
                            icon = Icons.Default.Save,
                            label = "保存",
                            onClick = {}
                        )
                    }
                )
            }
        }
    }
}

@PreviewTest
@Preview(
    name = "Location Sheet Light Medium Text",
    widthDp = 360,
    heightDp = 640,
    fontScale = 1.3f,
    showBackground = true
)
@Composable
fun LocationSheetLightBaseline() {
    SheetBaseline(darkTheme = false, title = "发送位置") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SheetChoice(
                title = "发送当前位置",
                detail = "使用设备当前的 WGS-84 坐标"
            )
            SheetChoice(
                title = "在地图上标点",
                detail = "选择位置并确认后发送"
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "CW Sheet Dark 2x Text",
    widthDp = 320,
    heightDp = 700,
    fontScale = 2f,
    showBackground = true
)
@Composable
fun CwSheetDarkLargeTextBaseline() {
    SheetBaseline(
        darkTheme = true,
        title = "CW 自动发送",
        dismissAction = DraarlAction("停止试听", {}),
        confirmAction = DraarlAction("发送 CW", {}, style = CommandStyle.PRIMARY)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            DataRow("速度", "18 WPM", technical = true)
            DataRow("音调", "700 Hz", technical = true)
            DataRow("报文", "CQ CQ DE BA7DRA", technical = true)
        }
    }
}

@Composable
private fun SheetBaseline(
    darkTheme: Boolean,
    title: String,
    dismissAction: DraarlAction? = null,
    confirmAction: DraarlAction? = null,
    content: @Composable () -> Unit
) {
    DraarlTheme(darkTheme = darkTheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 8.dp
                ) {
                    Column {
                        DraarlSheetHandle(Modifier.align(Alignment.CenterHorizontally))
                        DraarlSheetContent(
                            title = title,
                            dismissAction = dismissAction,
                            confirmAction = confirmAction
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetChoice(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
