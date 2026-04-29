plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

val appVersion: String by rootProject.extra(
    providers.gradleProperty("DATAWATCH_APP_VERSION").get(),
)

kotlin {
    androidTarget {
        compilations.all { kotlinOptions.jvmTarget = "17" }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "DatawatchShared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // api = leak to consumers (types appear in :shared's public signatures).
                api(libs.kotlin.stdlib)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)
                api(libs.ktor.client.core)
                api(libs.sqldelight.runtime)
                api(libs.kotlinx.serialization.json)
                // implementation = internal-only.
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.sqldelight.coroutines)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.mockwebserver)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.sqldelight.android.driver)
                implementation(libs.sqlcipher.android)
                implementation(libs.androidx.security.crypto)
                implementation(libs.kotlinx.coroutines.android)
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqldelight.native.driver)
            }
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
    }
}

android {
    namespace = "com.dmzs.datawatchclient.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ktlint {
    ignoreFailures.set(true) // Sprint 1 report-only; see root build.gradle.kts
}

// B7: exclude SQLDelight-generated sources from ktlint's scan. The
// plugin auto-includes `build/generated/**` via the Kotlin source set
// which ktlint-gradle then recursively walks — the generated code has
// 2-space indent that ktlint's 4-space rule can't parse, producing
// hundreds of false-positives that mask real lint issues. `exclude`
// on the task (SourceTask) filters files before they reach the rule
// set; `filter {}` in the extension only affects console output.
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
    exclude { it.file.absolutePath.contains("${file("build").absolutePath}/generated/") }
}
tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask>().configureEach {
    exclude { it.file.absolutePath.contains("${file("build").absolutePath}/generated/") }
}

sqldelight {
    databases {
        create("DatawatchDb") {
            packageName.set("com.dmzs.datawatchclient.db")
            // verifyMigrations requires a baseline .db file to compare against; no
            // migrations exist yet at v0.1.0-pre. Re-enable once Sprint 2 lands the
            // first schema delta and the baseline .db is committed under
            // shared/src/commonMain/sqldelight/databases/.
            verifyMigrations.set(false)
        }
    }
}
