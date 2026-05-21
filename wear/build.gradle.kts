plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.play.publisher)
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
        applicationId = "com.dmzs.datawatchclient.wear"
        minSdk = 28  // Support Wear OS 4.0+ (API 28+) for broader device compatibility
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
    }

    buildFeatures { compose = true }

    signingConfigs {
        // Read keystore password from KEYSTORE_PASSWORD env var or ~/.android/.keystore-env file
        val keystorePasswordProvider = {
            System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
                ?: file("${System.getProperty("user.home")}/.android/.keystore-env").takeIf { it.exists() }?.readText()?.trim()
                ?: error("KEYSTORE_PASSWORD env var or ~/.android/.keystore-env file required for release signing")
        }

        create("release") {
            storeFile = file("${System.getProperty("user.home")}/.android/datawatch-dev-upload-ring.jks")
            storePassword = keystorePasswordProvider()
            keyAlias = "datawatch-dev-upload"
            keyPassword = keystorePasswordProvider()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            // Mirror composeApp's `.debug` suffix so side-loaded debug
            // builds on phone + watch share the same applicationId
            // (`com.dmzs.datawatchclient.wear.debug`).
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
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

ktlint {
    ignoreFailures.set(true) // Sprint 1 report-only; see root build.gradle.kts
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    // Baseline accepts the current NestedBlockDepth (WearMainActivity
    // :402) + TooManyFunctions findings pending the 2026-04-24 Wear
    // UI refactor (card-border pass). Regenerate with
    // `./gradlew :wear:detektBaseline` once that refactor is done.
    baseline = file("detekt-baseline.xml")
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
    implementation(libs.androidx.wear.watchface.complications.datasource)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
}

play {
    // Read JSON service account key from environment or local file.
    val keyPath = System.getenv("PLAY_PUBLISHER_KEY") ?: "${System.getProperty("user.home")}/.android/datawatch-play-key.json"
    serviceAccountCredentials.set(file(keyPath))

    // Internal testing track for v1.0.0 pre-release validation
    // Wear OS apps require AAB format (no APK support on Play Console)
    track.set("internal")
    defaultToAppBundles.set(true)
}
