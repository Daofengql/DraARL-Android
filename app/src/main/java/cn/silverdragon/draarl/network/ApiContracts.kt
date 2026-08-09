package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.AccessPoint
import cn.silverdragon.draarl.data.CaptchaChallenge
import cn.silverdragon.draarl.data.ChannelMessage
import cn.silverdragon.draarl.data.ChannelMessagePage
import cn.silverdragon.draarl.data.ClientResourceDownload
import cn.silverdragon.draarl.data.ClientResourceManifest
import cn.silverdragon.draarl.data.CommunicationRecord
import cn.silverdragon.draarl.data.CommunicationRecordPage
import cn.silverdragon.draarl.data.CommunicationStats
import cn.silverdragon.draarl.data.DailyCommunicationStats
import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.DeviceBindPreview
import cn.silverdragon.draarl.data.DeviceBindResult
import cn.silverdragon.draarl.data.DevicePasswordInfo
import cn.silverdragon.draarl.data.EmailCodeSession
import cn.silverdragon.draarl.data.Group
import cn.silverdragon.draarl.data.OnlineDevice
import cn.silverdragon.draarl.data.PlatformInfo
import cn.silverdragon.draarl.data.RadioSession
import cn.silverdragon.draarl.data.RegistrationResult
import cn.silverdragon.draarl.data.Session
import cn.silverdragon.draarl.data.User
import cn.silverdragon.draarl.tools.LogbookEntry
import cn.silverdragon.draarl.tools.LogbookPage
import cn.silverdragon.draarl.tools.RadioPreset
import cn.silverdragon.draarl.tools.RelayStation
import org.json.JSONObject

internal interface ApiJsonRequester {
    fun execute(method: String, path: String, body: JSONObject? = null, requiresAuth: Boolean = true): JSONObject
}

data class RegistrationRequest(
    val baseUrl: String,
    val username: String,
    val password: String,
    val callsign: String,
    val phone: String,
    val nickname: String,
    val email: String,
    val sessionId: String,
    val emailCode: String
)

data class GroupUpdateRequest(
    val groupId: Int,
    val name: String? = null,
    val type: Int? = null,
    val password: String? = null,
    val note: String? = null,
    val status: Int? = null
)

data class ProfileUpdateRequest(
    val nickname: String,
    val phone: String,
    val address: String,
    val introduction: String,
    val birthday: String = "",
    val sex: Int = 0,
    val dmrid: Int = 0,
    val mdcid: String = "",
    val alarmMsg: Boolean = false
)

data class ClientResourceManifestQuery(
    val platform: String,
    val arch: String,
    val clientVersion: String = "",
    val channel: String = "stable",
    val osVersion: String = "",
    val androidApi: Int = 0
)

interface AuthApi {
    fun getCaptcha(baseUrl: String): CaptchaChallenge

    fun login(baseUrl: String, username: String, password: String, captchaId: String, captchaCode: String): Session

    fun getRegistrationRequiresEmailVerification(baseUrl: String): Boolean

    fun sendEmailCode(
        baseUrl: String,
        email: String,
        purpose: String,
        captchaId: String,
        captchaCode: String
    ): EmailCodeSession

    fun register(request: RegistrationRequest): RegistrationResult

    fun resetPassword(baseUrl: String, sessionId: String, code: String, newPassword: String)
}

interface DevicesApi :
    DeviceManagementApi,
    DeviceProvisioningApi

interface DeviceManagementApi {
    fun getDevices(): List<Device>

    fun getDefaultDeviceGroup(): Int?

    fun setDefaultDeviceGroup(groupId: Int?): Int?

    fun updateDevice(
        deviceId: Int,
        name: String? = null,
        disableSend: Boolean? = null,
        disableReceive: Boolean? = null
    ): Device

    fun deleteDevice(deviceId: Int)

    fun switchDeviceGroup(deviceId: Int, groupId: Int, password: String = "")
}

interface DeviceProvisioningApi {
    fun getDeviceConfig(deviceId: Int): Map<String, String>

    fun updateDeviceConfig(deviceId: Int, config: Map<String, String>): Map<String, String>

    fun syncDeviceConfig(deviceId: Int): String

    fun getDevicePassword(): DevicePasswordInfo

    fun regenerateDevicePassword(): DevicePasswordInfo

    fun bindDevice(dynamicCode: String): DeviceBindPreview

    fun submitDeviceConfig(deviceMac: String, ssid: Int?, replaceDeviceId: Int?): DeviceBindResult
}

interface GroupsApi :
    GroupDirectoryApi,
    GroupDeviceApi

interface GroupDirectoryApi {
    fun getGroups(): List<Group>

    fun getGroupStats(): Map<Int, Pair<Int, Int>>

    fun getOnlineDevices(groupId: Int): List<OnlineDevice>

    fun joinGroup(groupId: Int, password: String)

    fun leaveGroup(groupId: Int)

    fun searchGroups(keyword: String): List<Group>

    fun createGroup(name: String, type: Int, password: String, note: String): Group

    fun updateGroup(request: GroupUpdateRequest): Group

    fun deleteGroup(groupId: Int)
}

interface GroupDeviceApi {
    fun getGroupDevices(groupId: Int): List<Device>

    fun updateGroupDeviceCommControl(
        groupId: Int,
        deviceId: Int,
        disableSend: Boolean,
        disableReceive: Boolean
    ): Pair<Boolean, Boolean>

    fun kickGroupDevice(groupId: Int, deviceId: Int)
}

interface RadioApi :
    RadioSessionApi,
    RadioMessageApi,
    CommunicationApi

interface RadioSessionApi {
    fun freshAccessToken(): String

    fun renewAccessToken(): String

    fun getAccessPoints(): List<AccessPoint>

    fun getRadioSessions(): List<RadioSession>

    fun updateRadioSessionRouting(sessionId: String, txGroupId: Int, rxGroupIds: Collection<Int>): RadioSession
}

interface RadioMessageApi {
    fun getPublicUserByName(username: String): User

    fun getGroupMessages(
        groupId: Int,
        limit: Int? = null,
        cursor: String = "",
        messageType: String = "all"
    ): ChannelMessagePage

    fun getGroupMessage(groupId: Int, messageId: Int): ChannelMessage
}

interface CommunicationApi {
    fun getCommunicationStats(): CommunicationStats

    fun getCommunicationTrend(): List<DailyCommunicationStats>

    fun getCommunicationRecords(page: Int = 1, pageSize: Int = 100, groupId: Int? = null): CommunicationRecordPage

    fun getCommunicationRecord(id: Int): CommunicationRecord
}

interface ProfileApi {
    fun getMe(updateSession: Boolean = true): User

    fun acceptCurrentUser(user: User)

    fun updateProfile(request: ProfileUpdateRequest): User

    fun uploadFile(fileBytes: ByteArray, fileName: String, fileType: String): String

    fun changePassword(oldPassword: String, newPassword: String)

    fun changeEmail(oldSessionId: String, oldCode: String, newSessionId: String, newCode: String): User
}

interface ToolsApi {
    fun searchPublicRelays(location: String): List<RelayStation>

    fun getLogbooks(page: Int = 1, pageSize: Int = 20, callsign: String = ""): LogbookPage

    fun saveLogbook(entry: LogbookEntry): LogbookEntry

    fun deleteLogbook(id: Int)

    fun deleteLogbooks(ids: Collection<Int>)

    fun getRadioPresets(): List<RadioPreset>

    fun saveRadioPreset(preset: RadioPreset): RadioPreset

    fun deleteRadioPreset(id: Int)

    fun reorderRadioPresets(orders: List<Pair<Int, Int>>)
}

interface UpdatesApi {
    fun getPlatformInfo(): PlatformInfo

    fun getClientResourceManifest(query: ClientResourceManifestQuery): ClientResourceManifest

    fun getClientResourceArtifactDownload(artifactId: Int): ClientResourceDownload
}
