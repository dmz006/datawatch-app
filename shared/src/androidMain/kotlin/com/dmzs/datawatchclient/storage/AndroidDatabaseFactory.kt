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
 * **Native lib prerequisite:** callers MUST have invoked
 * `System.loadLibrary("sqlcipher")` exactly once before the first `driver()` call.
 * The `composeApp` module does this in `DatawatchApp.onCreate()`.
 *
 * **What this guarantees** (docs/security-model.md § "At-rest protection"):
 * - Database is encrypted with AES-256 by SQLCipher using HMAC-SHA256-derived key.
 * - Master key never leaves the Android Keystore (not even to this process memory
 *   beyond the derivation step).
 * - App uninstall or factory reset irrecoverably destroys the key and therefore the
 *   data.
 */
public actual class DatabaseFactory(
    private val context: Context,
    private val keystore: KeystoreManager = KeystoreManager(context),
) {

    public actual fun driver(): SqlDriver {
        keystore.ensureMasterKey()
        val passphrase = keystore.deriveDatabasePassphrase()
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
