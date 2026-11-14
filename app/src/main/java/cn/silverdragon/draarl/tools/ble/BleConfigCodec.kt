package cn.silverdragon.draarl.tools.ble

import java.net.URI
import org.json.JSONObject

internal object BleConfigCodec {
    private const val MAX_DMR_ID = 16777215
    private const val MAX_SSID = 254
    private const val FIRST_RESERVED_SSID = 100
    private const val LAST_RESERVED_SSID = 105
    private const val MAX_GAIN_DB = 24
    private const val GAIN_STEP_DB = 3
    private const val MAX_VOLUME = 100
    private const val MAX_SQUELCH = 8
    private const val MIN_FREQUENCY_MHZ = 400.0
    private const val MAX_FREQUENCY_MHZ = 470.0

    fun parse(data: JSONObject): BleProvisionConfig {
        val wifi = data.optJSONObject("wifi") ?: JSONObject()
        val server = data.optJSONObject("server") ?: JSONObject()
        val radio = data.optJSONObject("radio") ?: JSONObject()
        val defaults = BleRadioConfig()
        val endpoint = server.optJSONObject("access_point")
        return BleProvisionConfig(
            wifi = BleWifiConfig(
                wifi.optString("ssid"),
                wifi.optString("password"),
                wifi.optBoolean("dhcp", true),
                wifi.optString("ip"),
                wifi.optString("gateway"),
                wifi.optString("subnet"),
                wifi.optString("dns1"),
                wifi.optString("dns2")
            ),
            server = BleServerConfig(
                callsign = server.optString("callsign"),
                nodeSsid = server.optInt("node_ssid"),
                httpApiBaseUrl = server.optString("http_api_base_url", "https://ptt.4l2.cn"),
                account = server.optString("account"),
                deviceAuthPassword = server.optString("device_auth_password"),
                dmrId = server.optInt("dmr_id"),
                activeEndpoint = endpoint?.optString("udp_host")?.takeIf { it.isNotBlank() }
                    ?.let { "$it:${endpoint.optInt("udp_port")}" }.orEmpty()
            ),
            deviceModel = data.optInt("dev_model", 1),
            radio = BleRadioConfig(
                radio.optString("tx_frequency_mhz", "439.5000"), radio.optString("rx_frequency_mhz", "438.5000"),
                radio.optString("tx_tone_type", "OFF"), radio.optInt("tx_tone_index"),
                radio.optString("rx_tone_type", "OFF"), radio.optInt("rx_tone_index"),
                radio.optInt(
                    "squelch",
                    defaults.squelch
                ),
                radio.optBoolean("wide_band"), radio.optBoolean("power_high"),
                radio.optInt("adc_gain_db", defaults.adcGainDb), radio.optInt("adc_volume", defaults.adcVolume),
                radio.optInt("dac_volume", defaults.dacVolume),
                radio.optBoolean("sql_active_high"), radio.optBoolean("ptt_active_high")
            )
        )
    }

    fun serverPayload(config: BleServerConfig): JSONObject {
        val uri = runCatching { URI(config.httpApiBaseUrl.trim()) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) { "API 地址必须使用 HTTPS" }
        require(config.nodeSsid in 0..MAX_SSID && config.nodeSsid !in FIRST_RESERVED_SSID..LAST_RESERVED_SSID) {
            "SSID 必须为 0、1–99 或 106–254"
        }
        require(config.dmrId in 0..MAX_DMR_ID) { "DMR ID 必须为 0–16777215" }
        require(config.account.isBlank() == config.deviceAuthPassword.isBlank()) { "账号和设备认证密码需一起填写，或同时留空使用动态码绑定" }
        return JSONObject().put("callsign", config.callsign).put("node_ssid", config.nodeSsid)
            .put("http_api_base_url", config.httpApiBaseUrl.trim()).put("account", config.account)
            .put("device_auth_password", config.deviceAuthPassword).put("dmr_id", config.dmrId)
    }

    fun radioPayload(model: Int, config: BleRadioConfig): JSONObject {
        require(config.adcGainDb in 0..MAX_GAIN_DB && config.adcGainDb % GAIN_STEP_DB == 0) {
            "ADC 增益需为 0–24 dB 的 3 dB 档位"
        }
        require(config.adcVolume in 0..MAX_VOLUME && config.dacVolume in 0..MAX_VOLUME) { "音量需为 0–100" }
        val data = JSONObject().put("adc_gain_db", config.adcGainDb).put("adc_volume", config.adcVolume)
            .put("dac_volume", config.dacVolume)
        if (model == 1) {
            require(
                listOf(config.txFrequencyMhz, config.rxFrequencyMhz).all {
                    it.toDoubleOrNull()?.let { n ->
                        n.isFinite() &&
                            n in MIN_FREQUENCY_MHZ..MAX_FREQUENCY_MHZ
                    } ==
                        true
                }
            ) { "频率需为 400–470 MHz" }
            require(config.squelch in 0..MAX_SQUELCH) { "静噪需为 0–8" }
            data.put("tx_frequency_mhz", config.txFrequencyMhz).put("rx_frequency_mhz", config.rxFrequencyMhz)
                .put("tx_tone_type", config.txToneType).put("tx_tone_index", config.txToneIndex)
                .put("rx_tone_type", config.rxToneType).put("rx_tone_index", config.rxToneIndex)
                .put("squelch", config.squelch).put("wide_band", config.wideBand).put("power_high", config.powerHigh)
        } else {
            data.put("sql_active_high", config.sqlActiveHigh).put("ptt_active_high", config.pttActiveHigh)
        }
        return data
    }
}
