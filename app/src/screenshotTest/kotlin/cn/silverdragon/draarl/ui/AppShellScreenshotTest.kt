package cn.silverdragon.draarl.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.silverdragon.draarl.ui.components.DraarlBottomBar
import cn.silverdragon.draarl.ui.components.DraarlBottomBarItem
import cn.silverdragon.draarl.ui.theme.DraarlTheme
import com.android.tools.screenshot.PreviewTest

private val BaselineNavigationItems = listOf(
    DraarlBottomBarItem("devices", "设备", Icons.Default.Devices),
    DraarlBottomBarItem("groups", "群组", Icons.Default.Groups),
    DraarlBottomBarItem("radio", "PTT", Icons.Default.Mic, prominent = true),
    DraarlBottomBarItem("tools", "工具", Icons.Default.Build),
    DraarlBottomBarItem("profile", "我的", Icons.Default.Person)
)

@Composable
internal fun BaselineScreen(selectedKey: String, darkTheme: Boolean, content: @Composable () -> Unit = {}) {
    DraarlTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                DraarlBottomBar(BaselineNavigationItems, selectedKey = selectedKey, onSelect = {})
            }
        ) { contentPadding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                color = MaterialTheme.colorScheme.background,
                content = content
            )
        }
    }
}

@PreviewTest
@Preview(name = "App Shell Light", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun AppShellLightBaseline() {
    BaselineScreen(selectedKey = "radio", darkTheme = false)
}

@PreviewTest
@Preview(name = "App Shell Dark", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
fun AppShellDarkBaseline() {
    BaselineScreen(selectedKey = "devices", darkTheme = true)
}
