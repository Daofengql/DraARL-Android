package cn.silverdragon.draarl.tools.ble

data class BleDeviceInfo(val address: String, val name: String, val rssi: Int)

data class BleDeviceProfile(
    val key: String,
    val deviceModel: Int,
    val label: String,
    val description: String,
    val supportsWifi: Boolean,
    val supportsDraarl: Boolean
)

object BleDeviceProfiles {
    val all = listOf(
        BleDeviceProfile(
            key = "devmodel1",
            deviceModel = 1,
            label = "RFBox / Box32",
            description = "连接后自动识别射频模块，配置 Wi-Fi、音频与 HTTP API",
            supportsWifi = true,
            supportsDraarl = true
        )
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
    val error: String = ""
)

data class BleWifiConfig(
    val ssid: String = "",
    val password: String = "",
    val dhcp: Boolean = true,
    val ip: String = "",
    val gateway: String = "",
    val subnet: String = "",
    val dns1: String = "",
    val dns2: String = ""
)

data class BleServerConfig(
    val callsign: String = "",
    val nodeSsid: Int = 0,
    val httpApiBaseUrl: String = "https://ptt.4l2.cn",
    val account: String = "",
    val deviceAuthPassword: String = "",
    val dmrId: Int = 0,
    val activeEndpoint: String = ""
)

data class BleProvisionConfig(
    val wifi: BleWifiConfig = BleWifiConfig(),
    val server: BleServerConfig = BleServerConfig(),
    val deviceModel: Int = 1,
    val radio: BleRadioConfig = BleRadioConfig()
)

data class BleRadioConfig(
    val txFrequencyMhz: String = "439.5000",
    val rxFrequencyMhz: String = "438.5000",
    val txToneType: String = "OFF",
    val txToneIndex: Int = 0,
    val rxToneType: String = "OFF",
    val rxToneIndex: Int = 0,
    val squelch: Int = 4,
    val wideBand: Boolean = false,
    val powerHigh: Boolean = false,
    val adcGainDb: Int = 18,
    val adcVolume: Int = 100,
    val dacVolume: Int = 80,
    val sqlActiveHigh: Boolean = false,
    val pttActiveHigh: Boolean = false
)

data class BleWifiNetwork(val ssid: String, val rssi: Int, val secure: Boolean)
