package cn.silverdragon.draarl.data

fun formatRadioIdentity(identity: String, ssid: Int): String {
    val normalized = identity.trim()
    return when {
        normalized.isNotEmpty() && ssid > 0 -> "$normalized-$ssid"
        normalized.isNotEmpty() -> normalized
        ssid > 0 -> "SSID-$ssid"
        else -> "未知台站"
    }
}
