package cn.silverdragon.draarl.ui.screens

import cn.silverdragon.draarl.tools.RelayStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RelaySearchStateTest {
    @Test
    fun `result snapshot ignores control-only changes`() {
        val state = contentState()
        val controlOnlyUpdate = state.copy(
            location = "北京市 北京市",
            error = "network unavailable",
            queriedLocation = "上海市 上海市",
            cacheTime = 200L
        )

        assertEquals(state.resultsState(), controlOnlyUpdate.resultsState())
    }

    @Test
    fun `result snapshot follows list presentation changes`() {
        val state = contentState()

        assertNotEquals(state.resultsState(), state.copy(busy = true).resultsState())
        assertNotEquals(
            state.resultsState(),
            state.copy(queriedLocation = "").resultsState()
        )
        assertNotEquals(
            state.resultsState(),
            state.copy(relays = state.relays + relay(id = 2)).resultsState()
        )
    }

    private fun contentState() = RelaySearchContentState(
        location = "北京市",
        error = "",
        busy = false,
        queriedLocation = "北京市",
        cacheTime = 100L,
        relays = listOf(relay(id = 1))
    )

    private fun relay(id: Int) = RelayStation(
        id = id,
        name = "测试中继 $id",
        uplinkFrequency = "438.500",
        downlinkFrequency = "433.500",
        transmitTone = "88.5",
        receiveTone = "88.5",
        ownerCallsign = "BH1ABC",
        location = "北京市",
        status = 1,
        note = ""
    )
}
