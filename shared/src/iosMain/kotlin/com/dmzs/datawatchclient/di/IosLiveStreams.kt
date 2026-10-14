package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.ws.SessionsHub
import com.dmzs.datawatchclient.transport.ws.WsConnectionHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import platform.Foundation.NSLock

/**
 * One ref-counted global `/ws` per server profile, shared by every iOS caller
 * (alert feed, Sessions, Observer, Automata, Dashboard, local alert watcher).
 *
 * Before this each caller opened its own socket, so every multi-MB `sessions`
 * frame was received and decoded once per socket. Now the first subscriber
 * opens the socket, later ones join it, and it closes [STOP_TIMEOUT_MS] after
 * the last one leaves (`SharingStarted.WhileSubscribed`). The flow never emits —
 * callers collect it only to keep the socket open and read the hubs.
 *
 * Keyed by profile id; a profile whose URL / token / trust anchor changed gets a
 * fresh stream (the old one closes once its subscribers cancel).
 */
internal object IosGlobalStreams {
    private const val STOP_TIMEOUT_MS: Long = 5_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = NSLock()
    private val streams = HashMap<String, Pair<String, Flow<Unit>>>()

    private fun signature(profile: ServerProfile): String =
        profile.baseUrl + "|" + profile.bearerTokenRef + "|" + (profile.trustAnchorSha256 ?: "")

    /** The shared global stream for [profile]; collect it to keep the socket open. */
    fun stream(profile: ServerProfile): Flow<Unit> {
        IosSessionsLive.start()
        val sig = signature(profile)
        lock.lock()
        try {
            val existing = streams[profile.id]
            if (existing != null && existing.first == sig) return existing.second
            val shared: Flow<Unit> =
                IosServiceLocator
                    .wsTransportFor(profile)
                    .globalStream()
                    .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 0)
            streams[profile.id] = sig to shared
            return shared
        } finally {
            lock.unlock()
        }
    }
}

/**
 * Latest session list per server profile, built from the WS `sessions` frames
 * (full list) and `session_state` diffs (merged in by id) that reach
 * [SessionsHub] over the shared global sockets.
 *
 * Swift screens read sessions from here instead of re-fetching the multi-MB
 * `GET /api/sessions`: subscribing replays the cached list at once and then
 * delivers only lists whose content changed. REST stays a fallback for when
 * [isLive] is false (socket down, or no list received since it connected).
 */
public object IosSessionsLive {
    private data class Entry(
        val sessions: List<Session>,
        val receivedAtMs: Long,
        /** A full list arrived while the socket was open (cleared when it closes). */
        val wsFresh: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
    private val startLock = NSLock()
    private var started = false

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** Starts the hub collectors once (subscribed before any socket can deliver). */
    internal fun start() {
        startLock.lock()
        try {
            if (started) return
            started = true
        } finally {
            startLock.unlock()
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            SessionsHub.fullListFlow.collect { u -> put(u.serverProfileId, u.sessions) }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            SessionsHub.singleSessionFlow.collect { u -> upsert(u.serverProfileId, u.session) }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            WsConnectionHub.openSockets.collect { open: Map<String, Int> ->
                entries.update { m: Map<String, Entry> ->
                    if (m.none { (pid, e) -> e.wsFresh && (open[pid] ?: 0) <= 0 }) {
                        m
                    } else {
                        m.mapValues { (pid, e) -> if ((open[pid] ?: 0) > 0) e else e.copy(wsFresh = false) }
                    }
                }
            }
        }
    }

    private fun put(
        profileId: String,
        sessions: List<Session>,
    ) {
        val now = nowMs()
        entries.update { m -> m + (profileId to Entry(sessions, now, wsFresh = true)) }
    }

    /** A diff before any full list is dropped — a one-row "list" would look like every other session vanished. */
    private fun upsert(
        profileId: String,
        session: Session,
    ) {
        val now = nowMs()
        entries.update { m ->
            val e = m[profileId] ?: return@update m
            val i = e.sessions.indexOfFirst { it.id == session.id }
            val list =
                if (i >= 0) {
                    e.sessions.toMutableList().also { it[i] = session }
                } else {
                    e.sessions + session
                }
            m + (profileId to e.copy(sessions = list, receivedAtMs = now))
        }
    }

    /** Content-deduplicated session list for [profileId]; replays the cached one first. */
    internal fun listFlow(profileId: String): Flow<List<Session>> =
        entries
            .map { m -> m[profileId]?.sessions }
            .filterNotNull()
            .distinctUntilChanged { a, b -> a === b || a == b }

    /**
     * True while a global socket for [profileId] is open and has delivered a full
     * session list since it connected — WS data is authoritative, skip REST.
     */
    public fun isLive(profileId: String): Boolean =
        WsConnectionHub.isConnected(profileId) && entries.value[profileId]?.wsFresh == true

    /** Milliseconds since the last WS session data for [profileId]; -1 when none yet. */
    public fun ageMillis(profileId: String): Long {
        val e = entries.value[profileId] ?: return -1
        return (nowMs() - e.receivedAtMs).coerceAtLeast(0)
    }

    /** The cached list for [profileId] (null before the first WS `sessions` frame). */
    public fun latest(profileId: String): List<Session>? = entries.value[profileId]?.sessions

    /**
     * Keeps the shared global socket for [profile] open and delivers its session
     * list: the cached one immediately (when present), then each changed list.
     * Callbacks run on a background thread; cancel the handle when done.
     */
    public fun subscribe(
        profile: ServerProfile,
        onSessions: (List<Session>) -> Unit,
    ): IosSubscription {
        start()
        val job =
            scope.launch {
                launch { IosGlobalStreams.stream(profile).collect { } }
                launch { listFlow(profile.id).collect { onSessions(it) } }
            }
        return IosSubscription(job)
    }

    /**
     * One session of [profile] (matched by short or full id) from the shared WS
     * data, delivered when its content changes. Keeps the shared socket open.
     */
    public fun subscribeSession(
        profile: ServerProfile,
        sessionId: String,
        fullId: String,
        onSession: (Session) -> Unit,
    ): IosSubscription {
        start()
        val job =
            scope.launch {
                launch { IosGlobalStreams.stream(profile).collect { } }
                launch {
                    listFlow(profile.id)
                        .map { list -> list.firstOrNull { it.id == sessionId || it.fullId == fullId } }
                        .filterNotNull()
                        .distinctUntilChanged()
                        .collect { onSession(it) }
                }
            }
        return IosSubscription(job)
    }
}
