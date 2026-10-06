pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "datawatch-client"

// Security floors for transitive build-tooling dependencies (issue #208).
// None of these ship in the APK/AAB or the iOS XCFramework. They come from the
// Android Gradle Plugin (bouncycastle, jose4j, jdom2, commons-compress,
// protobuf-java, grpc-netty → netty), the Unified Test Platform host-side
// test runner (`_internal-unified-test-platform-*`, netty/protobuf/commons-io)
// and the ktlint CLI (`ktlint` → logback). They are reported to the Dependency
// Graph by the `submit-graph` job, so Dependabot flags them.
//
// Each floor only RAISES a requested version that is below it, within the same
// release line (netty 4.1.x, bouncycastle *-jdk18on, protobuf 3.x, logback
// 1.5.x). It never downgrades and never touches a version that is already newer.
// Applied to every project's buildscript classpath (where `plugins {}` resolves)
// and to every project configuration.
val toolingSecurityFloors: Map<String, String> = listOf(
    // io.netty 4.1 line (pulled in by grpc-netty 1.57) — rapid-reset, request
    // smuggling, SNI handler and compression-bomb advisories; all fixed by
    // 4.1.137.Final. Listed per module so netty-tcnative (own version line)
    // is never touched.
    "buffer", "codec", "codec-http", "codec-http2", "codec-socks", "common",
    "handler", "handler-proxy", "resolver", "transport",
    "transport-native-unix-common",
).associate { "io.netty:netty-$it" to "4.1.138.Final" } + mapOf(
    "org.bouncycastle:bcprov-jdk18on" to "1.86",
    "org.bouncycastle:bcpkix-jdk18on" to "1.86",
    "org.bouncycastle:bcutil-jdk18on" to "1.86",
    "org.apache.commons:commons-compress" to "1.28.0",
    "commons-io:commons-io" to "2.17.0",
    "ch.qos.logback:logback-core" to "1.5.38",
    "ch.qos.logback:logback-classic" to "1.5.38",
    "org.jdom:jdom2" to "2.0.6.1",
    "org.bitbucket.b_c:jose4j" to "0.9.6",
    "com.google.protobuf:protobuf-java" to "3.25.9",
    "com.google.protobuf:protobuf-java-util" to "3.25.9",
    // Kotlin 2.4 Swift-export tooling (shared `swiftExportClasspathResolvable`) — build-time only.
    "io.opentelemetry:opentelemetry-api" to "1.62.0",
    "io.opentelemetry:opentelemetry-context" to "1.62.0",
)

fun numericVersion(v: String): List<Int> =
    v.split('.', '-').map { it.toIntOrNull() }.takeWhile { it != null }.map { it!! }

fun isLowerVersion(a: String, b: String): Boolean {
    val pa = numericVersion(a)
    val pb = numericVersion(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x < y
    }
    return false
}

fun ResolutionStrategy.applyToolingSecurityFloors() {
    eachDependency {
        val floor = toolingSecurityFloors["${requested.group}:${requested.name}"]
            ?: return@eachDependency
        val current = requested.version
        if (current.isNullOrBlank() || !current[0].isDigit()) return@eachDependency
        if (isLowerVersion(current, floor)) {
            useVersion(floor)
            because("security floor for build tooling (issue #208)")
        }
    }
}

gradle.allprojects {
    buildscript.configurations.configureEach { resolutionStrategy.applyToolingSecurityFloors() }
    configurations.configureEach { resolutionStrategy.applyToolingSecurityFloors() }
}

include(":shared")
include(":composeApp")
include(":wear")
include(":auto")
