package com.dmzs.datawatchclient

/**
 * Canonical app version. Read by composeApp, wear, auto, and iOSApp at build time.
 * Per AGENT.md versioning rules, this value must match:
 *  - composeApp/build.gradle.kts `versionName`
 *  - wear/build.gradle.kts `versionName`
 *  - gradle.properties `DATAWATCH_APP_VERSION`
 *
 * The CI `check-version` job enforces parity across all four.
 */
public object Version {
    public const val VERSION: String = "0.35.0"
    public const val VERSION_CODE: Int = 75
}
