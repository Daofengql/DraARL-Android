package cn.silverdragon.draarl.data

object ClientResourceKeys {
    const val APP_DRAARL = "app/draarl"
    const val MODEL_DENOISE = "model/denoise"
}

data class ClientResourceSummary(
    val id: Int,
    val resourceKey: String,
    val name: String,
    val category: String = "",
    val required: Boolean = false,
)

data class ClientResourceRelease(
    val id: Int,
    val version: String,
    val channel: String,
    val title: String = "",
    val changelog: String = "",
    val forceUpdate: Boolean = false,
    val minClientVersion: String = "",
    val publishedAt: String = "",
)

data class ClientResourceArtifactTarget(
    val platform: String,
    val arch: String,
    val minOsVersion: String = "",
    val minAndroidApi: Int = 0,
)

data class ClientResourceArtifact(
    val id: Int,
    val releaseId: Int,
    val format: String,
    val runtime: String = "",
    val variant: String = "",
    val buildNumber: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val sha256: String = "",
    val contentSignature: String = "",
    val signatureAlgorithm: String = "",
    val externalUrl: String = "",
    val targets: List<ClientResourceArtifactTarget> = emptyList(),
)

data class ClientResourceManifestItem(
    val resource: ClientResourceSummary,
    val release: ClientResourceRelease,
    val artifacts: List<ClientResourceArtifact>,
)

data class ClientResourceManifest(
    val schemaVersion: Int,
    val resources: List<ClientResourceManifestItem>,
)

data class ClientResourceDownload(
    val artifactId: Int,
    val downloadUrl: String,
    val urlExpiresAt: String = "",
)
