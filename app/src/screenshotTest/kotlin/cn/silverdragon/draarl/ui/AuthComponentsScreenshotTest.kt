package cn.silverdragon.draarl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.data.RegistrationResult
import cn.silverdragon.draarl.ui.components.CommandButton
import cn.silverdragon.draarl.ui.components.CommandStyle
import cn.silverdragon.draarl.ui.screens.AuthErrorNotice
import cn.silverdragon.draarl.ui.screens.RegistrationSuccess
import cn.silverdragon.draarl.ui.theme.DraarlTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "Auth Error Dark Large Text",
    widthDp = 360,
    heightDp = 300,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun AuthErrorDarkLargeTextBaseline() {
    DraarlTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AuthErrorNotice("账号、密码或图片验证码不正确，请重新输入。")
                CommandButton(
                    label = "登录",
                    onClick = {},
                    enabled = false,
                    loading = true,
                    style = CommandStyle.PRIMARY,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@PreviewTest
@Preview(
    name = "Registration Success Light Medium Text",
    widthDp = 360,
    heightDp = 560,
    fontScale = 1.3f,
    showBackground = true
)
@Composable
fun RegistrationSuccessLightBaseline() {
    DraarlTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(24.dp)) {
                RegistrationSuccess(
                    result = RegistrationResult(
                        id = 7,
                        username = "radio_operator",
                        nickname = "应急通信值守员",
                        approvalStatus = 0,
                        devicePassword = "DRAARL-7K4M-92QX"
                    ),
                    onLogin = {}
                )
            }
        }
    }
}
