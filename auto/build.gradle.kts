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

    flavorDimensions += "surface"
    productFlavors {
        create("publicMessaging") {
            dimension = "surface"
        }
        create("devPassenger") {
            dimension = "surface"
        }
    }

    sourceSets {
        getByName("publicMessaging") {
            java.srcDirs("src/publicMain/kotlin")
            manifest.srcFile("src/publicMain/AndroidManifest.xml")
        }
        getByName("devPassenger") {
            java.srcDirs("src/devMain/kotlin")
            manifest.srcFile("src/devMain/AndroidManifest.xml")
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
