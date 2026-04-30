package com.dmzs.datawatchclient.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dmzs.datawatchclient.db.DatawatchDb
import com.dmzs.datawatchclient.security.KeystoreManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Android [DatabaseFactory] actual. Phase 2 wraps SQLDelight's [AndroidSqliteDriver]
 * in a SQLCipher-backed `SupportOpenHelperFactory`, with the passphrase derived from
 * the Keystore-bound master key (see [KeystoreManager]).
 *
 * **BL16 (v0.50.0)**: When the biometric gate is enabled, the passphrase is instead
 * decrypted from the biometric-bound Keystore key (within the 30 s window granted by
 * the launch biometric prompt). If decryption fails (window expired), the implementation
 * falls back to the [EncryptedSharedPreferences]-wrapped copy so the DB always opens —
 * the EncryptedSharedPreferences copy is kept as a warm standby and is never deleted.
 *
 * **Native lib prerequisite:** callers MUST have invoked
 * `System.loadLibrary("sqlcipher")` exactly once before the first `driver()` call.
 * The `composeApp` module does this in `DatawatchApp.onCreate()`.
 *
 * **What this guarantees** (docs/security-model.md § "At-rest protection"):
 * - Database is encrypted with AES-256 by SQLCipher using HMAC-SHA256-derived key.
 * - Master key never leaves the Android Keystore.
 * - App uninstall or factory reset irrecoverably destroys the key and therefore the data.
 * - When biometric is enabled: key additionally requires a fresh biometric event within
 *   30 seconds, binding DB access to the user's enrolled biometric.
 */
public actual class DatabaseFactory(
    private val context: Context,
    private val keystore: KeystoreManager = KeystoreManager(context),
) {
    public actual fun driver(): SqlDriver {
        keystore.ensureMasterKey()
        val passphrase =
            if (keystore.hasBiometricPassphrase()) {
                runCatching { keystore.deriveDatabasePassphraseFromBiometricKey() }
                    .getOrElse { keystore.deriveDatabasePassphrase() }
            } else {
                keystore.deriveDatabasePassphrase()
            }
        val factory = SupportOpenHelperFactory(passphrase)
        return AndroidSqliteDriver(
            schema = DatawatchDb.Schema,
            context = context,
            name = DB_NAME,
            factory = factory,
        )
    }

    public companion object {
        public const val DB_NAME: String = "datawatch.db"

        /**
         * Call once at process start before opening the DB. Kept here rather than in
         * the factory constructor so callers can reason about load order explicitly.
         */
        public fun loadNativeLib() {
            System.loadLibrary("sqlcipher")
        }
    }
}
