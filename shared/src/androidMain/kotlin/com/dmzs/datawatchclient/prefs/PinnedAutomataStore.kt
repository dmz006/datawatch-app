package com.dmzs.datawatchclient.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Persists the set of automata (PRD) ids the operator has pinned to the top of the list.
 *
 * Same semantics as [WatchedAutomataStore] — per-profile, default empty, reactive.
 * Key: `pinned_automata_<profileId>` (StringSet).
 */
public class PinnedAutomataStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    public fun pinnedIds(profileId: String): Set<String> =
        prefs.getStringSet(key(profileId), emptySet()) ?: emptySet()

    public fun isPinned(profileId: String, prdId: String): Boolean =
        pinnedIds(profileId).contains(prdId)

    public fun setPinned(profileId: String, prdId: String, pinned: Boolean) {
        val current = pinnedIds(profileId).toMutableSet()
        if (pinned) current.add(prdId) else current.remove(prdId)
        prefs.edit().putStringSet(key(profileId), current).apply()
    }

    public fun pinnedFlow(profileId: String): Flow<Set<String>> =
        callbackFlow {
            val k = key(profileId)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
                if (changedKey == k) trySend(sp.getStringSet(k, emptySet()) ?: emptySet())
            }
            trySend(pinnedIds(profileId))
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    private fun key(profileId: String): String = "$KEY_PREFIX$profileId"

    public companion object {
        public const val PREFS_FILE: String = "dw.pinned_automata.v1"
        public const val KEY_PREFIX: String = "pinned_automata."
    }
}
