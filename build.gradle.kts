// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}

spotless {
    ratchetFrom(providers.gradleProperty("spotlessRatchetFrom").getOrElse("origin/main"))

    kotlin {
        target("app/src/**/*.kt")
        targetExclude("app/src/main/cpp/third_party/rnnoise/**", "**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                "ktlint_code_style" to "android_studio",
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                "max_line_length" to "120"
            )
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "gradle/**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf("ktlint_code_style" to "android_studio")
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("projectText") {
        target("*.md", "docs/**/*.md", ".github/**/*.yml", ".github/**/*.yaml")
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
