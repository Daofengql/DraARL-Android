package cn.silverdragon.draarl.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateApiDtosTest {
    @Test
    fun `platform parser supplies compatibility names`() {
        val platform = UpdateApiResponseMapper.platform(JSONObject("""{"data":{"version":"2.0.0"}}"""))

        assertEquals("DraARL 麟链", platform.name)
        assertEquals("2.0.0", platform.version)
        assertEquals("DraARLv1", platform.protocolVersion)
    }

    @Test
    fun `download parser keeps requested artifact and resolves a relative url`() {
        val download = UpdateApiResponseMapper.download(
            JSONObject("""{"data":{"download_url":"/downloads/app.apk"}}"""),
            defaultArtifactId = 42
        ).toDomain("https://api.example.test")
        val unsafe = UpdateApiResponseMapper.download(
            JSONObject("""{"data":{"artifact_id":9,"download_url":"http://cdn.example.test/app.apk"}}"""),
            defaultArtifactId = 42
        ).toDomain("https://api.example.test")

        assertEquals(42, download.artifactId)
        assertEquals("https://api.example.test/downloads/app.apk", download.downloadUrl)
        assertEquals("", unsafe.downloadUrl)
    }

    @Test
    fun `manifest parser rejects a release without its required identity`() {
        val error = assertThrows(ApiException::class.java) {
            UpdateApiResponseMapper.manifest(
                JSONObject(
                    """{"data":{"schema_version":1,"resources":[{"resource":{"id":1},"release":{},"artifacts":[]}]}}"""
                )
            )
        }

        assertEquals(500, error.code)
        assertTrue(error.message.contains("id"))
    }
}
