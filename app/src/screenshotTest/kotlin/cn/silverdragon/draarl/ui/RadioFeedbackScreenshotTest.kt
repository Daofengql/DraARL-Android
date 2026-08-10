package cn.silverdragon.draarl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.silverdragon.draarl.ui.screens.RadioHistoryFeedback
import cn.silverdragon.draarl.ui.theme.DraarlTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "PTT History Sync Error Dark Large Text",
    widthDp = 360,
    heightDp = 180,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun RadioHistorySyncErrorDarkLargeTextBaseline() {
    DraarlTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                RadioHistoryFeedback(loading = false, hasSyncError = true)
            }
        }
    }
}
