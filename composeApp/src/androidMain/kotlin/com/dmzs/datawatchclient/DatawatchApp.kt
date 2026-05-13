package com.dmzs.datawatchclient

import android.app.Application
import com.dmzs.datawatchclient.network.VpnMonitor
import com.dmzs.datawatchclient.push.ForegroundSessionTracker
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
    /** Exposed so the transport layer can observe VPN state before retrying. */
    public lateinit var vpnMonitor: VpnMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        DatabaseFactory.loadNativeLib()
        ForegroundSessionTracker.isForeground("") // register lifecycle observer on main thread
        com.dmzs.datawatchclient.di.ServiceLocator.init(this)
        // Publish session counts to the paired Wear device. Watch's
        // WearSessionCountsViewModel subscribes to /datawatch/counts
        // DataItem and populates its UI from the phone's values —
        // closes the "Pair phone in Settings" placeholder that was
        // unfinished from v0.5.0 Phase 1.
        com.dmzs.datawatchclient.wear.WearSyncService(this).start()
        // S10-1: Monitor Tailscale VPN connectivity so transport can
        // avoid retries while the tunnel is known to be down.
        vpnMonitor = VpnMonitor(this)
        vpnMonitor.start()
        // S10-2: Schedule 15-min WorkManager heartbeat for Wear sync.
        com.dmzs.datawatchclient.wear.WearHeartbeatWorker.schedule(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        if (::vpnMonitor.isInitialized) vpnMonitor.stop()
    }
}
