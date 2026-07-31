package cn.silverdragon.draarl.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun normalizesClientVersionForManifestQuery() {
        assertEquals("1.0.0-beta8", normalizeVersionForSemver("1.0-beta8"))
        assertEquals("1.0.0", compatibleClientVersionForResourceQuery("1.0.0-beta8"))
        assertEquals("1.2.0", normalizeVersionForSemver("1.2"))
    }

    @Test
    fun comparesSemverWithPrereleaseOrdering() {
        assertTrue(compareSemver("1.0.2", "1.0.0") > 0)
        assertTrue(compareSemver("1.0.0", "1.0.0-beta8") > 0)
        assertTrue(compareSemver("1.0.0-beta9", "1.0.0-beta8") > 0)
    }

    @Test
    fun mapsAndroidAbiToServerArch() {
        assertEquals("arm64", preferredAndroidResourceArch(arrayOf("arm64-v8a")))
        assertEquals("armv7", preferredAndroidResourceArch(arrayOf("armeabi-v7a")))
        assertEquals("x86_64", preferredAndroidResourceArch(arrayOf("x86_64")))
    }
}
