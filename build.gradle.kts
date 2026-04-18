plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// Shared config across modules
allprojects {
    group = "com.dmzs"
}

// ktlint runs report-only during Sprint 1 so style nits don't gate the foundation
// from landing. Configured per-module (see each build.gradle.kts) because the
// KtlintExtension type isn't on the root buildscript classpath. Sprint 5 (harden +
// Play submission) flips `ignoreFailures` back to false.
