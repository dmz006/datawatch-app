package com.dmzs.datawatchclient.wear

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * S10-2 — 15-minute WorkManager periodic job that triggers a Tier A
 * dashboard sync to the watch. Replaces the removed 15 s polling loop
 * in [WearSyncService]: the watch now stays up-to-date via on-demand
 * MessageClient syncs (triggered by app-open / refresh tap) plus this
 * background heartbeat — dramatically reducing BLE radio activations.
 *
 * Requires CONNECTED network so the phone can reach the datawatch
 * server before pushing DataItems to the watch.
 */
public class WearHeartbeatWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        android.util.Log.d(TAG, "15-min heartbeat tick — fetching dashboard")
        runCatching {
            WearSyncService(applicationContext).fetchAndPublishDashboard()
        }.onFailure { err ->
            android.util.Log.w(TAG, "heartbeat fetchAndPublishDashboard FAILED: ${err.message}")
        }
        return Result.success()
    }

    public companion object {
        private const val TAG = "WearHeartbeat"
        public const val WORK_NAME: String = "wear_heartbeat"

        public fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<WearHeartbeatWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
