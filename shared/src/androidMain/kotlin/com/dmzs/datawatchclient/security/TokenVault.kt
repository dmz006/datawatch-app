package com.dmzs.datawatchclient.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for per-server bearer tokens. Tokens are NEVER persisted in the
 * SQLite database (see `docs/security-model.md`); only a Keystore alias reference is.
 * This vault resolves those aliases back to plaintext on demand for the transport
 * layer's `tokenProvider` lambda.
 *
 * Uses [EncryptedSharedPreferences] — AES-256-GCM for values, AES-256-SIV for keys —
 * with a MasterKey bound to the Android Keystore (auto-rotates if Android deems it
 * necessary). Separate XML file per app install; wiped by uninstall.
 */
public class TokenVault(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Store the bearer token for a given server profile. Returns the opaque alias
     *  that should be written into `ServerProfile.bearerTokenRef`. */
    public fun put(profileId: String, token: String): String {
        val alias = aliasFor(profileId)
        prefs.edit().putString(alias, token).apply()
        return alias
    }

    /** Retrieve the bearer token for a given alias; null if missing. */
    public fun get(alias: String): String? = prefs.getString(alias, null)

    /** Remove the bearer token for a given alias. */
    public fun remove(alias: String) {
        prefs.edit().remove(alias).apply()
    }

    /** List all known aliases — useful for bulk cleanup when a profile is deleted. */
    public fun listAliases(): Set<String> = prefs.all.keys

    public companion object {
        public const val PREFS_FILE: String = "dw.tokens.v1"
        public const val ALIAS_PREFIX: String = "dw.profile."

        public fun aliasFor(profileId: String): String = "$ALIAS_PREFIX$profileId"
    }
}
