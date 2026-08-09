package cn.silverdragon.draarl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cn.silverdragon.draarl.data.DailyCommunicationStats
import cn.silverdragon.draarl.data.DashboardData
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.ui.components.RadioStatusStripState
import cn.silverdragon.draarl.ui.components.StatusTone
import cn.silverdragon.draarl.ui.screens.DevicesContent
import cn.silverdragon.draarl.ui.screens.DevicesContentState
import cn.silverdragon.draarl.ui.screens.GroupsContent
import cn.silverdragon.draarl.ui.screens.ProfileContent
import cn.silverdragon.draarl.ui.screens.RadioComposer
import cn.silverdragon.draarl.ui.screens.RadioConnectionPanel
import cn.silverdragon.draarl.ui.screens.RadioConnectionPanelState
import cn.silverdragon.draarl.ui.screens.RadioMessageEmptyFeedback
import cn.silverdragon.draarl.ui.screens.RadioModeSwitcher
import cn.silverdragon.draarl.ui.screens.ToolsHome
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Devices Light Narrow", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun DevicesLightBaseline() {
    DevicesBaseline(darkTheme = false)
}

@PreviewTest
@Preview(name = "Devices Dark Regular", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
fun DevicesDarkBaseline() {
    DevicesBaseline(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "Devices Loading Light Medium Text",
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.3f,
    showBackground = true
)
@Composable
fun DevicesLoadingLightBaseline() {
    BaselineScreen(selectedKey = "devices", darkTheme = false) {
        DevicesContent(
            state = DevicesContentState(
                devices = emptyList(),
                groups = emptyList(),
                defaultGroupId = null,
                loading = true
            ),
            onAction = {}
        )
    }
}

@PreviewTest
@Preview(name = "Groups Light Narrow", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun GroupsLightBaseline() {
    GroupsBaseline(darkTheme = false)
}

@PreviewTest
@Preview(name = "Groups Dark Regular", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
fun GroupsDarkBaseline() {
    GroupsBaseline(darkTheme = true)
}

@PreviewTest
@Preview(name = "Groups Empty Dark", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
fun GroupsEmptyDarkBaseline() {
    BaselineScreen(selectedKey = "groups", darkTheme = true) {
        GroupsContent(
            groups = emptyList(),
            loading = false,
            onOpenGroup = {},
            onSearchToJoin = {},
            onCreateGroup = {}
        )
    }
}

@PreviewTest
@Preview(name = "PTT Light Regular", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
fun RadioLightBaseline() {
    RadioBaseline(darkTheme = false)
}

@PreviewTest
@Preview(name = "PTT Dark Narrow", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun RadioDarkBaseline() {
    RadioBaseline(darkTheme = true)
}

@PreviewTest
@Preview(
    name = "PTT Error Light Large Text",
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun RadioErrorLightLargeTextBaseline() {
    RadioBaseline(darkTheme = false, hasSyncError = true)
}

@PreviewTest
@Preview(name = "Tools Light Landscape", widthDp = 800, heightDp = 360, showBackground = true)
@Composable
fun ToolsLightBaseline() {
    ToolsBaseline(darkTheme = false, approved = true)
}

@PreviewTest
@Preview(name = "Tools Dark Regular", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
fun ToolsDarkBaseline() {
    ToolsBaseline(darkTheme = true, approved = false)
}

@PreviewTest
@Preview(
    name = "Tools Error Light Large Text",
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun ToolsErrorLightLargeTextBaseline() {
    BaselineScreen(selectedKey = "tools", darkTheme = false) {
        ToolsHome(
            approved = true,
            error = "无法刷新中继台数据，请检查网络后重试。",
            onClearError = {},
            onOpen = {}
        )
    }
}

@PreviewTest
@Preview(name = "Profile Light Regular", widthDp = 411, heightDp = 891, showBackground = true)
@Composable
fun ProfileLightBaseline() {
    ProfileBaseline(darkTheme = false)
}

@PreviewTest
@Preview(
    name = "Profile Dark Narrow Large Text",
    widthDp = 360,
    heightDp = 800,
    fontScale = 1.5f,
    showBackground = true
)
@Composable
fun ProfileDarkLargeTextBaseline() {
    ProfileBaseline(darkTheme = true)
}

@Composable
private fun DevicesBaseline(darkTheme: Boolean) {
    BaselineScreen(selectedKey = "devices", darkTheme = darkTheme) {
        DevicesContent(
            state = DevicesContentState(
                devices = SampleDevices,
                groups = SampleGroups,
                defaultGroupId = SampleGroups.first().id,
                loading = false
            ),
            onAction = {}
        )
    }
}

@Composable
private fun GroupsBaseline(darkTheme: Boolean) {
    BaselineScreen(selectedKey = "groups", darkTheme = darkTheme) {
        GroupsContent(
            groups = SampleGroups,
            loading = false,
            onOpenGroup = {},
            onSearchToJoin = {},
            onCreateGroup = {}
        )
    }
}

@Composable
private fun RadioBaseline(darkTheme: Boolean, hasSyncError: Boolean = false) {
    BaselineScreen(selectedKey = "radio", darkTheme = darkTheme) {
        Column(Modifier.fillMaxSize()) {
            RadioConnectionPanel(
                state = SampleRadioState,
                onAction = {}
            )
            RadioModeSwitcher(
                mapSelected = false,
                onMap = {},
                onMessages = {},
                modifier = Modifier.fillMaxWidth()
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                RadioMessageEmptyFeedback(
                    hasSyncError = hasSyncError,
                    modifier = Modifier.fillMaxSize()
                )
            }
            RadioComposer(
                textMode = false,
                text = "",
                connected = true,
                transmitting = false,
                receiving = false,
                canSendText = true,
                onTextModeChange = {},
                onTextChange = {},
                onTextInputFocused = {},
                onSendText = {},
                onMoreMessage = {},
                onStartPtt = { true },
                onStopPtt = {}
            )
        }
    }
}

@Composable
private fun ToolsBaseline(darkTheme: Boolean, approved: Boolean) {
    BaselineScreen(selectedKey = "tools", darkTheme = darkTheme) {
        ToolsHome(
            approved = approved,
            error = "",
            onClearError = {},
            onOpen = {}
        )
    }
}

@Composable
private fun ProfileBaseline(darkTheme: Boolean) {
    BaselineScreen(selectedKey = "profile", darkTheme = darkTheme) {
        ProfileContent(
            user = SampleUser,
            dashboard = SampleDashboard,
            onAvatarClick = {},
            onNavigate = {}
        )
    }
}

private val SampleDevices = listOf(
    Device(
        id = 18,
        name = "车载中继与应急通信主机",
        callsign = "BG5DRA",
        ssid = 2,
        model = 1,
        groupId = 999,
        online = true,
        enabled = true,
        qth = "浙江杭州"
    ),
    Device(
        id = 23,
        name = "便携网络节点",
        callsign = "BG5DRA",
        ssid = 7,
        model = 2,
        groupId = 1206,
        online = false,
        enabled = true,
        disableReceive = true
    ),
    Device(
        id = 31,
        name = "Android 客户端",
        callsign = "BG5DRA",
        ssid = 101,
        model = 101,
        groupId = 73,
        online = true,
        enabled = true
    )
)

private val SampleGroups = listOf(
    Group(
        id = 999,
        name = "全国公共联络",
        type = 1,
        status = 1,
        note = "公共呼叫与日常联络",
        joined = true,
        onlineCount = 42,
        totalCount = 318
    ),
    Group(
        id = 73,
        name = "城市中继测试",
        type = 1,
        status = 1,
        note = "链路质量与覆盖测试",
        joined = true,
        onlineCount = 8,
        totalCount = 27
    ),
    Group(
        id = 1206,
        name = "华东应急通信协调与演练群组",
        type = 2,
        status = 1,
        note = "跨区域长中文名称与说明文字的稳定显示验证",
        ownerId = 7,
        ownerCallsign = "BG5DRA",
        joined = true,
        owner = true,
        requiresPassword = true,
        onlineCount = 6,
        totalCount = 19
    )
)

private val SampleRadioState = RadioConnectionPanelState(
    strip = RadioStatusStripState(
        stationIdentity = "BG5DRA-101",
        radioIdentifiers = "MDC 12065 · DMR 4600012",
        connectionText = "UDP 已连接 · 华东低延迟入口",
        connectionTone = StatusTone.CONNECTED,
        nodeSelectionEnabled = true,
        onlineCount = 18,
        receiving = false,
        transmitting = false,
        denoiseEnabled = true,
        muted = false,
        sendChannel = "华东应急通信协调与演练群组",
        sendChannelEnabled = true,
        receiveChannelCount = 3,
        receiveChannelsEnabled = true,
        speaker = "",
        error = ""
    ),
    avatarUrl = "",
    receiveLevel = 0.42f,
    transmitLevel = 0f,
    receiving = false,
    transmitting = false
)

private val SampleUser = User(
    id = 7,
    username = "radio_operator",
    nickname = "林默 · 应急通信值守员",
    callsign = "BG5DRA",
    email = "operator@example.com",
    emailVerified = true,
    approvalStatus = 1,
    address = "浙江省杭州市西湖区应急通信联合值守中心",
    introduction = "负责日常链路巡检、跨区域通信演练与突发情况下的现场协调。",
    dmrId = 4_600_012,
    mdcId = "12065"
)

private val SampleDashboard = DashboardData(
    devices = 3,
    onlineDevices = 2,
    groups = 3,
    communications = 128,
    communicationDurationMs = 27_450_000,
    communicationTrend = listOf(
        DailyCommunicationStats("08-02", 12, 1_200_000),
        DailyCommunicationStats("08-03", 18, 2_100_000),
        DailyCommunicationStats("08-04", 9, 860_000),
        DailyCommunicationStats("08-05", 25, 4_300_000),
        DailyCommunicationStats("08-06", 16, 2_740_000),
        DailyCommunicationStats("08-07", 31, 5_900_000),
        DailyCommunicationStats("08-08", 17, 3_100_000)
    )
)
