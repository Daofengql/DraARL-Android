package cn.silverdragon.draarl.radio

internal data class VoiceStreamKey(
    val sourceGroupId: Int,
    val senderIdentity: String,
    val senderSsid: Int,
) {
    init {
        require(sourceGroupId > 0)
        require(senderIdentity.isNotBlank())
        require(senderSsid in 0..255)
    }

    val playbackKey: String = "$sourceGroupId\u0000${senderIdentity.trim().lowercase()}\u0000$senderSsid"
}
