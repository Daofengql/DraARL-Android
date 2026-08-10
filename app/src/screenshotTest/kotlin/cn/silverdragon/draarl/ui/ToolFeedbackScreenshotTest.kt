package cn.silverdragon.draarl.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.silverdragon.draarl.tools.RelayStation
import cn.silverdragon.draarl.ui.screens.LogbookListFeedback
import cn.silverdragon.draarl.ui.screens.RadioPresetListFeedback
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

@PreviewTest
@Preview(
    name = "Logbook Filter Empty Dark Large Text",
    widthDp = 360,
    heightDp = 360,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun LogbookFilterEmptyDarkLargeTextBaseline() {
    DraarlTheme(darkTheme = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LogbookListFeedback(busy = false, filter = "BG5DRA")
        }
    }
}

@PreviewTest
@Preview(
    name = "Radio Presets Empty Light Large Text",
    widthDp = 360,
    heightDp = 360,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun RadioPresetsEmptyLightLargeTextBaseline() {
    DraarlTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            RadioPresetListFeedback(busy = false)
        }
    }
}

@PreviewTest
@Preview(
    name = "Tool List Results Light Large Text",
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.3f,
    showBackground = true
)
@Composable
fun ToolListResultsLightLargeTextBaseline() {
    ToolFeedbackBaseline(
        darkTheme = false,
        state = RelaySearchContentState(
            location = "浙江省 杭州市",
            queriedLocation = "浙江省 杭州市",
            cacheTime = 1_754_677_200_000L,
            relays = listOf(
                RelayStation(
                    id = 1,
                    name = "杭州西湖应急通信中继台",
                    uplinkFrequency = "145.000",
                    downlinkFrequency = "145.600",
                    transmitTone = "88.5",
                    receiveTone = "88.5",
                    ownerCallsign = "BG5DRA",
                    location = "浙江省杭州市西湖区",
                    status = 1,
                    note = "城市应急通信与日常联络"
                ),
                RelayStation(
                    id = 2,
                    name = "杭州东部备用节点",
                    uplinkFrequency = "438.500",
                    downlinkFrequency = "430.900",
                    transmitTone = "",
                    receiveTone = "",
                    ownerCallsign = "",
                    location = "浙江省杭州市钱塘区",
                    status = 0,
                    note = ""
                )
            )
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
