package com.dmzs.datawatchclient.storage

import app.cash.sqldelight.db.SqlDriver
import com.dmzs.datawatchclient.db.DatawatchDb

/**
 * Platform-backed database factory. Android implementation (see
 * `androidMain/storage/AndroidDatabaseFactory.kt`) opens a SQLCipher driver with a
 * key unwrapped from the Android Keystore; iOS implementation uses the native driver
 * (currently plaintext — SQLCipher for iOS arrives with the iOS content phase).
 */
public expect class DatabaseFactory {
    public fun driver(): SqlDriver
}

public fun DatabaseFactory.database(): DatawatchDb = DatawatchDb(driver())
