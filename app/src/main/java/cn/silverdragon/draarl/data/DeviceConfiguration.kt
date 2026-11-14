package cn.silverdragon.draarl.data

/** Configuration and capabilities are read together to avoid using a stale device-list model. */
data class DeviceConfiguration(
    val values: Map<String, String>,
    val model: Int? = null,
    val supportedKeys: Set<String>? = null
)

fun deviceConfigKeys(model: Int): Set<String> {
    val audio = setOf("adc_gain_db", "adc_volume", "dac_volume")
    return when (model) {
        1 -> audio + setOf(
            "rx_freq", "tx_freq", "rx_ctcss", "tx_ctcss", "rx_tone_mode", "rx_tone_value",
            "tx_tone_mode", "tx_tone_value", "sql_level", "power_level", "tx_bandwidth"
        )

        2 -> audio + setOf("sql_active_high", "ptt_active_high")

        else -> emptySet()
    }
}
