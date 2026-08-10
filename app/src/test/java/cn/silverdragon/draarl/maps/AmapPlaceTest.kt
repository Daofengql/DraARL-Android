package cn.silverdragon.draarl.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AmapPlaceTest {
    @Test
    fun `POI id keeps places at the same coordinate distinct`() {
        val first = place(id = "poi-a", name = "A 入口")
        val second = place(id = "poi-b", name = "B 入口")

        assertNotEquals(first.stableKey, second.stableKey)
    }

    @Test
    fun `fallback key includes place text when POI id is unavailable`() {
        val first = place(name = "A 入口", address = "东门")
        val second = place(name = "B 入口", address = "西门")

        assertNotEquals(first.stableKey, second.stableKey)
    }

    @Test
    fun `fallback key is unambiguous when place text contains separators`() {
        val first = place(name = "A|B", address = "C")
        val second = place(name = "A", address = "B|C")

        assertNotEquals(first.stableKey, second.stableKey)
        assertNotEquals(place(id = first.stableKey, name = "remote").stableKey, first.stableKey)
    }

    @Test
    fun `stable key removes exact duplicate search results`() {
        val place = place(name = "应急通信中心", address = "值守楼")

        assertEquals(listOf(place), listOf(place, place.copy()).distinctBy(AmapPlace::stableKey))
    }

    private fun place(id: String = "", name: String, address: String = "浙江省杭州市") = AmapPlace(
        id = id,
        name = name,
        address = address,
        latitude = 30.2741,
        longitude = 120.1551
    )
}
