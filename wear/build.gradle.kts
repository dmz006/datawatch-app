plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val appVersion: String = providers.gradleProperty("DATAWATCH_APP_VERSION").get()
val appVersionCode: Int =
    providers.gradleProperty("DATAWATCH_APP_VERSION_CODE").get().toInt()

android {
    // Namespace stays distinct so Kotlin packages don't collide with
    // composeApp's. The applicationId, however, MUST match the paired
    // phone app for the Wearable Data Layer to treat us as the same
    // app pair — DataClient.putDataItem uri ownership is (pkg, cert).
    // With mismatched pkgs, phone-published DataItems never reach the
    // watch subscriber. This was the root cause of the "Pair phone in
    // Settings" placeholder never flipping.
    namespace = "com.dmzs.datawatchclient.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dmzs.datawatchclient"
        minSdk = 30
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
    }

    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            // Mirror composeApp's `.debug` suffix so side-loaded debug
            // builds on phone + watch share the same applicationId
            // (`com.dmzs.datawatchclient.debug`).
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
    }
}

ktlint {
    ignoreFailures.set(true) // Sprint 1 report-only; see root build.gradle.kts
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.guava)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
