package com.dmzs.datawatchclient.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Persists the set of automata (PRD) ids the operator has opted into watching.
 *
 * Same semantics as [WatchedSessionsStore] — per-profile, default empty,
 * reactive via callbackFlow.
 */
public class WatchedAutomataStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    public fun watchedIds(profileId: String): Set<String> =
        prefs.getStringSet(key(profileId), emptySet()) ?: emptySet()

    public fun isWatched(profileId: String, prdId: String): Boolean =
        watchedIds(profileId).contains(prdId)

    public fun setWatched(profileId: String, prdId: String, watched: Boolean) {
        val current = watchedIds(profileId).toMutableSet()
        if (watched) current.add(prdId) else current.remove(prdId)
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
        public const val PREFS_FILE: String = "dw.watched_automata.v1"
        public const val KEY_PREFIX: String = "watched_automata."
    }
}
