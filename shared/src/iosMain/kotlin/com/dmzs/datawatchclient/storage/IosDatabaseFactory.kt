package com.dmzs.datawatchclient.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.dmzs.datawatchclient.db.DatawatchDb

/**
 * iOS [DatabaseFactory] actual. iOS skeleton — post-v1.0.0 Android scope — will
 * revisit native encryption options (SQLCipher Objective-C wrapper or Apple
 * Keychain + EncryptedContainer) when the iOS content phase begins.
 */
public actual class DatabaseFactory {
    public actual fun driver(): SqlDriver =
        NativeSqliteDriver(DatawatchDb.Schema, "datawatch.db")
}
