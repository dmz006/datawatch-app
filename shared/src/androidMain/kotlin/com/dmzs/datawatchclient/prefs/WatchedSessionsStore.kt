package com.dmzs.datawatchclient.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Persists the set of session ids the operator has opted into watching.
 *
 * - Default: empty (no sessions watched). AlertsViewModel falls back to
 *   showing all alerts when the set is empty.
 * - Key is scoped per server-profile so watch state doesn't bleed across
 *   servers. Key: `watched_sessions_<profileId>` (StringSet).
 *
 * Plain SharedPreferences — only non-secret session ids.
 */
public class WatchedSessionsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    public fun watchedIds(profileId: String): Set<String> =
        prefs.getStringSet(key(profileId), emptySet()) ?: emptySet()

    public fun isWatched(profileId: String, sessionId: String): Boolean =
        watchedIds(profileId).contains(sessionId)

    public fun setWatched(profileId: String, sessionId: String, watched: Boolean) {
        val current = watchedIds(profileId).toMutableSet()
        if (watched) current.add(sessionId) else current.remove(sessionId)
        prefs.edit().putStringSet(key(profileId), current).apply()
    }

    public fun watchedFlow(profileId: String): Flow<Set<String>> =
        callbackFlow {
            val k = key(profileId)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
                if (changedKey == k) trySend(sp.getStringSet(k, emptySet()) ?: emptySet())
            }
            trySend(watchedIds(profileId))
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    private fun key(profileId: String): String = "$KEY_PREFIX$profileId"

    public companion object {
        public const val PREFS_FILE: String = "dw.watched_sessions.v1"
        public const val KEY_PREFIX: String = "watched_sessions."
    }
}
