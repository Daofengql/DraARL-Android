package cn.silverdragon.draarl.tools.ble

data class BleDeviceInfo(val address: String, val name: String, val rssi: Int)

data class BleDeviceProfile(
    val key: String,
    val deviceModel: Int,
    val label: String,
    val description: String,
    val supportsWifi: Boolean,
    val supportsDraarl: Boolean,
)

object BleDeviceProfiles {
    val all = listOf(
        BleDeviceProfile(
            key = "devmodel1",
            deviceModel = 1,
            label = "ESP32 链路盒子（1W 射频版）",
            description = "Wi-Fi 与 DraARL 预配置",
            supportsWifi = true,
            supportsDraarl = true,
        ),
    )

    fun find(key: String): BleDeviceProfile = all.firstOrNull { it.key == key } ?: all.first()
}

enum class BleConnectionPhase { IDLE, SCANNING, CONNECTING, DISCOVERING, READY, DISCONNECTED, ERROR }

data class BleProvisionStatus(
    val phase: BleConnectionPhase = BleConnectionPhase.IDLE,
    val deviceName: String = "",
    val wifiState: String = "未知",
    val bleState: String = "未知",
    val authenticated: Boolean = false,
    val rssi: Int? = null,
    val error: String = "",
)

data class BleWifiConfig(
    val ssid: String = "",
    val password: String = "",
    val dhcp: Boolean = true,
    val ip: String = "",
    val gateway: String = "",
    val subnet: String = "",
    val dns1: String = "",
    val dns2: String = "",
)

data class BleServerConfig(
    val callsign: String = "",
    val nodeSsid: Int = 0,
    val udpHost: String = "ptt.4l2.cn",
    val udpPort: Int = 60_050,
    val httpApiBaseUrl: String = "https://ptt.4l2.cn",
    val account: String = "",
    val deviceAuthPassword: String = "",
)

data class BleProvisionConfig(
    val wifi: BleWifiConfig = BleWifiConfig(),
    val server: BleServerConfig = BleServerConfig(),
)
