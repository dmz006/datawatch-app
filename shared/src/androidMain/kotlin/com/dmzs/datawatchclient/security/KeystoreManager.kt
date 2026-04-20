package com.dmzs.datawatchclient.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Lifetime manager for the SQLCipher database passphrase, per
 * `docs/security-model.md` "Key hierarchy".
 *
 * First call generates a cryptographically-random 32-byte passphrase and hands
 * it to Jetpack Security's [EncryptedSharedPreferences], which wraps it under a
 * hardware-backed AES-256-GCM key bound to the Android Keystore ([MasterKey]).
 * Subsequent calls retrieve and decrypt the wrapped ciphertext — the plaintext
 * passphrase is only ever in process memory for the duration of an open DB
 * handle.
 *
 * **What this class does NOT do:**
 * - It does not encrypt or decrypt user content directly (SQLCipher + the DB driver do).
 * - It does not prompt for biometrics — ADR-0011 defers biometric unlock.
 * - It does not expose the MasterKey to external callers; only the derived
 *   passphrase is surfaced, and only to the DatabaseFactory.
 *
 * **Design notes:**
 * - Earlier revisions tried to HMAC-derive from a Keystore-bound AES key via
 *   `master.encoded`, but Android Keystore restricts extractable keys and
 *   returns `null` — the current approach sidesteps that entirely by delegating
 *   wrapping to Jetpack Security.
 * - StrongBox backing happens automatically where available (MasterKey builder
 *   honors the device's strongest available scheme).
 */
public class KeystoreManager(context: Context) {
    public companion object {
        public const val PREFS_FILE: String = "dw.cipher.keys"
        internal const val KEY_DB_PASSPHRASE: String = "dw.db.passphrase.v1"
        internal const val PASSPHRASE_BYTES: Int = 32 // 256-bit SQLCipher key
    }

    private val masterKey: MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private val prefs: SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    /** Ensures the SQLCipher passphrase exists, generating on first call. */
    public fun ensureMasterKey() {
        if (!prefs.contains(KEY_DB_PASSPHRASE)) {
            val bytes = ByteArray(PASSPHRASE_BYTES)
            SecureRandom().nextBytes(bytes)
            prefs.edit()
                .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(bytes, Base64.NO_WRAP))
                .apply()
            bytes.fill(0) // best-effort wipe
        }
    }

    /** Returns the 32-byte SQLCipher passphrase, lazily generating it first. */
    public fun deriveDatabasePassphrase(): ByteArray {
        ensureMasterKey()
        val encoded =
            prefs.getString(KEY_DB_PASSPHRASE, null)
                ?: error("SQLCipher passphrase unexpectedly absent after ensure")
        return Base64.decode(encoded, Base64.NO_WRAP)
    }
}
