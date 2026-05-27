plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.play.publisher)
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
        // applicationId MUST match the phone app so the Wearable Data Layer
        // treats them as the same pair. The wear APK is embedded in the
        // phone app's AAB via `wearApp(project(":wear"))` in composeApp.
        applicationId = "com.dmzs.datawatchclient"
        minSdk = 28  // Support Wear OS 4.0+ (API 28+) for broader device compatibility
        targetSdk = 35
        // Wear version codes are offset by 100_000 so phone (305) and wear (100305)
        // occupy the same listing without version code collision in Play Console.
        versionCode = appVersionCode + 100_000
        versionName = appVersion
    }

    buildFeatures { compose = true }

    signingConfigs {
        val keystorePasswordRaw: String? =
            System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotEmpty() }
                ?: file("${System.getProperty("user.home")}/.android/.keystore-env")
                    .takeIf { it.exists() }?.readText()?.trim()

        if (keystorePasswordRaw != null) {
            create("release") {
                storeFile = file("${System.getProperty("user.home")}/.android/datawatch-upload-ring.jks")
                storePassword = keystorePasswordRaw
                keyAlias = "datawatch-upload"
                keyPassword = keystorePasswordRaw
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
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
    // Same service account and track as the phone app — both upload to the
    // same Play Console listing (com.dmzs.datawatchclient). Play Console
    // detects the android.hardware.type.watch required-feature in the wear
    // manifest and serves this AAB only to Wear OS devices.
    val keyPath = System.getenv("PLAY_PUBLISHER_KEY")
        ?: "${System.getProperty("user.home")}/.android/datawatch-play-key.json"
    serviceAccountCredentials.set(file(keyPath))
    // Wear OS form factor has its own track namespace in the Play Developer
    // API — uploads must target `wear:<track>` rather than the bare phone-
    // form-factor `<track>`. Using the unprefixed name routes the wear AAB to
    // the phone track, which Play rejects because the AAB requires
    // android.hardware.type.watch.
    track.set("wear:internal")
    defaultToAppBundles.set(true)
}
