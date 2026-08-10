import java.util.Properties
import java.util.zip.ZipFile

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
        versionCode = 8
        versionName = "2.0.0-alpha1"
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
        ndk {
            abiFilters += "arm64-v8a"
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
    baseline = rootProject.file("config/detekt/baseline.xml")
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
    dependsOn("assembleRelease")

    val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val releaseMapping = layout.buildDirectory.dir("outputs/mapping/release")
    val generatedProfiles = layout.projectDirectory.dir("src/release/generated/baselineProfiles")
    inputs.file(releaseApk)
    inputs.dir(releaseMapping)
    inputs.dir(generatedProfiles)

    doLast {
        fun verify(condition: Boolean, message: String) {
            if (!condition) throw GradleException(message)
        }

        val apk = releaseApk.get().asFile
        verify(apk.isFile, "Release APK is missing: ${apk.absolutePath}")
        val maxApkBytes = 36L * 1024L * 1024L
        verify(
            apk.length() <= maxApkBytes,
            "Release APK is ${apk.length()} bytes, above the $maxApkBytes byte regression limit"
        )

        ZipFile(apk).use { zip ->
            val entries = zip.entries().asSequence().associateBy { it.name }
            val requiredEntries = listOf(
                "AndroidManifest.xml",
                "assets/dexopt/baseline.prof",
                "assets/dexopt/baseline.profm",
                "lib/arm64-v8a/libAMapSDK_MAP_v10_0_600.so",
                "lib/arm64-v8a/libdraarl_rnnoise.so"
            )
            requiredEntries.forEach { name ->
                val entry = entries[name]
                verify(
                    entry != null && entry.size > 0L,
                    "Release APK entry is missing or empty: $name"
                )
            }
            verify(
                entries.keys.any {
                    it.matches(Regex("classes\\d*\\.dex"))
                },
                "Release APK contains no DEX files"
            )
            val unexpectedAbis = entries.keys
                .filter { it.startsWith("lib/") }
                .filterNot { it.startsWith("lib/arm64-v8a/") }
            verify(
                unexpectedAbis.isEmpty(),
                "Release APK contains unexpected native ABIs: $unexpectedAbis"
            )
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

        logger.lifecycle("Verified Release APK: ${apk.length()} bytes (${apk.absolutePath})")
    }
}
