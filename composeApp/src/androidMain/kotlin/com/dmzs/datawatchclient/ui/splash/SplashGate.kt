package com.dmzs.datawatchclient.ui.splash

import android.content.Context

/**
 * Parity D37a — PWA splash gating (app.js `cs_splash_time` / `cs_splash_version`):
 * the brand splash shows on first launch, after an app version change, or when
 * more than 24 h have passed since it was last shown. Otherwise cold launch
 * goes straight in. The app keys off its own version (the PWA keys off the
 * daemon version, which the app doesn't know before the vault unlocks).
 *
 * Settings → About → "Replay splash" (D59a) is not gated.
 */
public object SplashGate {
    private const val PREFS = "splash_gate"
    private const val KEY_TIME = "last_shown_ms"
    private const val KEY_VERSION = "last_shown_version"
    internal const val INTERVAL_MS: Long = 24L * 60 * 60 * 1000

    /** Pure rule, unit-tested. */
    internal fun shouldShow(
        nowMs: Long,
        lastShownMs: Long,
        lastVersion: String?,
        currentVersion: String,
    ): Boolean =
        lastShownMs <= 0L ||
            lastVersion != currentVersion ||
            nowMs - lastShownMs >= INTERVAL_MS

    /**
     * PWA `isNewVersion`: only a *change* from a previously recorded version
     * counts (first launch is not an update).
     */
    internal fun updatedTo(
        lastVersion: String?,
        currentVersion: String,
    ): String? = currentVersion.takeIf { !lastVersion.isNullOrBlank() && lastVersion != currentVersion }

    /** Result of [consumeDecision]: whether to show, and the "Updated to vX" badge text source. */
    public data class Decision(val show: Boolean, val updatedTo: String?)

    /**
     * Decides whether this cold launch shows the splash and, when it does,
     * records the time + version (PWA writes them at the same point).
     */
    public fun consume(
        context: Context,
        currentVersion: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = consumeDecision(context, currentVersion, nowMs).show

    /** Like [consume] but also reports a version change for the PWA "Updated to vX" splash badge. */
    public fun consumeDecision(
        context: Context,
        currentVersion: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Decision {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastVersion = prefs.getString(KEY_VERSION, null)
        val show =
            shouldShow(
                nowMs = nowMs,
                lastShownMs = prefs.getLong(KEY_TIME, 0L),
                lastVersion = lastVersion,
                currentVersion = currentVersion,
            )
        if (show) {
            prefs.edit().putLong(KEY_TIME, nowMs).putString(KEY_VERSION, currentVersion).apply()
        }
        return Decision(show = show, updatedTo = updatedTo(lastVersion, currentVersion))
    }
}
