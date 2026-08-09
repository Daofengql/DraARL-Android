package cn.silverdragon.draarl.network

import cn.silverdragon.draarl.data.ClientResourceArtifact
import cn.silverdragon.draarl.data.ClientResourceArtifactTarget
import cn.silverdragon.draarl.data.ClientResourceDownload
import cn.silverdragon.draarl.data.ClientResourceManifest
import cn.silverdragon.draarl.data.ClientResourceManifestItem
import cn.silverdragon.draarl.data.ClientResourceRelease
import cn.silverdragon.draarl.data.ClientResourceSummary
import cn.silverdragon.draarl.data.PlatformInfo
import org.json.JSONArray
import org.json.JSONObject

internal data class PlatformInfoDto(val name: String, val version: String, val protocolVersion: String) {
    fun toDomain() = PlatformInfo(name, version, protocolVersion)
}

internal data class ClientResourceManifestDto(
    val schemaVersion: Int,
    val serverVersion: String,
    val protocolVersion: Int,
    val capabilities: List<String>,
    val resources: List<ClientResourceManifestItemDto>
) {
    fun toDomain(baseUrl: String) = ClientResourceManifest(
        schemaVersion,
        serverVersion,
        protocolVersion,
        capabilities,
        resources.map { it.toDomain(baseUrl) }
    )
}

internal data class ClientResourceManifestItemDto(
    val resource: ClientResourceSummaryDto,
    val release: ClientResourceReleaseDto,
    val artifacts: List<ClientResourceArtifactDto>
) {
    fun toDomain(baseUrl: String) = ClientResourceManifestItem(
        resource.toDomain(),
        release.toDomain(),
        artifacts.map { it.toDomain(baseUrl) }
    )
}

internal data class ClientResourceSummaryDto(
    val id: Int,
    val resourceKey: String,
    val name: String,
    val category: String,
    val required: Boolean
) {
    fun toDomain() = ClientResourceSummary(id, resourceKey, name, category, required)
}

internal data class ClientResourceReleaseDto(
    val id: Int,
    val version: String,
    val channel: String,
    val title: String,
    val changelog: String,
    val forceUpdate: Boolean,
    val minClientVersion: String,
    val minServerVersion: String,
    val requiredProtocolVersion: Int,
    val requiredCapabilities: List<String>,
    val publishedAt: String
) {
    fun toDomain() = ClientResourceRelease(
        id,
        version,
        channel,
        title,
        changelog,
        forceUpdate,
        minClientVersion,
        minServerVersion,
        requiredProtocolVersion,
        requiredCapabilities,
        publishedAt
    )
}

internal data class ClientResourceArtifactDto(
    val id: Int,
    val releaseId: Int,
    val format: String,
    val runtime: String,
    val variant: String,
    val buildNumber: String,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val contentSignature: String,
    val signatureAlgorithm: String,
    val externalUrl: String,
    val targets: List<ClientResourceArtifactTargetDto>
) {
    fun toDomain(baseUrl: String) = ClientResourceArtifact(
        id,
        releaseId,
        format,
        runtime,
        variant,
        buildNumber,
        fileName,
        fileSize,
        sha256,
        contentSignature,
        signatureAlgorithm,
        optionalHttpsUrl(externalUrl, baseUrl),
        targets.map(ClientResourceArtifactTargetDto::toDomain)
    )
}

internal data class ClientResourceArtifactTargetDto(
    val platform: String,
    val arch: String,
    val minOsVersion: String,
    val minAndroidApi: Int
) {
    fun toDomain() = ClientResourceArtifactTarget(platform, arch, minOsVersion, minAndroidApi)
}

internal data class ClientResourceDownloadDto(val artifactId: Int, val downloadUrl: String, val urlExpiresAt: String) {
    fun toDomain(baseUrl: String) = ClientResourceDownload(
        artifactId,
        optionalHttpsUrl(downloadUrl, baseUrl),
        urlExpiresAt
    )
}

internal object UpdateApiResponseMapper {
    fun platform(response: JSONObject): PlatformInfoDto {
        val data = response.requireObject("data")
        return PlatformInfoDto(
            name = data.optStringClean("name").ifBlank { "DraARL 麟链" },
            version = data.optStringClean("version"),
            protocolVersion = data.optStringClean("protocol_version").ifBlank { "DraARLv1" }
        )
    }

    fun manifest(response: JSONObject): ClientResourceManifestDto {
        val data = response.requireObject("data")
        return ClientResourceManifestDto(
            schemaVersion = data.requireInt("schema_version"),
            serverVersion = data.optStringClean("server_version"),
            protocolVersion = data.optInt("protocol_version"),
            capabilities = data.optJSONArray("capabilities")?.strings().orEmpty(),
            resources = (data.optJSONArray("resources") ?: JSONArray())
                .requireObjects("data.resources")
                .map(::manifestItem)
        )
    }

    fun download(response: JSONObject, defaultArtifactId: Int): ClientResourceDownloadDto {
        val data = response.requireObject("data")
        return ClientResourceDownloadDto(
            artifactId = data.optInt("artifact_id", defaultArtifactId),
            downloadUrl = data.optStringClean("download_url"),
            urlExpiresAt = data.optStringClean("url_expires_at")
        )
    }

    private fun manifestItem(data: JSONObject) = ClientResourceManifestItemDto(
        resource = resource(data.requireObject("resource")),
        release = release(data.requireObject("release")),
        artifacts = (data.optJSONArray("artifacts") ?: JSONArray())
            .requireObjects("data.resources[].artifacts")
            .map(::artifact)
    )

    private fun resource(data: JSONObject) = ClientResourceSummaryDto(
        id = data.requireInt("id"),
        resourceKey = data.optStringClean("resource_key"),
        name = data.optStringClean("name"),
        category = data.optStringClean("category"),
        required = data.optBoolean("required")
    )

    private fun release(data: JSONObject) = ClientResourceReleaseDto(
        id = data.requireInt("id"),
        version = data.optStringClean("version"),
        channel = data.optStringClean("channel"),
        title = data.optStringClean("title"),
        changelog = data.optStringClean("changelog"),
        forceUpdate = data.optBoolean("force_update"),
        minClientVersion = data.optStringClean("min_client_version"),
        minServerVersion = data.optStringClean("min_server_version"),
        requiredProtocolVersion = data.optInt("required_protocol_version"),
        requiredCapabilities = data.optJSONArray("required_capabilities")?.strings().orEmpty(),
        publishedAt = data.optStringClean("published_at")
    )

    private fun artifact(data: JSONObject) = ClientResourceArtifactDto(
        id = data.requireInt("id"),
        releaseId = data.optInt("release_id"),
        format = data.optStringClean("format"),
        runtime = data.optStringClean("runtime"),
        variant = data.optStringClean("variant"),
        buildNumber = data.optStringClean("build_number"),
        fileName = data.optStringClean("file_name"),
        fileSize = data.optLong("file_size"),
        sha256 = data.optStringClean("sha256"),
        contentSignature = data.optStringClean("content_signature"),
        signatureAlgorithm = data.optStringClean("signature_algorithm"),
        externalUrl = data.optStringClean("external_url"),
        targets = (data.optJSONArray("targets") ?: JSONArray())
            .requireObjects("data.resources[].artifacts[].targets")
            .map(::target)
    )

    private fun target(data: JSONObject) = ClientResourceArtifactTargetDto(
        platform = data.optStringClean("platform"),
        arch = data.optStringClean("arch"),
        minOsVersion = data.optStringClean("min_os_version"),
        minAndroidApi = data.optInt("min_android_api")
    )
}
