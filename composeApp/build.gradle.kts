import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val appVersion: String = providers.gradleProperty("DATAWATCH_APP_VERSION").get()
val appVersionCode: Int =
    providers.gradleProperty("DATAWATCH_APP_VERSION_CODE").get().toInt()

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(project(":auto"))
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.process)
                implementation(libs.androidx.navigation.compose)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                // FCM removed in v0.33.17 (B6). Self-hosted ntfy is the push path;
                // see `push/PushRegistrationCoordinator.kt` kdoc for rationale.
                implementation(libs.androidx.biometric)
                implementation(libs.kotlinx.coroutines.android)
                // Wearable Data Layer — phone publishes session-count
                // DataItems that the paired Wear companion subscribes to
                // via its own DataClient. Watch never stores the bearer
                // token; phone holds auth.
                implementation(libs.play.services.wearable)
                // Material Components for Android — provides the XML Theme.Material3.*
                // parent styles referenced in res/values/themes.xml.
                implementation(libs.material.components)
            }
        }
    }
}

android {
    namespace = "com.dmzs.datawatchclient"
    compileSdk = 35

    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "com.dmzs.datawatchclient"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Embed the current commit short SHA so the running app can report
        // exactly which build the user is testing. Falls back to "dev" when
        // git isn't available (e.g., a tarball clone in CI).
        val gitSha: String =
            providers.exec {
                commandLine("git", "rev-parse", "--short=8", "HEAD")
                isIgnoreExitValue = true
            }.standardOutput.asText.map { it.trim() }.orElse("dev").get()
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    flavorDimensions += "track"
    productFlavors {
        create("publicTrack") {
            dimension = "track"
            applicationIdSuffix = ""
            versionNameSuffix = ""
            manifestPlaceholders["autoCategory"] = "androidx.car.app.category.MESSAGING"
            // Auto module has its own flavor dimension `surface`;
            // pair publicTrack → publicMessaging so the
            // CarAppService + manifest merge into the release APK.
            missingDimensionStrategy("surface", "publicMessaging")
        }
        create("dev") {
            dimension = "track"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            manifestPlaceholders["autoCategory"] = "androidx.car.app.category.MESSAGING"
            missingDimensionStrategy("surface", "devPassenger")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Sprint 1: lint reports but doesn't gate the build. Sprint 5 flips
        // abortOnError back to true and adds a lint baseline.
        abortOnError = false
        warningsAsErrors = false
        checkReleaseBuilds = false
    }
}

ktlint {
    ignoreFailures.set(true) // Sprint 1 report-only; see root build.gradle.kts
}
