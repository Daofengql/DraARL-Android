package cn.silverdragon.draarl.tools

data class RelayStation(
    val id: Int,
    val name: String,
    val uplinkFrequency: String,
    val downlinkFrequency: String,
    val transmitTone: String,
    val receiveTone: String,
    val ownerCallsign: String,
    val location: String,
    val status: Int,
    val note: String,
)

data class LogbookEntry(
    val id: Int = 0,
    val myCallsign: String,
    val timeUtc: String,
    val txFrequency: Double,
    val rxFrequency: Double,
    val cqZone: Int = 0,
    val ituZone: Int = 0,
    val mode: String,
    val callsign: String,
    val theirRst: String = "59",
    val theirPower: Int? = null,
    val theirQth: String = "",
    val theirRadio: String = "",
    val theirAntenna: String = "",
    val myRst: String = "59",
    val myPower: Int? = null,
    val myQth: String = "",
    val myRadio: String = "",
    val myAntenna: String = "",
    val notes: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class LogbookPage(
    val items: List<LogbookEntry>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
)

data class RadioPreset(
    val id: Int = 0,
    val name: String,
    val radio: String,
    val antenna: String,
    val power: Int? = null,
    val qth: String = "",
    val sortOrder: Int = 0,
)

data class LogbookDraft(
    val editingId: Int = 0,
    val myCallsign: String = "",
    val localTime: String = "",
    val txFrequency: String = "",
    val rxFrequency: String = "",
    val cqZone: String = "",
    val ituZone: String = "",
    val mode: String = "FM",
    val callsign: String = "",
    val theirRst: String = "59",
    val theirPower: String = "",
    val theirQth: String = "",
    val theirRadio: String = "",
    val theirAntenna: String = "",
    val myRst: String = "59",
    val myPower: String = "",
    val myQth: String = "",
    val myRadio: String = "",
    val myAntenna: String = "",
    val notes: String = "",
)

enum class ToolDestination { HOME, BLE, RELAYS, LOGBOOK, LOGBOOK_EDITOR, MAIDENHEAD }
