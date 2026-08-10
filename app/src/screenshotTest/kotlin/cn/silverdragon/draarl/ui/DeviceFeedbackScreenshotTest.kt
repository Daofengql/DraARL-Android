package cn.silverdragon.draarl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.data.DeviceBindResult
import cn.silverdragon.draarl.ui.screens.DeviceBindingResultContent
import cn.silverdragon.draarl.ui.theme.DraarlTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "Device Binding Success Dark Large Text",
    widthDp = 360,
    heightDp = 640,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun DeviceBindingSuccessDarkLargeTextBaseline() {
    DraarlTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DeviceBindingResultContent(
                    result = DeviceBindResult(
                        message = "设备配置已提交，请妥善保存以下连接凭据。",
                        ssid = 101,
                        username = "BG5DRA-101",
                        devicePassword = "DRAARL-8K4M-2P7Q",
                        dmrId = 4_600_012
                    ),
                    onCopyUsername = {},
                    onCopyPassword = {}
                )
            }
        }
    }
}
