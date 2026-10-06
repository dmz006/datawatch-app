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

    lint {
        abortOnError = false
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
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
    constraints {
        // androidx.car.app pulls guava 31.1-android, which is affected by
        // GHSA-7g45-4rm6-3mm3 / GHSA-5mg8-w23w-74h3 (fixed in 32.0.0-android).
        // Align with the version the Wear app already ships (issue #208).
        implementation(libs.guava) {
            because("guava < 32.0.0-android: GHSA-7g45-4rm6-3mm3, GHSA-5mg8-w23w-74h3")
        }
    }
}
