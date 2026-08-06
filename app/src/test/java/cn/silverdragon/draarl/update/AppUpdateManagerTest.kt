package cn.silverdragon.draarl.update

import cn.silverdragon.draarl.data.ClientResourceManifest
import cn.silverdragon.draarl.data.ClientResourceRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun acceptsCompatibleServerContract() {
        requireCompatibleAppUpdateServerContract(
            manifest = manifest(),
            release = release(),
        )
    }

    @Test
    fun rejectsUnknownOrOldServerVersion() {
        assertThrows(AppUpdateServerContractException::class.java) {
            requireCompatibleAppUpdateServerContract(manifest(serverVersion = "dev"), release())
        }
        assertThrows(AppUpdateServerContractException::class.java) {
            requireCompatibleAppUpdateServerContract(manifest(serverVersion = "1.0.9"), release())
        }
    }

    @Test
    fun rejectsOldProtocolAndMissingCapabilities() {
        assertThrows(AppUpdateServerContractException::class.java) {
            requireCompatibleAppUpdateServerContract(manifest(protocolVersion = 0), release())
        }
        assertThrows(AppUpdateServerContractException::class.java) {
            requireCompatibleAppUpdateServerContract(manifest(capabilities = listOf("multi_receive_v1")), release())
        }
    }

    @Test
    fun permitsSchemaOneReleaseWithoutServerRequirements() {
        requireCompatibleAppUpdateServerContract(
            manifest = ClientResourceManifest(schemaVersion = 1, resources = emptyList()),
            release = ClientResourceRelease(id = 1, version = "1.0.3", channel = "stable"),
        )
    }

    private fun manifest(
        serverVersion: String = "1.1.5-alpha7",
        protocolVersion: Int = 1,
        capabilities: List<String> = listOf("multi_receive_v1", "source_group_v1"),
    ) = ClientResourceManifest(
        schemaVersion = 1,
        serverVersion = serverVersion,
        protocolVersion = protocolVersion,
        capabilities = capabilities,
        resources = emptyList(),
    )

    private fun release() = ClientResourceRelease(
        id = 1,
        version = "1.0.3",
        channel = "stable",
        minServerVersion = "1.1.5-alpha7",
        requiredProtocolVersion = 1,
        requiredCapabilities = listOf("multi_receive_v1", "source_group_v1"),
    )
}
