package com.dmzs.datawatchclient.push

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Foreground service that subscribes to per-profile ntfy topics and surfaces
 * push events as notifications. Used for servers that haven't been issued a
 * Firebase project, or when FCM is otherwise unavailable on the device.
 *
 * Each enabled profile that has an `ntfyTopic` in [PushTokenStore] gets its
 * own JSON-stream long-poll connection. The service stays foreground (low-
 * importance channel) so Doze doesn't kill the connections — exactly the
 * trade-off the user opted into when they chose ntfy over FCM.
 */
public class NtfyFallbackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    // Reuse the shared module's pre-configured HttpClient (OkHttp engine on
    // Android) — keeps engine selection in one place. The shared client already
    // disables `expectSuccess`, which is what we want here.
    private val client: HttpClient =
        com.dmzs.datawatchclient.transport.createHttpClient()

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
        client.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun reconcile() {
        val store = ServiceLocator.pushTokenStore
        val profiles = ServiceLocator.profileRepository.observeAll().first()
            .filter { it.enabled }
        for (profile in profiles) {
            val topic = store.ntfyTopicFor(profile.id) ?: continue
            val server = store.ntfyServerFor(profile.id) ?: PushTokenStore.DEFAULT_NTFY_SERVER
            jobs.getOrPut(profile.id) { scope.launch { subscribe(server, topic) } }
        }
    }

    private suspend fun subscribe(server: String, topic: String) {
        val url = "$server/$topic/json"
        var backoff = 2_000L
        while (true) {
            try {
                val res = client.get(url)
                val channel = res.bodyAsChannel()
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank()) continue
                    handleNtfyLine(line)
                }
                backoff = 2_000L
            } catch (e: Throwable) {
                android.util.Log.w("NtfyFallback", "subscription error: ${e.message}; retrying in ${backoff}ms")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    private fun handleNtfyLine(line: String) {
        val msg = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(NtfyMessage.serializer(), line) }
            .getOrNull() ?: return
        if (msg.event != "message") return
        val sessionId = msg.title?.substringAfter("session ")?.substringBefore(" ")?.takeIf { it.isNotBlank() }
            ?: msg.id
        NotificationPoster(applicationContext).post(
            NotificationPoster.Event(
                sessionId = sessionId,
                type = inferType(msg.title.orEmpty()),
                title = msg.title ?: "Datawatch",
                body = msg.message ?: "",
            ),
        )
    }

    private fun inferType(title: String): NotificationPoster.Event.Type = when {
        title.contains("input", ignoreCase = true) -> NotificationPoster.Event.Type.InputNeeded
        title.contains("error", ignoreCase = true) -> NotificationPoster.Event.Type.Error
        title.contains("rate", ignoreCase = true) -> NotificationPoster.Event.Type.RateLimited
        else -> NotificationPoster.Event.Type.Completed
    }

    private fun foregroundNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.FOREGROUND)
            .setContentTitle("Datawatch — listening for ntfy")
            .setSmallIcon(R.drawable.ic_stat_dw)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    @Serializable
    private data class NtfyMessage(
        val id: String = "",
        val event: String = "",
        val time: Long = 0,
        val topic: String = "",
        val title: String? = null,
        val message: String? = null,
        @SerialName("priority") val priority: Int = 3,
    )

    public companion object {
        public const val FOREGROUND_NOTIFICATION_ID: Int = 4_242

        public fun start(context: Context) {
            val intent = Intent(context, NtfyFallbackService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Throwable) {
                android.util.Log.w("NtfyFallback", "startForegroundService denied: ${e.message}")
            }
        }
    }
}
