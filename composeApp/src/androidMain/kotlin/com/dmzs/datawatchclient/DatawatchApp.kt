package com.dmzs.datawatchclient

import android.app.Application
import com.dmzs.datawatchclient.storage.DatabaseFactory

/**
 * Application bootstrap.
 *
 * Load order matters:
 * 1. `DatabaseFactory.loadNativeLib()` must run before any DB open attempt so the
 *    SQLCipher `libsqlcipher.so` is resident before `AndroidSqliteDriver` asks the
 *    `SupportOpenHelperFactory` for a connection.
 * 2. DI graph construction happens lazily on first access (see `di.Container`) so
 *    cold start stays fast and nothing touches Keystore before the UI needs it.
 */
public class DatawatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DatabaseFactory.loadNativeLib()
    }
}
