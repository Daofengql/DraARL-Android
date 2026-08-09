package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.DeviceBindPreview
import cn.silverdragon.draarl.data.DeviceBindResult
import cn.silverdragon.draarl.data.DevicePasswordInfo
import cn.silverdragon.draarl.data.ReplaceableDevice
import org.json.JSONArray
import org.json.JSONObject

internal class DevicesApiClient(requester: ApiJsonRequester) :
    DevicesApi,
    DeviceManagementApi by DeviceManagementApiClient(requester),
    DeviceProvisioningApi by DeviceProvisioningApiClient(requester)

private class DeviceManagementApiClient(private val requester: ApiJsonRequester) : DeviceManagementApi {
    override fun getDevices(): List<Device> {
        val result = ArrayList<Device>()
        var page = FIRST_PAGE
        var total = Int.MAX_VALUE
        while (result.size < total) {
            val data = requester.execute(
                "GET",
                "/api/devices?page=$page&limit=$DEVICE_PAGE_SIZE&owner_only=true"
            ).requireObject("data")
            total = data.optInt("total", total)
            val pageItems = (data.optJSONArray("items") ?: JSONArray()).objects().map(JSONObject::toDevice)
            result += pageItems
            if (pageItems.size < DEVICE_PAGE_SIZE) {
                total = result.size
            } else {
                page += 1
            }
        }
        return result
    }

    override fun getDefaultDeviceGroup(): Int? = requester.execute("GET", "/api/user/device-default-group")
        .requireObject("data")
        .optNullableInt("group_id")

    override fun setDefaultDeviceGroup(groupId: Int?): Int? {
        val body = JSONObject().put("group_id", groupId ?: JSONObject.NULL)
        return requester.execute("PUT", "/api/user/device-default-group", body)
            .requireObject("data")
            .optNullableInt("group_id")
    }

    override fun updateDevice(deviceId: Int, name: String?, disableSend: Boolean?, disableReceive: Boolean?): Device {
        val body = JSONObject().apply {
            name?.let { put("name", it) }
            disableSend?.let { put("disable_send", it) }
            disableReceive?.let { put("disable_recv", it) }
        }
        return requester.execute("PUT", "/api/devices/$deviceId", body).requireObject("data").toDevice()
    }

    override fun deleteDevice(deviceId: Int) {
        requester.execute("DELETE", "/api/devices/$deviceId")
    }

    override fun switchDeviceGroup(deviceId: Int, groupId: Int, password: String) {
        requester.execute(
            "POST",
            "/api/device/changegroup",
            JSONObject()
                .put("device_id", deviceId)
                .put("group_id", groupId)
                .put("password", password)
        )
    }
}

private class DeviceProvisioningApiClient(private val requester: ApiJsonRequester) : DeviceProvisioningApi {
    override fun getDeviceConfig(deviceId: Int): Map<String, String> = jsonStringMap(
        requester.execute("GET", "/api/devices/$deviceId/config").requireObject("data")
    )

    override fun updateDeviceConfig(deviceId: Int, config: Map<String, String>): Map<String, String> {
        val body = JSONObject().apply { config.forEach(::put) }
        return jsonStringMap(
            requester.execute("PUT", "/api/devices/$deviceId/config", body).requireObject("data")
        )
    }

    override fun syncDeviceConfig(deviceId: Int): String = requester
        .execute("POST", "/api/devices/$deviceId/config/sync")
        .optJSONObject("data")
        ?.optStringClean("message")
        .orEmpty()
        .ifBlank { "同步请求已发送" }

    override fun getDevicePassword(): DevicePasswordInfo {
        val data = requester.execute("GET", "/api/user/device-password").requireObject("data")
        return data.toDevicePasswordInfo()
    }

    override fun regenerateDevicePassword(): DevicePasswordInfo {
        val data = requester.execute("POST", "/api/user/device-password/regenerate").requireObject("data")
        return data.toDevicePasswordInfo(generated = true)
    }

    override fun bindDevice(dynamicCode: String): DeviceBindPreview {
        val data = requester.execute(
            "POST",
            "/api/device/bind",
            JSONObject().put("dynamic_code", dynamicCode)
        ).requireObject("data")
        val availableSsids = data.optJSONArray("available_ssids") ?: JSONArray()
        val replacements = data.optJSONArray("replaceable_devices") ?: JSONArray()
        return DeviceBindPreview(
            deviceMac = data.optStringClean("device_mac"),
            callsign = data.optStringClean("call_sign"),
            message = data.optStringClean("message"),
            availableSsids = buildList {
                for (index in 0 until availableSsids.length()) add(availableSsids.optInt(index))
            },
            recommendedSsid = data.optInt("recommended_ssid"),
            replaceableDevices = replacements.objects().map(JSONObject::toReplaceableDevice)
        )
    }

    override fun submitDeviceConfig(deviceMac: String, ssid: Int?, replaceDeviceId: Int?): DeviceBindResult {
        val body = JSONObject().put("device_mac", deviceMac).apply {
            ssid?.let { put("ssid", it) }
            replaceDeviceId?.let { put("replace_device_id", it) }
        }
        val data = requester.execute("POST", "/api/device/submit-config", body).requireObject("data")
        val auth = data.optJSONObject("udp_auth_info") ?: JSONObject()
        return DeviceBindResult(
            message = data.optStringClean("message"),
            ssid = data.optNullableInt("ssid"),
            username = auth.optStringClean("username"),
            devicePassword = auth.optStringClean("device_password"),
            dmrId = data.optInt("dmr_id")
        )
    }
}

private fun JSONObject.toDevicePasswordInfo(generated: Boolean = false) = DevicePasswordInfo(
    password = optStringClean("device_password"),
    hasPassword = if (generated) true else optBoolean("has_password"),
    isNew = if (generated) true else optBoolean("is_new"),
    createdAt = optStringClean("created_at")
)

private fun JSONObject.toReplaceableDevice() = ReplaceableDevice(
    deviceId = optInt("device_id"),
    name = optStringClean("name"),
    callsign = optStringClean("callsign"),
    ssid = optInt("ssid"),
    lastOnlineIp = optStringClean("last_online_ip"),
    onlineTime = optStringClean("online_time")
)

private const val DEVICE_PAGE_SIZE = 100
private const val FIRST_PAGE = 1
