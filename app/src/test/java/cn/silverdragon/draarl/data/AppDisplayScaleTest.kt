package cn.silverdragon.draarl.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDisplayScaleTest {
    @Test
    fun compactScaleMatchesReferencePhone() {
        assertEquals(1280f / 480f, appDensityFor(1280f, AppDisplayScale.COMPACT), 0.001f)
    }

    @Test
    fun densityHasASafeLowerBoundForTinyWindows() {
        assertEquals(0.5f, appDensityFor(120f, AppDisplayScale.COMPACT), 0f)
    }
}
