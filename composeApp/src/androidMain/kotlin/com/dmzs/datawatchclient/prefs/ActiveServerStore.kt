package com.dmzs.datawatchclient.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Persists the id of the profile the user currently wants as "active" for the
 * Sessions + Stats + Alerts tabs. Decoupled from `ServerProfile.enabled` so a
 * user can have several enabled profiles but view one at a time (Sprint 3 adds
 * all-servers fan-out as a separate mode).
 *
 * Plain [SharedPreferences] — the value is just a non-secret identifier.
 */
public class ActiveServerStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    public fun get(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    public fun set(profileId: String?) {
        prefs.edit().apply {
            if (profileId == null) remove(KEY_ACTIVE_ID) else putString(KEY_ACTIVE_ID, profileId)
        }.apply()
    }

    public fun observe(): Flow<String?> =
        callbackFlow {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_ACTIVE_ID) trySend(sp.getString(KEY_ACTIVE_ID, null))
                }
            trySend(get())
            prefs.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    public companion object {
        public const val PREFS_FILE: String = "dw.active_server.v1"
        public const val KEY_ACTIVE_ID: String = "active_profile_id"

        /**
         * Sentinel value stored in [KEY_ACTIVE_ID] when the user picks the
         * "All servers" view. SessionsViewModel detects this and fans
         * `/api/federation/sessions` across every enabled profile rather
         * than scoping to a single profile.
         */
        public const val SENTINEL_ALL_SERVERS: String = "*all"
    }
}
