package com.dmzs.datawatchclient.wear

import android.content.Context
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.storage.observeForProfileAny
import com.dmzs.datawatchclient.transport.ws.WsOutbound
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Publishes the active server's session counts + name to the paired
 * Wear device via the Wearable Data Layer. The watch subscribes to
 * the `/datawatch/counts` DataItem path in its own ViewModel.
 *
 * Path + key contract:
 *
 * ```
 * /datawatch/counts
 *   serverId: String         (empty = none paired)
 *   serverName: String
 *   running: Int
 *   waiting: Int
 *   total: Int
 *   ts: Long                 (force DataItem to be considered changed)
 * ```
 *
 * Started from [com.dmzs.datawatchclient.DatawatchApp.onCreate]; runs
 * the length of the process. Uses the phone-side SessionRepository so
 * the watch never needs to hold a bearer token or call the server.
 *
 * Filed after v0.33.11 on-device test surfaced the Wear app's "Pair
 * phone in Settings" stub — the Phase 1 placeholder in the Wear
 * ViewModel never had an actual DataLayer producer on the phone side.
 */
public class WearSyncService(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val messageListener =
        MessageClient.OnMessageReceivedListener { ev: MessageEvent ->
            when (ev.path) {
                SET_ACTIVE_PATH -> {
                    val id = runCatching { String(ev.data, Charsets.UTF_8) }.getOrNull()
                    if (!id.isNullOrEmpty()) {
                        ServiceLocator.activeServerStore.set(id)
                    }
                }
                REPLY_PATH -> {
                    // Watch-initiated reply: payload is "sessionId\ntext".
                    // Open a transient WS collector for the session, emit
                    // send_input, then cancel after a grace period. The
                    // server only accepts input on an open WS (see
                    // WsOutbound comment at shared/.../WsOutbound.kt:
                    // "server doesn't expose POST /api/sessions/reply").
                    val body = runCatching { String(ev.data, Charsets.UTF_8) }.getOrNull().orEmpty()
                    val (sessionId, text) =
                        body.split("\n", limit = 2).let { parts ->
                            val id = parts.firstOrNull().orEmpty()
                            val rest = parts.getOrNull(1).orEmpty()
                            id to rest
                        }
                    if (sessionId.isNotEmpty() && text.isNotEmpty()) {
                        scope.launch { forwardWatchReply(sessionId, text) }
                    }
                }
                AUDIO_PATH -> {
                    // v0.35.8 — watch-initiated voice transcribe.
                    // Payload is "sessionId\n<utf-8 garbage>" + raw
                    // audio bytes after the first newline. We slice
                    // by the first 0x0A byte so the audio body is
                    // not corrupted by UTF-8 decoding.
                    val data = ev.data
                    val nl = data.indexOf('\n'.code.toByte())
                    if (nl > 0 && nl < data.size - 1) {
                        val sessionId = String(data, 0, nl, Charsets.UTF_8)
                        val audio = data.copyOfRange(nl + 1, data.size)
                        if (sessionId.isNotEmpty() && audio.isNotEmpty()) {
                            scope.launch {
                                forwardWatchAudio(ev.sourceNodeId, sessionId, audio)
                            }
                        }
                    }
                }
            }
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    public fun start() {
        // Listen for the watch's "switch active server" message so a
        // server picked on the wrist updates the phone's shared store.
        runCatching {
            Wearable.getMessageClient(context).addListener(messageListener)
        }
        // Active server's session counts + per-session list. A single
        // flow feeds both paths so they stay in lock-step; the watch's
        // Sessions page and the tap-to-reply popup read the same
        // snapshot of running sessions.
        scope.launch {
            ServiceLocator.activeServerStore.observe()
                .flatMapLatest { activeId ->
                    combine(
                        ServiceLocator.profileRepository.observeAll(),
                        if (activeId == null || activeId == ActiveServerStore.SENTINEL_ALL_SERVERS) {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        } else {
                            ServiceLocator.sessionRepository.observeForProfile(activeId)
                        },
                    ) { profiles, sessions ->
                        val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
                        val count =
                            Snapshot(
                                serverId = profile?.id.orEmpty(),
                                serverName = profile?.displayName.orEmpty(),
                                running = sessions.count { it.state == SessionState.Running },
                                waiting = sessions.count { it.state == SessionState.Waiting },
                                total = sessions.size,
                            )
                        val list =
                            SessionsListSnapshot(
                                items = sessions
                                    .asSequence()
                                    .sortedByDescending { it.lastActivityAt }
                                    .take(SESSIONS_PUBLISH_LIMIT)
                                    .map { s ->
                                        SessionItem(
                                            id = s.id,
                                            title = (s.name ?: s.taskSummary ?: s.id).take(40),
                                            backend = s.backend.orEmpty(),
                                            stateName = s.state.name,
                                            // v0.35.8 — prefer
                                            // lastResponse (the LLM's
                                            // actual output) over
                                            // lastPrompt (the question
                                            // it's waiting on); a
                                            // running session is
                                            // mostly interesting for
                                            // what it just produced.
                                            // Falls back to
                                            // lastPrompt → taskSummary
                                            // → empty so the row is
                                            // always anchored to
                                            // something readable.
                                            lastLine =
                                                (
                                                    s.lastResponse
                                                        ?: s.lastPrompt
                                                        ?: s.taskSummary
                                                        ?: ""
                                                ).replace("\n", " ")
                                                    .trim()
                                                    .take(SESSION_LAST_LINE_MAX),
                                        )
                                    }
                                    .toList(),
                            )
                        count to list
                    }
                }
                .collectLatest { (snap, list) ->
                    publishCounts(snap)
                    publishSessions(list)
                }
        }
        // Enabled profile list — watch's server-picker page reads this.
        scope.launch {
            combine(
                ServiceLocator.profileRepository.observeAll(),
                ServiceLocator.activeServerStore.observe(),
            ) { profiles, activeId ->
                ProfilesSnapshot(
                    activeId = activeId.orEmpty(),
                    ids = profiles.filter { it.enabled }.map { it.id },
                    names = profiles.filter { it.enabled }.map { it.displayName },
                )
            }.collectLatest { snap -> publishProfiles(snap) }
        }
        // Light stats snapshot — polled every 15 s on the phone, cached
        // in the DataLayer so the watch reads the last-known values
        // without opening its own HTTP client.
        scope.launch {
            while (isActive) {
                runCatching {
                    val activeId = ServiceLocator.activeServerStore.get()
                    if (!activeId.isNullOrEmpty() && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS) {
                        val profile =
                            ServiceLocator.profileRepository.observeAll().first()
                                .firstOrNull { it.id == activeId && it.enabled }
                        if (profile != null) {
                            ServiceLocator.transportFor(profile).stats().onSuccess { s ->
                                publishStats(
                                    StatsSnapshot(
                                        cpuLoad1 = s.cpuLoad1 ?: s.cpuPct ?: 0.0,
                                        cpuCores = s.cpuCores ?: 0,
                                        memUsed = s.memUsed ?: 0L,
                                        memTotal = s.memTotal ?: 0L,
                                        diskUsed = s.diskUsed ?: 0L,
                                        diskTotal = s.diskTotal ?: 0L,
                                        uptimeSeconds = s.uptimeSeconds,
                                        sessionsTotal = s.sessionsTotal,
                                        sessionsRunning = s.sessionsRunning,
                                        sessionsWaiting = s.sessionsWaiting,
                                        gpuUtilPct = s.gpuUtilPct ?: s.gpuPct ?: 0.0,
                                        gpuTempC = s.gpuTemp ?: 0.0,
                                        gpuMemUsedMb = s.gpuMemUsedMb ?: 0L,
                                        gpuMemTotalMb = s.gpuMemTotalMb ?: 0L,
                                        gpuName = s.gpuName.orEmpty(),
                                    ),
                                )
                            }
                        }
                    }
                }
                delay(STATS_POLL_MS)
            }
        }
    }

    public fun stop() {
        runCatching {
            Wearable.getMessageClient(context).removeListener(messageListener)
        }
    }

    /**
     * Phone-side handler for the watch's voice-transcribe round-trip
     * (v0.35.8). Resolves the session's profile, posts the audio
     * blob to that server's `/api/voice/transcribe`, and replies on
     * `/datawatch/transcript` with `sessionId\n<text>` (or the cause
     * chain on failure prefixed with `error:`). Watch keeps the
     * confirm-then-send flow already in place from v0.35.5.
     */
    private suspend fun forwardWatchAudio(
        sourceNodeId: String,
        sessionId: String,
        audio: ByteArray,
    ) {
        val (text, error) =
            runCatching {
                val sessionRow =
                    ServiceLocator.sessionRepository
                        .observeForProfileAny(sessionId).first()
                val profiles = ServiceLocator.profileRepository.observeAll().first()
                val profile =
                    sessionRow?.serverProfileId
                        ?.let { pid -> profiles.firstOrNull { it.id == pid && it.enabled } }
                        ?: run {
                            val activeId = ServiceLocator.activeServerStore.get()
                            profiles.firstOrNull { it.id == activeId && it.enabled }
                                ?: profiles.firstOrNull { it.enabled }
                        }
                        ?: return@runCatching "" to "no enabled profile"
                ServiceLocator.transportFor(profile).transcribeAudio(
                    audio = audio,
                    audioMime = "audio/mp4",
                    sessionId = sessionId,
                    autoExec = false,
                ).fold(
                    onSuccess = { it.transcript.trim() to "" },
                    onFailure = { err ->
                        val cause =
                            generateSequence(err as Throwable?) { it.cause }
                                .take(3)
                                .joinToString(" ← ") {
                                    "${it::class.simpleName}: ${it.message?.take(120)}"
                                }
                        "" to cause
                    },
                )
            }.getOrElse { "" to (it.message ?: it::class.simpleName ?: "unknown") }

        // Reply payload: "sessionId\n<text>" on success,
        // "sessionId\nerror:<cause>" on failure.
        val payload =
            buildString {
                append(sessionId)
                append('\n')
                if (error.isEmpty()) append(text)
                else append("error:").append(error)
            }.toByteArray(Charsets.UTF_8)
        runCatching {
            Wearable.getMessageClient(context)
                .sendMessage(sourceNodeId, TRANSCRIPT_PATH, payload)
        }
    }

    /**
     * Open a transient WS subscription to [sessionId], emit a `send_input`
     * frame with [text], wait briefly for the server to ack, then cancel.
     *
     * The datawatch server rejects `POST /api/sessions/reply` (404), so the
     * only path for reply text is `send_input` over the WS hub. That hub
     * only accepts input while a subscriber is collecting the session's
     * events flow — so for watch-initiated replies we open + send + close.
     */
    private suspend fun forwardWatchReply(sessionId: String, text: String) {
        runCatching {
            val activeId = ServiceLocator.activeServerStore.get()
            if (activeId.isNullOrEmpty() || activeId == ActiveServerStore.SENTINEL_ALL_SERVERS) return
            val profile =
                ServiceLocator.profileRepository.observeAll().first()
                    .firstOrNull { it.id == activeId && it.enabled } ?: return
            val ws = ServiceLocator.wsTransportFor(profile)
            val collectorJob: Job =
                scope.launch {
                    ws.events(sessionId).collect {
                        // Drop inbound frames — we only need the subscriber
                        // slot so the outbound `send_input` frame has a
                        // writer attached to the hub.
                    }
                }
            // Give the WS time to connect + subscribe. The subscribe
            // frame is sent immediately after upgrade; the outbound
            // writer collects from WsOutbound.frames once subscribed.
            delay(WATCH_REPLY_SUBSCRIBE_GRACE_MS)
            WsOutbound.sendInput(sessionId, text)
            // Hold the WS open long enough for the frame to flush,
            // then cancel so we don't keep a socket open for nothing.
            delay(WATCH_REPLY_DRAIN_MS)
            collectorJob.cancel()
        }
    }

    private data class Snapshot(
        val serverId: String,
        val serverName: String,
        val running: Int,
        val waiting: Int,
        val total: Int,
    )

    private data class ProfilesSnapshot(
        val activeId: String,
        val ids: List<String>,
        val names: List<String>,
    )

    /**
     * Per-session snapshot published to `/datawatch/sessions`. Fields are
     * chosen so the watch can render a tap-to-reply popup without any
     * additional DataLayer fetch: title + state badge + a one-line
     * context preview (lastPrompt for waiting sessions, else
     * lastResponse / taskSummary). Keep individual strings short —
     * DataLayer items have a 100 KB total cap.
     */
    private data class SessionItem(
        val id: String,
        val title: String,
        val backend: String,
        val stateName: String,
        val lastLine: String,
    )

    private data class SessionsListSnapshot(
        val items: List<SessionItem>,
    )

    private data class StatsSnapshot(
        val cpuLoad1: Double,
        val cpuCores: Int,
        val memUsed: Long,
        val memTotal: Long,
        val diskUsed: Long,
        val diskTotal: Long,
        val uptimeSeconds: Long,
        val sessionsTotal: Int,
        val sessionsRunning: Int,
        val sessionsWaiting: Int,
        // v0.35.4 — GPU stats for the Wear Monitor redesign.
        val gpuUtilPct: Double,
        val gpuTempC: Double,
        val gpuMemUsedMb: Long,
        val gpuMemTotalMb: Long,
        val gpuName: String,
    )

    private fun publishCounts(snap: Snapshot) {
        runCatching {
            val req =
                PutDataMapRequest.create(COUNTS_PATH).apply {
                    dataMap.putString("serverId", snap.serverId)
                    dataMap.putString("serverName", snap.serverName)
                    dataMap.putInt("running", snap.running)
                    dataMap.putInt("waiting", snap.waiting)
                    dataMap.putInt("total", snap.total)
                    dataMap.putLong("ts", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }
    }

    private fun publishSessions(snap: SessionsListSnapshot) {
        runCatching {
            val req =
                PutDataMapRequest.create(SESSIONS_PATH).apply {
                    dataMap.putStringArray("ids", snap.items.map { it.id }.toTypedArray())
                    dataMap.putStringArray("titles", snap.items.map { it.title }.toTypedArray())
                    dataMap.putStringArray("backends", snap.items.map { it.backend }.toTypedArray())
                    dataMap.putStringArray("states", snap.items.map { it.stateName }.toTypedArray())
                    dataMap.putStringArray("lastLines", snap.items.map { it.lastLine }.toTypedArray())
                    dataMap.putLong("ts", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }
    }

    private fun publishProfiles(snap: ProfilesSnapshot) {
        runCatching {
            val req =
                PutDataMapRequest.create(PROFILES_PATH).apply {
                    dataMap.putString("activeId", snap.activeId)
                    dataMap.putStringArray("ids", snap.ids.toTypedArray())
                    dataMap.putStringArray("names", snap.names.toTypedArray())
                    dataMap.putLong("ts", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }
    }

    private fun publishStats(snap: StatsSnapshot) {
        runCatching {
            val req =
                PutDataMapRequest.create(STATS_PATH).apply {
                    dataMap.putDouble("cpuLoad1", snap.cpuLoad1)
                    dataMap.putInt("cpuCores", snap.cpuCores)
                    dataMap.putLong("memUsed", snap.memUsed)
                    dataMap.putLong("memTotal", snap.memTotal)
                    dataMap.putLong("diskUsed", snap.diskUsed)
                    dataMap.putLong("diskTotal", snap.diskTotal)
                    dataMap.putLong("uptimeSeconds", snap.uptimeSeconds)
                    dataMap.putInt("sessionsTotal", snap.sessionsTotal)
                    dataMap.putInt("sessionsRunning", snap.sessionsRunning)
                    dataMap.putInt("sessionsWaiting", snap.sessionsWaiting)
                    dataMap.putDouble("gpuUtilPct", snap.gpuUtilPct)
                    dataMap.putDouble("gpuTempC", snap.gpuTempC)
                    dataMap.putLong("gpuMemUsedMb", snap.gpuMemUsedMb)
                    dataMap.putLong("gpuMemTotalMb", snap.gpuMemTotalMb)
                    dataMap.putString("gpuName", snap.gpuName)
                    dataMap.putLong("ts", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(req)
        }
    }

    public companion object {
        public const val COUNTS_PATH: String = "/datawatch/counts"
        public const val PROFILES_PATH: String = "/datawatch/profiles"
        public const val STATS_PATH: String = "/datawatch/stats"
        public const val SESSIONS_PATH: String = "/datawatch/sessions"
        public const val SET_ACTIVE_PATH: String = "/datawatch/setActive"
        public const val REPLY_PATH: String = "/datawatch/reply"
        public const val AUDIO_PATH: String = "/datawatch/audio"
        public const val TRANSCRIPT_PATH: String = "/datawatch/transcript"
        public const val STATS_POLL_MS: Long = 15_000L
        // v0.35.5 watch-reply plumbing — see forwardWatchReply. These
        // are tuned conservatively: Ktor WS subscribe typically completes
        // in <300 ms on LAN, but self-signed TLS on first connect can
        // take ~1 s. Drain keeps the socket up just long enough for
        // the frame to flush through the write queue.
        public const val WATCH_REPLY_SUBSCRIBE_GRACE_MS: Long = 1_200L
        public const val WATCH_REPLY_DRAIN_MS: Long = 400L
        // Max sessions published to the watch list. Wearable DataItem
        // has a hard 100 KB limit per item — this cap keeps us well
        // under it even with long session titles + last-line snippets.
        public const val SESSIONS_PUBLISH_LIMIT: Int = 12
        public const val SESSION_LAST_LINE_MAX: Int = 160
    }
}
