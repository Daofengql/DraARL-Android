package cn.silverdragon.draarl.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.silverdragon.draarl.ui.screens.RelaySearchContent
import cn.silverdragon.draarl.ui.screens.RelaySearchContentState
import cn.silverdragon.draarl.ui.theme.DraarlTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(
    name = "Tool List Loading Light",
    widthDp = 360,
    heightDp = 640,
    showBackground = true
)
@Composable
fun ToolListLoadingLightBaseline() {
    ToolFeedbackBaseline(
        darkTheme = false,
        state = RelaySearchContentState(
            location = "浙江省 杭州市",
            busy = true,
            queriedLocation = "浙江省 杭州市"
        )
    )
}

@PreviewTest
@Preview(
    name = "Tool List Error Empty Dark Large Text",
    widthDp = 360,
    heightDp = 700,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun ToolListErrorEmptyDarkBaseline() {
    ToolFeedbackBaseline(
        darkTheme = true,
        state = RelaySearchContentState(
            location = "浙江省 杭州市",
            error = "中继台资料暂时无法刷新，请检查网络后重试。",
            queriedLocation = "浙江省 杭州市"
        )
    )
}

@PreviewTest
@Preview(
    name = "Tool List No Results Dark Large Text",
    widthDp = 360,
    heightDp = 700,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun ToolListNoResultsDarkLargeTextBaseline() {
    ToolFeedbackBaseline(
        darkTheme = true,
        state = RelaySearchContentState(
            location = "浙江省 杭州市",
            queriedLocation = "浙江省 杭州市"
        )
    )
}

@Composable
private fun ToolFeedbackBaseline(darkTheme: Boolean, state: RelaySearchContentState) {
    DraarlTheme(darkTheme = darkTheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            RelaySearchContent(
                state = state,
                onLocationChange = {},
                onSearch = {},
                onClearError = {},
                onBack = {}
            )
        }
    }
}
