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
        com.dmzs.datawatchclient.di.ServiceLocator.init(this)
        // Publish session counts to the paired Wear device. Watch's
        // WearSessionCountsViewModel subscribes to /datawatch/counts
        // DataItem and populates its UI from the phone's values —
        // closes the "Pair phone in Settings" placeholder that was
        // unfinished from v0.5.0 Phase 1.
        com.dmzs.datawatchclient.wear.WearSyncService(this).start()
    }
}
