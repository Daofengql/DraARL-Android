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

fun formatRadioIdentifiers(mdcId: String, dmrId: Int): String = buildList {
    mdcId.trim().takeIf(String::isNotEmpty)?.let { add("MDC $it") }
    if (dmrId > 0) add("DMR $dmrId")
}.joinToString("  ·  ")
