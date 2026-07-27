package cn.silverdragon.draarl.tools

internal fun LogbookDraft.toLogbookEntry(): LogbookEntry {
    val tx = txFrequency.toDoubleOrNull()
    val rx = rxFrequency.ifBlank { txFrequency }.toDoubleOrNull()
    val parsedCqZone = cqZone.optionalInt("CQ 分区")
    val parsedItuZone = ituZone.optionalInt("ITU 分区")
    val parsedTheirPower = theirPower.optionalInt("对方功率")
    val parsedMyPower = myPower.optionalInt("我方功率")

    require(myCallsign.isNotBlank() && callsign.isNotBlank()) { "请填写双方呼号" }
    require(tx != null && tx > 0.0 && rx != null && rx > 0.0) { "请填写正确的收发频率" }
    require(mode.isNotBlank()) { "请选择通信模式" }
    require(parsedCqZone == null || parsedCqZone in 1..40) { "CQ 分区应为 1 到 40" }
    require(parsedItuZone == null || parsedItuZone in 1..90) { "ITU 分区应为 1 到 90" }
    require(parsedTheirPower == null || parsedTheirPower >= 0) { "对方功率不能小于 0" }
    require(parsedMyPower == null || parsedMyPower >= 0) { "我方功率不能小于 0" }

    return LogbookEntry(
        id = editingId,
        myCallsign = myCallsign.trim().uppercase(),
        timeUtc = LogbookTime.localToUtc(localTime),
        txFrequency = tx,
        rxFrequency = rx,
        cqZone = parsedCqZone ?: 0,
        ituZone = parsedItuZone ?: 0,
        mode = mode.trim().uppercase(),
        callsign = callsign.trim().uppercase(),
        theirRst = theirRst.trim(),
        theirPower = parsedTheirPower,
        theirQth = theirQth.trim(),
        theirRadio = theirRadio.trim(),
        theirAntenna = theirAntenna.trim(),
        myRst = myRst.trim(),
        myPower = parsedMyPower,
        myQth = myQth.trim(),
        myRadio = myRadio.trim(),
        myAntenna = myAntenna.trim(),
        notes = notes.trim(),
    )
}

private fun String.optionalInt(label: String): Int? {
    if (isBlank()) return null
    return trim().toIntOrNull() ?: throw IllegalArgumentException("$label 必须是整数")
}
