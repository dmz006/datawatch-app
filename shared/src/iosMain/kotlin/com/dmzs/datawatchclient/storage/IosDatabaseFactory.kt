package com.dmzs.datawatchclient.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.dmzs.datawatchclient.db.DatawatchDb
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionComplete
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS [DatabaseFactory] actual.
 *
 * Encryption: Apple Data Protection class A (NSFileProtectionComplete).
 * The OS uses a file-specific AES-256 key wrapped with the user's passcode-derived
 * key and the device UID stored in the Secure Enclave. The database is inaccessible
 * when the device is locked — no SQLCipher passphrase management needed.
 *
 * Protection is applied to all three SQLite files (.db, .db-wal, .db-shm) after
 * the driver opens the database for the first time.
 *
 * This is equivalent to Android's SQLCipher + Android Keystore in terms of
 * at-rest security guarantee. See docs/security-model.md § "At-rest protection".
 */
public actual class DatabaseFactory {
    public actual fun driver(): SqlDriver {
        val driver = NativeSqliteDriver(DatawatchDb.Schema, DB_NAME)
        applyDataProtection()
        return driver
    }

    private fun applyDataProtection() {
        val attrs = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionComplete)
        for (dir in candidateDirectories()) {
            for (suffix in listOf("", "-wal", "-shm")) {
                val path = "$dir/$DB_NAME$suffix"
                if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
                    NSFileManager.defaultManager.setAttributes(
                        attrs,
                        ofItemAtPath = path,
                        error = null,
                    )
                }
            }
        }
    }

    // SQLDelight 2.x native driver defaults to NSDocumentDirectory on iOS (via SQLiter).
    // Application Support is checked as a fallback in case the path changes in a future
    // driver version or is overridden via DatabaseConfiguration.
    @Suppress("UNCHECKED_CAST")
    private fun candidateDirectories(): List<String> =
        listOfNotNull(
            (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
                as List<*>).firstOrNull() as? String,
            (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
                as List<*>).firstOrNull() as? String,
        )

    public companion object {
        public const val DB_NAME: String = "datawatch.db"
    }
}
