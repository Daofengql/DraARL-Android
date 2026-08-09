package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.ClientResourceArtifact
import cn.silverdragon.draarl.data.ClientResourceArtifactTarget
import cn.silverdragon.draarl.data.ClientResourceDownload
import cn.silverdragon.draarl.data.ClientResourceManifest
import cn.silverdragon.draarl.data.ClientResourceManifestItem
import cn.silverdragon.draarl.data.ClientResourceRelease
import cn.silverdragon.draarl.data.ClientResourceSummary
import cn.silverdragon.draarl.data.PlatformInfo
import cn.silverdragon.draarl.data.Session
import org.json.JSONArray
import org.json.JSONObject

internal class UpdatesApiClient(private val requester: ApiJsonRequester, private val currentSession: () -> Session?) :
    UpdatesApi {
    override fun getPlatformInfo(): PlatformInfo {
        val data = requester.execute("GET", "/api/platform/info", requiresAuth = false).requireObject("data")
        return PlatformInfo(
            name = data.optStringClean("name").ifBlank { "DraARL 麟链" },
            version = data.optStringClean("version"),
            protocolVersion = data.optStringClean("protocol_version").ifBlank { "DraARLv1" }
        )
    }

    override fun getClientResourceManifest(query: ClientResourceManifestQuery): ClientResourceManifest {
        val path = buildString {
            append("/api/public/client-resources/manifest?platform=").append(urlEncode(query.platform))
            append("&arch=").append(urlEncode(query.arch))
            append("&channel=").append(urlEncode(query.channel))
            if (query.clientVersion.isNotBlank()) append("&client_version=").append(urlEncode(query.clientVersion))
            if (query.osVersion.isNotBlank()) append("&os_version=").append(urlEncode(query.osVersion))
            if (query.androidApi > 0) append("&android_api=").append(query.androidApi)
        }
        val data = requester.execute("GET", path, requiresAuth = false).requireObject("data")
        return data.toClientResourceManifest(currentBaseUrl())
    }

    override fun getClientResourceArtifactDownload(artifactId: Int): ClientResourceDownload {
        val data = requester.execute(
            "GET",
            "/api/public/client-resources/artifacts/${artifactId.coerceAtLeast(FIRST_VALID_ID)}/download",
            requiresAuth = false
        ).requireObject("data")
        return ClientResourceDownload(
            artifactId = data.optInt("artifact_id", artifactId),
            downloadUrl = optionalHttpsUrl(data.optStringClean("download_url"), currentBaseUrl()),
            urlExpiresAt = data.optStringClean("url_expires_at")
        )
    }

    private fun currentBaseUrl(): String = currentSession()?.baseUrl.orEmpty()
}

private fun JSONObject.toClientResourceManifest(baseUrl: String) = ClientResourceManifest(
    schemaVersion = optInt("schema_version"),
    serverVersion = optStringClean("server_version"),
    protocolVersion = optInt("protocol_version"),
    capabilities = optJSONArray("capabilities")?.strings().orEmpty(),
    resources = (optJSONArray("resources") ?: JSONArray()).objects().map { item ->
        ClientResourceManifestItem(
            resource = item.requireObject("resource").toClientResourceSummary(),
            release = item.requireObject("release").toClientResourceRelease(),
            artifacts = (item.optJSONArray("artifacts") ?: JSONArray())
                .objects()
                .map { it.toClientResourceArtifact(baseUrl) }
        )
    }
)

private fun JSONObject.toClientResourceSummary() = ClientResourceSummary(
    id = optInt("id"),
    resourceKey = optStringClean("resource_key"),
    name = optStringClean("name"),
    category = optStringClean("category"),
    required = optBoolean("required")
)

private fun JSONObject.toClientResourceRelease() = ClientResourceRelease(
    id = optInt("id"),
    version = optStringClean("version"),
    channel = optStringClean("channel"),
    title = optStringClean("title"),
    changelog = optStringClean("changelog"),
    forceUpdate = optBoolean("force_update"),
    minClientVersion = optStringClean("min_client_version"),
    minServerVersion = optStringClean("min_server_version"),
    requiredProtocolVersion = optInt("required_protocol_version"),
    requiredCapabilities = optJSONArray("required_capabilities")?.strings().orEmpty(),
    publishedAt = optStringClean("published_at")
)

private fun JSONObject.toClientResourceArtifact(baseUrl: String) = ClientResourceArtifact(
    id = optInt("id"),
    releaseId = optInt("release_id"),
    format = optStringClean("format"),
    runtime = optStringClean("runtime"),
    variant = optStringClean("variant"),
    buildNumber = optStringClean("build_number"),
    fileName = optStringClean("file_name"),
    fileSize = optLong("file_size"),
    sha256 = optStringClean("sha256"),
    contentSignature = optStringClean("content_signature"),
    signatureAlgorithm = optStringClean("signature_algorithm"),
    externalUrl = optionalHttpsUrl(optStringClean("external_url"), baseUrl),
    targets = (optJSONArray("targets") ?: JSONArray()).objects().map(JSONObject::toClientResourceArtifactTarget)
)

private fun JSONObject.toClientResourceArtifactTarget() = ClientResourceArtifactTarget(
    platform = optStringClean("platform"),
    arch = optStringClean("arch"),
    minOsVersion = optStringClean("min_os_version"),
    minAndroidApi = optInt("min_android_api")
)

private const val FIRST_VALID_ID = 1
