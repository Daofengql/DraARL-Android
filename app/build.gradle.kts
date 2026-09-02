import java.util.Properties
import java.util.zip.ZipFile

private val supportedAbis = listOf("armeabi-v7a", "arm64-v8a")
private val amapNativeAbis = setOf("armeabi-v7a", "arm64-v8a")
private val amapNativeLibrary = "libAMapSDK_MAP_v10_0_600.so"
private val rnnoiseNativeLibrary = "libdraarl_rnnoise.so"

private data class AbiPackaging(
    val requested: String,
    val abis: List<String>,
    val splitApk: Boolean,
    val universalApk: Boolean
)

private fun normalizeAbiName(value: String): String = when (value.trim().lowercase()) {
    "v7", "32", "arm32", "armeabi", "armeabi-v7a" -> "armeabi-v7a"
    "64", "arm64", "arm64-v8a" -> "arm64-v8a"
    else -> value.trim()
}

private fun parseAbiPackaging(rawValue: String): AbiPackaging {
    val requested = rawValue.trim().ifBlank { "arm64-v8a" }
    val normalized = requested.lowercase()
    if (normalized == "all") {
        return AbiPackaging(requested, supportedAbis, splitApk = true, universalApk = true)
    }
    if (normalized == "universal" || normalized == "fat") {
        return AbiPackaging(requested, supportedAbis, splitApk = false, universalApk = false)
    }

    val abis = requested.split(',')
        .map(::normalizeAbiName)
        .filter(String::isNotBlank)
        .distinct()
    require(abis.isNotEmpty() && abis.all(supportedAbis::contains)) {
        "Unsupported ABI '$rawValue'. Use one of ${supportedAbis.joinToString()}, universal, all, or a comma-separated list."
    }
    return AbiPackaging(
        requested = requested,
        abis = abis,
        splitApk = abis.size > 1,
        universalApk = false
    )
}

private val requestedAbiProperty = providers.gradleProperty("abi").orNull
private val requestedConvenienceAbi = when {
    gradle.startParameter.taskNames.any {
        it.substringAfterLast(':') == "assembleReleaseAll"
    } -> "all"

    gradle.startParameter.taskNames.any {
        it.substringAfterLast(':') == "assembleReleaseUniversal"
    } -> "universal"

    else -> null
}
private val abiPackaging: AbiPackaging = parseAbiPackaging(
    requestedAbiProperty ?: requestedConvenienceAbi ?: "arm64-v8a"
)
private val abiPackagingRequested = abiPackaging.requested
private val abiPackagingAbis = abiPackaging.abis.toList()
private val abiPackagingSplitApk = abiPackaging.splitApk
private val abiPackagingUniversalApk = abiPackaging.universalApk
private val amapNativeAbiNames = amapNativeAbis.toList()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.screenshot)
}

val releaseSigning = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use(::load)
}
val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use(::load)
}
val amapApiKey = providers.gradleProperty("AMAP_API_KEY").orNull
    ?: localProperties.getProperty("AMAP_API_KEY").orEmpty()
val enableComposeCompilerReports = providers.gradleProperty("enableComposeCompilerReports")
    .map(String::toBoolean)
    .getOrElse(false)
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !releaseSigning.getProperty(it).isNullOrBlank() }

if (enableComposeCompilerReports) {
    composeCompiler {
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    namespace = "cn.silverdragon.draarl"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "cn.silverdragon.draarl"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "2.0.0-alpha4"
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
        ndk {
            abiFilters += abiPackaging.abis
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    ndkVersion = "28.2.13676358"

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
        }
    }
    splits {
        abi {
            isEnable = abiPackaging.splitApk
            reset()
            include(*abiPackaging.abis.toTypedArray())
            isUniversalApk = abiPackaging.universalApk
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

detekt {
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline-debug.xml")
    buildUponDefaultConfig = true
    parallel = true
    basePath.set(rootProject.projectDir)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    exclude("**/cpp/third_party/rnnoise/**", "**/build/**")
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    exclude("**/cpp/third_party/rnnoise/**", "**/build/**")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.okhttp)
    implementation(libs.concentus)
    implementation(libs.amap.map)
    implementation(libs.amap.search)
    baselineProfile(project(":baselineprofile"))
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.okhttp.mockwebserver)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

baselineProfile {
    dexLayoutOptimization = true
}

val verifyReleaseArtifact by tasks.registering {
    group = "verification"
    description =
        "Verifies the optimized Release APK, R8 reports, JNI keep rules, and packaged Baseline Profile."
    notCompatibleWithConfigurationCache(
        "Release artifact verification enumerates and inspects generated APK ZIP entries."
    )
    dependsOn("assembleRelease")

    val releaseOutputDirectory = layout.buildDirectory.dir("outputs/apk/release")
    val releaseMapping = layout.buildDirectory.dir("outputs/mapping/release")
    val generatedProfiles = layout.projectDirectory.dir("src/release/generated/baselineProfiles")
    inputs.dir(releaseOutputDirectory)
    inputs.dir(releaseMapping)
    inputs.dir(generatedProfiles)

    doLast {
        fun verify(condition: Boolean, message: String) {
            if (!condition) throw GradleException(message)
        }

        val outputDirectory = releaseOutputDirectory.get().asFile
        val expectedApks = when {
            abiPackagingSplitApk && abiPackagingUniversalApk ->
                listOf("app-universal-release.apk" to abiPackagingAbis) +
                    abiPackagingAbis.map { abi -> "app-$abi-release.apk" to listOf(abi) }

            abiPackagingSplitApk ->
                abiPackagingAbis.map { abi -> "app-$abi-release.apk" to listOf(abi) }

            else -> listOf("app-release.apk" to abiPackagingAbis)
        }
        val apks = expectedApks.map { (name, _) -> outputDirectory.resolve(name) }
        verify(
            apks.all(File::isFile),
            "Expected Release APK(s) for abi=$abiPackagingRequested " +
                "in ${outputDirectory.absolutePath}: " +
                expectedApks.joinToString { (name, _) -> name }
        )

        expectedApks.forEach { (name, expectedAbis) ->
            val apk = outputDirectory.resolve(name)
            ZipFile(apk).use { zip ->
                val entries = zip.entries().asSequence().associateBy { it.name }
                val packagedAbis = entries.keys
                    .filter { it.startsWith("lib/") }
                    .map { it.removePrefix("lib/").substringBefore('/') }
                    .distinct()
                val requiredEntries = listOf(
                    "AndroidManifest.xml",
                    "assets/dexopt/baseline.prof",
                    "assets/dexopt/baseline.profm"
                ) + packagedAbis.map { abi -> "lib/$abi/$rnnoiseNativeLibrary" } +
                    packagedAbis.filter(amapNativeAbiNames::contains).map { abi ->
                        "lib/$abi/$amapNativeLibrary"
                    }
                requiredEntries.forEach { name ->
                    val entry = entries[name]
                    verify(
                        entry != null && entry.size > 0L,
                        "Release APK ${apk.name} entry is missing or empty: $name"
                    )
                }
                verify(packagedAbis.isNotEmpty(), "Release APK ${apk.name} contains no native ABI")
                verify(
                    entries.keys.any { it.matches(Regex("classes\\d*\\.dex")) },
                    "Release APK ${apk.name} contains no DEX files"
                )
                verify(
                    packagedAbis.toSet() == expectedAbis.toSet(),
                    "Release APK ${apk.name} contains $packagedAbis; expected $expectedAbis"
                )
                val missingAmapAbis = packagedAbis.filter(
                    amapNativeAbiNames::contains
                ).filter { abi ->
                    "lib/$abi/$amapNativeLibrary" !in entries
                }
                verify(
                    missingAmapAbis.isEmpty(),
                    "Release APK ${apk.name} is missing AMap native libraries for: $missingAmapAbis"
                )
            }
        }

        val mappingDirectory = releaseMapping.get().asFile
        val reports =
            listOf("mapping.txt", "seeds.txt", "usage.txt", "resources.txt", "configuration.txt")
        reports.forEach { name ->
            val report = mappingDirectory.resolve(name)
            verify(
                report.isFile && report.length() > 0L,
                "R8 report is missing or empty: ${report.absolutePath}"
            )
        }

        val mapping = mappingDirectory.resolve("mapping.txt").readText()
        val rnnoiseClass = "cn.silverdragon.draarl.radio.RnnoiseNative"
        verify(
            "$rnnoiseClass -> $rnnoiseClass:" in mapping,
            "R8 renamed the RNNoise JNI owner class"
        )
        verify(
            "com.amap.api.maps.MapView -> com.amap.api.maps.MapView:" in mapping,
            "R8 renamed the AMap MapView reflection/JNI boundary"
        )

        val seeds = mappingDirectory.resolve("seeds.txt").readText()
        listOf("nativeCreate", "nativeDestroy", "nativeReset", "nativeProcess").forEach { method ->
            verify(
                seeds.lineSequence().any { line ->
                    line.startsWith("cn.silverdragon.draarl.radio.RnnoiseNative:") && method in line
                },
                "R8 seeds do not retain RnnoiseNative.$method"
            )
        }

        val profileDirectory = generatedProfiles.asFile
        listOf("baseline-prof.txt", "startup-prof.txt").forEach { name ->
            val profile = profileDirectory.resolve(name)
            verify(profile.isFile, "Generated profile is missing: ${profile.absolutePath}")
            verify(
                profile.useLines { lines -> lines.count(String::isNotBlank) } >= 1_000,
                "Generated profile has fewer than 1,000 non-blank rules: ${profile.absolutePath}"
            )
        }

        logger.lifecycle(
            "Verified Release APK(s) for abi=$abiPackagingRequested: " +
                apks.joinToString { "${it.name} (${it.length()} bytes)" }
        )
    }
}

tasks.register("assembleReleaseUniversal") {
    group = "build"
    description = "Builds one Release APK containing all supported ABIs."
    dependsOn("assembleRelease")
}

tasks.register("assembleReleaseAll") {
    group = "build"
    description = "Builds one split Release APK per ABI plus a universal APK."
    dependsOn("assembleRelease")
}
