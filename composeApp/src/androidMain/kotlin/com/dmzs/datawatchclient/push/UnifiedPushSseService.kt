package com.dmzs.datawatchclient.push

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.PushEventDto
import com.dmzs.datawatchclient.transport.dto.PushRegistrationDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Foreground service that subscribes to the datawatch UnifiedPush SSE endpoint
 * (`GET /api/push/alerts`) per enabled server profile and surfaces incoming
 * events as high- or default-importance notifications.
 *
 * Runs in parallel with [NtfyFallbackService] — both fire independently for
 * redundancy. SSE adds deep-link support via the `click` field.
 *
 * Reconnect/backoff is handled inside [TransportClient.subscribePushAlerts].
 *
 * Lifecycle: started from [AppRoot] bootstrap; stays alive until the app
 * process is killed. START_STICKY ensures the OS restarts it after low-memory
 * kills.
 */
public class UnifiedPushSseService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // profile.id → Pair(job, transportSignature) so we can detect profile trust changes
    private val jobs = mutableMapOf<String, Pair<Job, String>>()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureRegistered(this)
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { reconcile() }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun reconcile() {
        val profiles = ServiceLocator.profileRepository.observeAll().first().filter { it.enabled }
        for (profile in profiles) {
            // Transport signature includes trust setting — restart the job if it changed
            // (e.g. user toggled "Self-signed / trust all certs" after the service started).
            val sig = "${profile.baseUrl}|${profile.trustAnchorSha256 ?: ""}"
            val existing = jobs[profile.id]
            if (existing != null && existing.first.isActive && existing.second == sig) continue
            existing?.first?.cancel()
            val transport = ServiceLocator.transportFor(profile)
            // Generate or retrieve stable client_id per server URL.
            val prefs = applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            val key = "sse_client_id_${profile.baseUrl.hashCode()}"
            val clientId = prefs.getString(key, null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(key, it).apply()
            }
            // Register this device as a push receiver — fire-and-forget.
            runCatching {
                transport.registerPush(
                    PushRegistrationDto(
                        endpoint = "${profile.baseUrl}/api/push/alerts",
                        clientId = clientId,
                    ),
                )
            }
            // Subscribe and collect events indefinitely.
            val job = scope.launch {
                transport.subscribePushAlerts().collect { event -> postNotification(event) }
            }
            jobs[profile.id] = job to sig
        }
    }

    private suspend fun postNotification(event: PushEventDto) {
        val sessionId = event.tags.firstOrNull()
            ?: event.click.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: "system"
        if (runCatching { ServiceLocator.sessionRepository.isMuted(sessionId) }.getOrDefault(false)) return
        val type = when {
            event.priority >= 4 && event.title.contains("input", ignoreCase = true) ->
                NotificationPoster.Event.Type.InputNeeded
            event.priority >= 4 && event.title.contains("error", ignoreCase = true) ->
                NotificationPoster.Event.Type.Error
            event.priority >= 4 -> NotificationPoster.Event.Type.InputNeeded
            else -> NotificationPoster.Event.Type.StateChange
        }
        NotificationPoster(applicationContext).post(
            NotificationPoster.Event(
                sessionId = sessionId,
                type = type,
                title = event.title.ifBlank { "datawatch" },
                body = event.message,
            ),
        )
    }

    private fun foregroundNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.FOREGROUND)
            .setContentTitle("Datawatch — alerts stream active")
            .setSmallIcon(R.drawable.ic_stat_dw)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    public companion object {
        public const val FOREGROUND_NOTIFICATION_ID: Int = 4_243

        private const val PREFS_FILE: String = "unified_push_prefs"

        public fun start(context: Context) {
            val intent = Intent(context, UnifiedPushSseService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Throwable) {
                android.util.Log.w("UnifiedPushSse", "startForegroundService denied: ${e.message}")
            }
        }
    }
}
