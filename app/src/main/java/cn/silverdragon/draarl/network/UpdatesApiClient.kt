package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.ClientResourceDownload
import cn.silverdragon.draarl.data.ClientResourceManifest
import cn.silverdragon.draarl.data.PlatformInfo
import cn.silverdragon.draarl.data.Session

internal class UpdatesApiClient(private val requester: ApiJsonRequester, private val currentSession: () -> Session?) :
    UpdatesApi {
    override fun getPlatformInfo(): PlatformInfo = requester.executeMapped(
        "GET",
        "/api/platform/info",
        requiresAuth = false,
        mapper = UpdateApiResponseMapper::platform
    ).toDomain()

    override fun getClientResourceManifest(query: ClientResourceManifestQuery): ClientResourceManifest {
        val path = buildString {
            append("/api/public/client-resources/manifest?platform=").append(urlEncode(query.platform))
            append("&arch=").append(urlEncode(query.arch))
            append("&channel=").append(urlEncode(query.channel))
            if (query.clientVersion.isNotBlank()) append("&client_version=").append(urlEncode(query.clientVersion))
            if (query.osVersion.isNotBlank()) append("&os_version=").append(urlEncode(query.osVersion))
            if (query.androidApi > 0) append("&android_api=").append(query.androidApi)
        }
        return requester.executeMapped(
            "GET",
            path,
            requiresAuth = false,
            mapper = UpdateApiResponseMapper::manifest
        ).toDomain(currentBaseUrl())
    }

    override fun getClientResourceArtifactDownload(artifactId: Int): ClientResourceDownload {
        val safeArtifactId = artifactId.coerceAtLeast(FIRST_VALID_ID)
        return requester.executeMapped(
            "GET",
            "/api/public/client-resources/artifacts/$safeArtifactId/download",
            requiresAuth = false
        ) { UpdateApiResponseMapper.download(it, artifactId) }.toDomain(currentBaseUrl())
    }

    private fun currentBaseUrl(): String = currentSession()?.baseUrl.orEmpty()
}

private const val FIRST_VALID_ID = 1
