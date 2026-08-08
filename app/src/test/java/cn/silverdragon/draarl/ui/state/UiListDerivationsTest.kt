package cn.silverdragon.draarl.ui.state

import cn.silverdragon.draarl.data.Device
import cn.silverdragon.draarl.data.Group
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UiListDerivationsTest {
    @Test
    fun `blank device query preserves source list`() {
        val devices = listOf(device(1, "客厅电台", "BH1ABC", 7))

        assertSame(devices, filterDevices(devices, "  "))
    }

    @Test
    fun `device query matches name callsign and ssid`() {
        val devices = listOf(
            device(1, "客厅电台", "BH1ABC", 7),
            device(2, "Portable", "BH3XYZ", 12),
            device(3, "车载", "BG5TEST", 2),
        )

        assertEquals(listOf(1), filterDevices(devices, "客厅").map(Device::id))
        assertEquals(listOf(2), filterDevices(devices, "bh3x").map(Device::id))
        assertEquals(listOf(2), filterDevices(devices, "12").map(Device::id))
    }

    @Test
    fun `group sections filter once and keep joined private groups`() {
        val groups = listOf(
            group(1, "全国频道", type = 1),
            group(2, "应急协作", type = 2, joined = true),
            group(3, "未加入私有组", type = 2, joined = false),
            group(20, "本地频道", type = 1),
        )

        val all = visibleGroupSections(groups, "")
        assertEquals(listOf(1, 20), all.publicGroups.map(Group::id))
        assertEquals(listOf(2), all.privateGroups.map(Group::id))

        val byId = visibleGroupSections(groups, "2")
        assertEquals(listOf(20), byId.publicGroups.map(Group::id))
        assertEquals(listOf(2), byId.privateGroups.map(Group::id))
    }

    @Test
    fun `group indexes and eligibility preserve current rules`() {
        val groups = listOf(
            group(1, "公开启用", type = 1, status = 1),
            group(2, "公开停用", type = 1, status = 0),
            group(3, "已加入私有", type = 2, joined = true),
            group(4, "自己管理私有", type = 2, owner = true),
            group(5, "不可用私有", type = 2),
        )

        assertEquals(mapOf(1 to "公开启用", 2 to "公开停用", 3 to "已加入私有", 4 to "自己管理私有", 5 to "不可用私有"), groupNamesById(groups))
        assertEquals(listOf(1, 3, 4, 5), activeGroups(groups).map(Group::id))
        assertEquals(listOf(1, 2, 3, 4), availableRadioGroups(groups).map(Group::id))
    }

    private fun device(id: Int, name: String, callsign: String, ssid: Int) = Device(
        id = id,
        name = name,
        callsign = callsign,
        ssid = ssid,
        model = 1,
        groupId = 1,
        online = true,
        enabled = true,
    )

    private fun group(
        id: Int,
        name: String,
        type: Int,
        status: Int = 1,
        joined: Boolean = false,
        owner: Boolean = false,
    ) = Group(
        id = id,
        name = name,
        type = type,
        status = status,
        joined = joined,
        owner = owner,
    )
}
