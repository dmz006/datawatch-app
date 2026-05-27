plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.dmzs.datawatchclient.auto"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin", "src/publicMain/kotlin")
            manifest.srcFile("src/publicMain/AndroidManifest.xml")
            // publicMain/res must be listed explicitly — the AGP default only
            // adds src/main/res. Without this, hosts_allowlist.xml was silently
            // excluded, causing getIdentifier() to return 0 at runtime and
            // addAllowedHosts(0) to throw Resources.NotFoundException inside
            // createHostValidator(), crashing the CarAppService on launch.
            res.srcDirs("src/main/res", "src/publicMain/res")
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

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.car.app)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
}
