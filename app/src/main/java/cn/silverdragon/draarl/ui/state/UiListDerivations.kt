package cn.silverdragon.draarl.ui.state

import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group

internal data class VisibleGroupSections(
    val publicGroups: List<Group>,
    val privateGroups: List<Group>,
)

internal fun filterDevices(devices: List<Device>, rawQuery: String): List<Device> {
    val query = rawQuery.trim()
    if (query.isBlank()) return devices
    return devices.filter { device ->
        device.name.contains(query, ignoreCase = true) ||
            device.callsign.contains(query, ignoreCase = true) ||
            device.ssid.toString().contains(query)
    }
}

internal fun visibleGroupSections(groups: List<Group>, rawQuery: String): VisibleGroupSections {
    val query = rawQuery.trim()
    val publicGroups = ArrayList<Group>(groups.size)
    val privateGroups = ArrayList<Group>(groups.size)
    groups.forEach { group ->
        val matches = query.isBlank() ||
            group.name.contains(query, ignoreCase = true) ||
            group.id.toString().contains(query)
        if (matches) {
            when {
                !group.isPrivate -> publicGroups += group
                group.joined -> privateGroups += group
            }
        }
    }
    return VisibleGroupSections(publicGroups, privateGroups)
}

internal fun groupNamesById(groups: List<Group>): Map<Int, String> = groups.associate { it.id to it.name }

internal fun activeGroups(groups: List<Group>): List<Group> = groups.filter { it.status == 1 }

internal fun availableRadioGroups(groups: List<Group>): List<Group> = groups.filter { group ->
    !group.isPrivate || group.joined || group.owner
}
