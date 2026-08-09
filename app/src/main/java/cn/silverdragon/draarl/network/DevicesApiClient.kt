package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.DeviceBindPreview
import cn.silverdragon.draarl.data.DeviceBindResult
import cn.silverdragon.draarl.data.DevicePasswordInfo
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
            val data = requester.executeMapped(
                "GET",
                "/api/devices?page=$page&limit=$DEVICE_PAGE_SIZE&owner_only=true",
                mapper = DeviceApiResponseMapper::page
            )
            total = data.total
            val pageItems = data.items.map(DeviceDto::toDomain)
            result += pageItems
            if (pageItems.size < DEVICE_PAGE_SIZE) {
                total = result.size
            } else {
                page += 1
            }
        }
        return result
    }

    override fun getDefaultDeviceGroup(): Int? = requester.executeMapped(
        "GET",
        "/api/user/device-default-group",
        mapper = DeviceApiResponseMapper::defaultGroup
    ).groupId

    override fun setDefaultDeviceGroup(groupId: Int?): Int? {
        val body = JSONObject().put("group_id", groupId ?: JSONObject.NULL)
        return requester.executeMapped(
            "PUT",
            "/api/user/device-default-group",
            body,
            mapper = DeviceApiResponseMapper::defaultGroup
        ).groupId
    }

    override fun updateDevice(deviceId: Int, name: String?, disableSend: Boolean?, disableReceive: Boolean?): Device {
        val body = JSONObject().apply {
            name?.let { put("name", it) }
            disableSend?.let { put("disable_send", it) }
            disableReceive?.let { put("disable_recv", it) }
        }
        return requester.executeMapped(
            "PUT",
            "/api/devices/$deviceId",
            body,
            mapper = DeviceApiResponseMapper::device
        ).toDomain()
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
    override fun getDeviceConfig(deviceId: Int): Map<String, String> = requester.executeMapped(
        "GET",
        "/api/devices/$deviceId/config",
        mapper = DeviceApiResponseMapper::config
    ).values

    override fun updateDeviceConfig(deviceId: Int, config: Map<String, String>): Map<String, String> {
        val body = JSONObject().apply { config.forEach(::put) }
        return requester.executeMapped(
            "PUT",
            "/api/devices/$deviceId/config",
            body,
            mapper = DeviceApiResponseMapper::config
        ).values
    }

    override fun syncDeviceConfig(deviceId: Int): String = requester.executeMapped(
        "POST",
        "/api/devices/$deviceId/config/sync",
        mapper = DeviceApiResponseMapper::sync
    ).message

    override fun getDevicePassword(): DevicePasswordInfo = requester.executeMapped("GET", "/api/user/device-password") {
        DeviceApiResponseMapper.password(it, generated = false)
    }.toDomain()

    override fun regenerateDevicePassword(): DevicePasswordInfo =
        requester.executeMapped("POST", "/api/user/device-password/regenerate") {
            DeviceApiResponseMapper.password(it, generated = true)
        }.toDomain()

    override fun bindDevice(dynamicCode: String): DeviceBindPreview = requester.executeMapped(
        "POST",
        "/api/device/bind",
        JSONObject().put("dynamic_code", dynamicCode),
        mapper = DeviceApiResponseMapper::bindPreview
    ).toDomain()

    override fun submitDeviceConfig(deviceMac: String, ssid: Int?, replaceDeviceId: Int?): DeviceBindResult {
        val body = JSONObject().put("device_mac", deviceMac).apply {
            ssid?.let { put("ssid", it) }
            replaceDeviceId?.let { put("replace_device_id", it) }
        }
        return requester.executeMapped(
            "POST",
            "/api/device/submit-config",
            body,
            mapper = DeviceApiResponseMapper::bindResult
        ).toDomain()
    }
}

private const val DEVICE_PAGE_SIZE = 100
private const val FIRST_PAGE = 1
