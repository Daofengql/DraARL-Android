package cn.silverdragon.draarl.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.silverdragon.draarl.data.ClientResourceArtifact
import cn.silverdragon.draarl.data.ClientResourceKeys
import cn.silverdragon.draarl.data.ClientResourceManifest
import cn.silverdragon.draarl.data.ClientResourceRelease
import cn.silverdragon.draarl.network.ApiClient
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class AppUpdateStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    INSTALL_PERMISSION_REQUIRED,
    ERROR,
}

data class AppUpdateInfo(
    val version: String,
    val title: String,
    val changelog: String,
    val forceUpdate: Boolean,
    val artifact: ClientResourceArtifact,
    val currentVersionName: String,
    val currentVersion: String,
) {
    val displayTitle: String get() = title.ifBlank { "DraARL $version" }
}

class AppUpdateInstallPermissionException : Exception("需要允许本应用安装更新包")

class AppUpdateServerContractException(message: String) : Exception(message)

class AppUpdateManager(
    private val context: Context,
    private val api: ApiClient,
) {
    private val appContext = context.applicationContext

    val currentVersionName: String get() = resolveCurrentVersionName(appContext)
    val currentVersion: String get() = normalizeVersionForSemver(currentVersionName)

    fun checkForUpdate(channel: String = "stable"): AppUpdateInfo? {
        val semver = currentVersion
        val manifest = api.getClientResourceManifest(
            platform = "android",
            arch = preferredAndroidResourceArch(),
            clientVersion = compatibleClientVersionForResourceQuery(semver),
            channel = channel,
            androidApi = Build.VERSION.SDK_INT,
        )
        if (manifest.schemaVersion != CLIENT_RESOURCE_SCHEMA_VERSION) {
            error("服务器资源清单版本暂不支持")
        }
        val item = manifest.resources.firstOrNull { resource ->
            resource.resource.resourceKey == ClientResourceKeys.APP_DRAARL &&
                resource.resource.category == "application"
        } ?: return null
        if (!item.release.forceUpdate && compareSemver(item.release.version, semver) <= 0) return null
        requireCompatibleAppUpdateServerContract(manifest, item.release)
        val artifact = item.artifacts.firstOrNull { artifact ->
            artifact.format == "apk" && (artifact.runtime.isBlank() || artifact.runtime == "android")
        } ?: item.artifacts.firstOrNull { it.format == "apk" }
            ?: return null
        return AppUpdateInfo(
            version = item.release.version,
            title = item.release.title,
            changelog = item.release.changelog,
            forceUpdate = item.release.forceUpdate,
            artifact = artifact,
            currentVersionName = currentVersionName,
            currentVersion = semver,
        )
    }

    fun downloadUpdate(update: AppUpdateInfo, onProgress: (Float) -> Unit = {}): File {
        val download = api.getClientResourceArtifactDownload(update.artifact.id)
        val url = download.downloadUrl.ifBlank { update.artifact.externalUrl }
        if (url.isBlank()) error("服务器没有返回下载地址")
        val target = updateFile(update)
        if (target.isFile && verifyDownloadedFile(target, update.artifact)) {
            onProgress(1f)
            return target
        }
        return downloadToFile(url, target, update.artifact, onProgress)
    }

    fun installUpdate(apk: File) {
        if (!apk.isFile) error("更新包不存在")
        if (!canRequestPackageInstalls()) {
            throw AppUpdateInstallPermissionException()
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun canRequestPackageInstalls(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    private fun updateFile(update: AppUpdateInfo): File {
        val safeVersion = update.version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val name = "draarl-$safeVersion-${update.artifact.id}.apk"
        return File(File(appContext.filesDir, UPDATE_DIRECTORY).apply { mkdirs() }, name)
    }

    private fun downloadToFile(
        url: String,
        target: File,
        artifact: ClientResourceArtifact,
        onProgress: (Float) -> Unit,
    ): File {
        target.parentFile?.mkdirs()
        val part = File(target.absolutePath + PART_SUFFIX)
        val expectedSize = artifact.fileSize
        val existingBytes = part.takeIf { it.isFile }?.length()?.takeIf { expectedSize <= 0L || it in 1 until expectedSize } ?: 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
            readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            requestMethod = "GET"
            if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("下载更新包失败 ($status)")
            val append = existingBytes > 0L && status == HttpURLConnection.HTTP_PARTIAL
            if (!append && part.exists() && !part.delete()) error("无法重置下载缓存")
            val startBytes = if (append) existingBytes else 0L
            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = startBytes
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (expectedSize > 0L) onProgress((written.toFloat() / expectedSize).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!verifyDownloadedFile(part, artifact)) {
            part.delete()
            error("更新包校验失败")
        }
        if (target.exists() && !target.delete()) error("无法更新本地安装包")
        if (!part.renameTo(target)) error("无法保存更新包")
        onProgress(1f)
        return target
    }

    private fun verifyDownloadedFile(file: File, artifact: ClientResourceArtifact): Boolean {
        if (!file.isFile) return false
        if (artifact.fileSize > 0L && file.length() != artifact.fileSize) return false
        if (artifact.sha256.isBlank()) return true
        return file.sha256().equals(artifact.sha256, ignoreCase = true)
    }

    companion object {
        private const val CLIENT_RESOURCE_SCHEMA_VERSION = 1
        private const val UPDATE_DIRECTORY = "client_updates"
        private const val PART_SUFFIX = ".part"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 15_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 60_000
    }
}

internal fun preferredAndroidResourceArch(abis: Array<String> = Build.SUPPORTED_ABIS): String {
    val ordered = abis.map(String::lowercase)
    return when {
        "arm64-v8a" in ordered || "aarch64" in ordered -> "arm64"
        "armeabi-v7a" in ordered || "arm32" in ordered -> "armv7"
        "x86_64" in ordered || "amd64" in ordered -> "x86_64"
        "x86" in ordered -> "x86"
        else -> ordered.firstOrNull().orEmpty().ifBlank { "arm64" }
    }
}

internal fun normalizeVersionForSemver(value: String): String {
    val trimmed = value.trim().removePrefix("v")
    if (trimmed.matches(SEMVER_REGEX)) return trimmed
    val twoPart = Regex("""^(\d+)\.(\d+)([-+].+)?$""").matchEntire(trimmed)
    if (twoPart != null) {
        return "${twoPart.groupValues[1]}.${twoPart.groupValues[2]}.0${twoPart.groupValues[3]}"
    }
    val numericPrefix = Regex("""^(\d+)(?:\.(\d+))?(?:\.(\d+))?""").find(trimmed)
    if (numericPrefix != null) {
        val major = numericPrefix.groupValues[1]
        val minor = numericPrefix.groupValues[2].ifBlank { "0" }
        val patch = numericPrefix.groupValues[3].ifBlank { "0" }
        return "$major.$minor.$patch"
    }
    return "0.0.0"
}

internal fun compatibleClientVersionForResourceQuery(version: String): String =
    normalizeVersionForSemver(version).substringBefore('-').substringBefore('+')

internal fun requireCompatibleAppUpdateServerContract(
    manifest: ClientResourceManifest,
    release: ClientResourceRelease,
) {
    val minServerVersion = release.minServerVersion.trim()
    if (minServerVersion.isNotEmpty()) {
        val serverVersion = strictSemverOrNull(manifest.serverVersion)
            ?: throw AppUpdateServerContractException("服务器资源清单未声明可比较的服务端版本")
        if (strictSemverOrNull(minServerVersion) == null) {
            throw AppUpdateServerContractException("客户端更新声明了无效的最低服务端版本")
        }
        if (compareSemver(serverVersion, minServerVersion) < 0) {
            throw AppUpdateServerContractException("服务器版本 $serverVersion 低于更新要求 $minServerVersion")
        }
    }
    if (release.requiredProtocolVersion < 0) {
        throw AppUpdateServerContractException("客户端更新声明了无效的协议版本")
    }
    if (manifest.protocolVersion < release.requiredProtocolVersion) {
        throw AppUpdateServerContractException(
            "服务器幽灵协议版本 ${manifest.protocolVersion} 低于更新要求 ${release.requiredProtocolVersion}",
        )
    }
    val requiredCapabilities = release.requiredCapabilities.map { it.trim() }.filter(String::isNotEmpty).toSet()
    if (requiredCapabilities.isNotEmpty() && release.requiredProtocolVersion == 0) {
        throw AppUpdateServerContractException("客户端更新的协议能力约束无效")
    }
    val availableCapabilities = manifest.capabilities.map { it.trim().lowercase() }.filter(String::isNotEmpty).toSet()
    val missingCapabilities = requiredCapabilities.map { it.lowercase() }.filterNot(availableCapabilities::contains).sorted()
    if (missingCapabilities.isNotEmpty()) {
        throw AppUpdateServerContractException("服务器缺少更新所需能力：${missingCapabilities.joinToString()}")
    }
}

private fun strictSemverOrNull(value: String): String? {
    val trimmed = value.trim().let { version ->
        if (version.startsWith("v", ignoreCase = true)) version.drop(1) else version
    }
    return trimmed.takeIf(STRICT_SEMVER_REGEX::matches)
}

internal fun compareSemver(left: String, right: String): Int {
    val leftVersion = ParsedSemver.parse(left)
    val rightVersion = ParsedSemver.parse(right)
    return leftVersion.compareTo(rightVersion)
}

private data class ParsedSemver(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String>,
) : Comparable<ParsedSemver> {
    override fun compareTo(other: ParsedSemver): Int {
        compareValuesBy(this, other, ParsedSemver::major, ParsedSemver::minor, ParsedSemver::patch)
            .takeIf { it != 0 }
            ?.let { return it }
        if (preRelease.isEmpty() && other.preRelease.isEmpty()) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        val count = maxOf(preRelease.size, other.preRelease.size)
        for (index in 0 until count) {
            val left = preRelease.getOrNull(index) ?: return -1
            val right = other.preRelease.getOrNull(index) ?: return 1
            comparePreReleaseIdentifier(left, right).takeIf { it != 0 }?.let { return it }
        }
        return 0
    }

    companion object {
        fun parse(value: String): ParsedSemver {
            val normalized = normalizeVersionForSemver(value)
            val match = SEMVER_REGEX.matchEntire(normalized) ?: return ParsedSemver(0, 0, 0, emptyList())
            return ParsedSemver(
                major = match.groupValues[1].toIntOrNull() ?: 0,
                minor = match.groupValues[2].toIntOrNull() ?: 0,
                patch = match.groupValues[3].toIntOrNull() ?: 0,
                preRelease = match.groupValues[4].takeIf(String::isNotBlank)?.split('.') ?: emptyList(),
            )
        }
    }
}

private fun comparePreReleaseIdentifier(left: String, right: String): Int {
    val leftNumber = left.toIntOrNull()
    val rightNumber = right.toIntOrNull()
    return when {
        leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
        leftNumber != null -> -1
        rightNumber != null -> 1
        else -> left.compareTo(right)
    }
}

private fun resolveCurrentVersionName(context: Context): String {
    return runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName.orEmpty()
    }.getOrDefault("0.0.0").ifBlank { "0.0.0" }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private val SEMVER_REGEX = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")
private val STRICT_SEMVER_REGEX = Regex(
    """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*))*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$""",
)
