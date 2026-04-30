package com.dmzs.datawatchclient.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
 * **BL16 (v0.50.0) — biometric-bound key**: When [BiometricGate] is enabled,
 * a second Keystore key ([BIOMETRIC_KEY_ALIAS]) is created with
 * `setUserAuthenticationRequired(true)` + a 30-second window. The passphrase
 * is encrypted with this key and stored in [BIOMETRIC_PREFS_FILE]. The
 * [EncryptedSharedPreferences] copy is kept as a warm standby (not deleted) so
 * that a fallback is available if the auth window expires before the DB opens.
 * Migration runs in the SecurityCard toggle, within the 30 s window granted by
 * the confirmation biometric prompt.
 *
 * **What this class does NOT do:**
 * - It does not encrypt or decrypt user content directly (SQLCipher + the DB driver do).
 * - It does not prompt for biometrics — prompting lives in [BiometricGate].
 * - It does not expose the MasterKey to external callers; only the derived
 *   passphrase is surfaced, and only to the DatabaseFactory.
 */
public class KeystoreManager(private val context: Context) {
    public companion object {
        public const val PREFS_FILE: String = "dw.cipher.keys"
        internal const val KEY_DB_PASSPHRASE: String = "dw.db.passphrase.v1"
        internal const val PASSPHRASE_BYTES: Int = 32

        // BL16 — biometric-bound DB key (v0.50.0)
        public const val BIOMETRIC_KEY_ALIAS: String = "dw.cipher.keys.biometric.v1"
        public const val BIOMETRIC_PREFS_FILE: String = "dw.cipher.keys.biometric"
        internal const val KEY_BIOMETRIC_PASSPHRASE: String = "dw.db.passphrase.biometric.v1"
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_BITS = 128
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

    private val biometricPrefs: SharedPreferences =
        context.getSharedPreferences(BIOMETRIC_PREFS_FILE, Context.MODE_PRIVATE)

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

    /** True when the biometric-bound passphrase ciphertext exists in storage. */
    public fun hasBiometricPassphrase(): Boolean =
        biometricPrefs.contains(KEY_BIOMETRIC_PASSPHRASE)

    /**
     * Encrypts [passphrase] with the biometric-bound AES-256-GCM Keystore key and stores
     * IV+ciphertext in [biometricPrefs]. Must be called within the 30 s window opened by
     * a successful [BiometricGate.prompt].
     */
    public fun migratePassphraseToBiometricKey(passphrase: ByteArray) {
        ensureBiometricKeyExists()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        val key = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(passphrase)
        biometricPrefs.edit()
            .putString(
                KEY_BIOMETRIC_PASSPHRASE,
                Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP),
            )
            .apply()
    }

    /**
     * Decrypts and returns the biometric-bound passphrase. Must be called within the 30 s
     * auth window. Throws [IllegalStateException] if not migrated or [java.security.GeneralSecurityException]
     * if the window has expired.
     */
    public fun deriveDatabasePassphraseFromBiometricKey(): ByteArray {
        val stored =
            biometricPrefs.getString(KEY_BIOMETRIC_PASSPHRASE, null)
                ?: error("Biometric passphrase not found — migration has not run")
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val iv = raw.sliceArray(0 until GCM_IV_LEN)
        val ciphertext = raw.sliceArray(GCM_IV_LEN until raw.size)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        val key = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Reverses the biometric migration: decrypts the ciphertext (must be within auth window),
     * ensures the passphrase is back in [EncryptedSharedPreferences], removes the biometric
     * ciphertext, and deletes the Keystore entry.
     */
    public fun migratePassphraseFromBiometricKey() {
        val passphrase = deriveDatabasePassphraseFromBiometricKey()
        if (!prefs.contains(KEY_DB_PASSPHRASE)) {
            prefs.edit()
                .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP))
                .apply()
        }
        passphrase.fill(0)
        biometricPrefs.edit().remove(KEY_BIOMETRIC_PASSPHRASE).apply()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
            keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        }
    }

    private fun ensureBiometricKeyExists() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) return
        val keyGen =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec =
            KeyGenParameterSpec
                .Builder(
                    BIOMETRIC_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(30, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(30)
                    }
                }
                .build()
        keyGen.init(spec)
        keyGen.generateKey()
    }
}
