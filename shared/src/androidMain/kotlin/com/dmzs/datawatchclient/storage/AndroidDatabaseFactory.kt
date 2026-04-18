package com.dmzs.datawatchclient.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dmzs.datawatchclient.db.DatawatchDb

/**
 * Android [DatabaseFactory] actual. Phase 1 opens a plaintext SQLite database so the
 * rest of the shared module has something to compile against. Phase 2 (next commit)
 * replaces the driver with `sqlcipher-android` and unwraps a Keystore-bound master
 * key — see `docs/plans/2026-04-18-sprint-1-foundation.md`.
 */
public actual class DatabaseFactory(private val context: Context) {
    public actual fun driver(): SqlDriver =
        AndroidSqliteDriver(
            schema = DatawatchDb.Schema,
            context = context,
            name = "datawatch.db",
        )
}
