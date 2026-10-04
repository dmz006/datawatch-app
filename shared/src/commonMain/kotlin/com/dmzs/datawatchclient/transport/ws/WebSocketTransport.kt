package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.WsFrameDto
import com.dmzs.datawatchclient.transport.dto.WsSessionsFrameDataDto
import com.dmzs.datawatchclient.transport.dto.WsSessionStateFrameDataDto
import com.dmzs.datawatchclient.transport.rest.toDomain
import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Streams [SessionEvent]s over `wss://<host>/ws` per the datawatch hub
 * protocol. After the TLS+upgrade handshake we send a `subscribe` frame
 * naming the session id; the server then pushes typed JSON frames
 * (`raw_output`, `needs_input`, `alert`, etc.) that [toDomainEvents]
 * translates.
 *
 * Auto-reconnects with jittered exponential backoff. Per ADR-0013 the
 * client does not queue writes while disconnected; replies go via REST.
 *
 * @param client a pre-configured Ktor client with the WebSockets plugin installed
 * @param tokenProvider optional bearer-token resolver; `null` omits auth (ADR-0004
 *   "no bearer token" opt-in)
 */
public class WebSocketTransport(
    public val profile: ServerProfile,
    private val client: HttpClient,
    private val tokenProvider: (suspend () -> String)? = null,
    private val json: Json = RestTransport.DefaultJson,
) {
    public companion object {
        public const val INITIAL_BACKOFF_MS: Long = 500L
        public const val MAX_BACKOFF_MS: Long = 60_000L
        public const val BACKOFF_JITTER_MS: Long = 500L
    }

    /**
     * Stream events for a given session. On disconnect the stream does not
     * end — it emits a [SessionEvent.Error], waits exponential backoff +
     * jitter, then reconnects. Cancelling the coroutine that is collecting
     * this Flow stops reconnection cleanly.
     *
     * @param subscriptionId the session ID to send in the subscribe frame to the server
     *   (typically the full "hostname-shortid" format)
     * @param storageId the session ID to use when storing events (typically the short ID).
     *   Defaults to subscriptionId if not provided.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun events(
        subscriptionId: String,
        storageId: String = subscriptionId,
    ): Flow<SessionEvent> =
        callbackFlow {
            val producer = this
            // Critical protocol correction (v1.0.3): datawatch's /ws is a hub —
            // no query-param filter. After upgrade we send a `subscribe` frame.
            val wsUrl = buildWsUrl(profile.baseUrl)
            var backoff = INITIAL_BACKOFF_MS

            println(
                "WsTransport: stream start for $storageId (subscribe as $subscriptionId) → $wsUrl (trustAll=${profile.trustAnchorSha256 == "ALLOW_ALL_INSECURE"})",
            )

            while (!isClosedForSend) {
                val bearerHeader = tokenProvider?.invoke()?.let { "Bearer $it" }
                println("WsTransport: connecting $wsUrl auth=${if (bearerHeader != null) "yes" else "no"}")
                try {
                    client.webSocket(
                        urlString = wsUrl,
                        request = {
                            bearerHeader?.let { header(HttpHeaders.Authorization, it) }
                        },
                    ) {
                        println("WsTransport: connected $wsUrl; sending subscribe($subscriptionId)")
                        // Send subscribe immediately after upgrade — the server
                        // registers our client in its hub with no output stream
                        // until we opt in.
                        val subscribeFrame =
                            buildJsonObject {
                                put("type", "subscribe")
                                put(
                                    "data",
                                    buildJsonObject { put("session_id", subscriptionId) },
                                )
                            }
                        send(json.encodeToString(JsonObject.serializer(), subscribeFrame))

                        // Outbound relay — forward any WsOutbound frames
                        // tagged for this session to the open WS connection.
                        // `resize_term`, `send_input`, `command` (scroll-mode,
                        // sendkey) all reach the server through this hook.
                        // Matches PWA's app.js `send(...)` helper.
                        val writerJob =
                            launch {
                                WsOutbound.frames
                                    .filter { it.sessionId == storageId }
                                    .collect { env -> send(env.text) }
                            }

                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    val dto =
                                        runCatching {
                                            json.decodeFromString(WsFrameDto.serializer(), text)
                                        }.getOrNull()
                                    if (dto == null) {
                                        println("WsTransport: unparseable frame: ${text.take(120)}")
                                        continue
                                    }
                                    // B10: stats frames are global (not
                                    // session-scoped) — route to StatsHub
                                    // so StatsViewModel gets live updates
                                    // from any active session WS.
                                    if (dto.type == "stats") {
                                        tryRouteStatsFrame(dto.data, json)
                                        continue
                                    }
                                    // #178: prd_update frames are global —
                                    // route to PrdHub so AutonomousViewModel
                                    // can patch detail in-place without flicker.
                                    if (dto.type == "prd_update") {
                                        tryRoutePrdUpdateFrame(dto.data, json)
                                        continue
                                    }
                                    // #204: sessions (full-list) and session_state
                                    // (single-row diff, v8.37.0+) are global — route
                                    // to SessionsHub so SessionsViewModel can drop
                                    // REST polling in favour of event-driven updates.
                                    if (dto.type == "sessions") {
                                        tryRouteSessionsFrame(dto.data, json, profile.id)
                                        continue
                                    }
                                    if (dto.type == "session_state") {
                                        tryRouteSessionStateFrame(dto.data, json, profile.id)
                                        continue
                                    }
                                    // Channel tab: channel_reply / channel_notify are
                                    // broadcast — route to ChannelHub.
                                    if (dto.type == "channel_reply" || dto.type == "channel_notify") {
                                        tryRouteChannelFrame(dto.type, dto.data, dto.timestamp)
                                        continue
                                    }
                                    // v0.33.19: trace every inbound frame
                                    // type + count mapped → events, so we
                                    // can see when pane_captures arrive but
                                    // get filtered by EventMapper's
                                    // session-id check (B27 live-update
                                    // investigation).
                                    val events = dto.toDomainEvents(subscriptionId, storageId)
                                    println(
                                        "WsTransport: rx type=${dto.type} " +
                                            "mapped=${events.size} bytes=${text.length}",
                                    )
                                    if (events.isNotEmpty()) {
                                        for (ev in events) producer.trySend(ev)
                                    }
                                }
                                is Frame.Close -> {
                                    println("WsTransport: server closed WS")
                                    writerJob.cancel()
                                    return@webSocket
                                }
                                else -> { /* ignore binary / ping / pong */ }
                            }
                        }
                        writerJob.cancel()
                    }
                    backoff = INITIAL_BACKOFF_MS
                } catch (e: Throwable) {
                    // Include cause chain — Ktor wraps OkHttp's real failure
                    // inside a generic CancellationException / CloseReason, so
                    // the inner cause is what tells us whether it was a
                    // server-close (4xxx code), a ping timeout, or
                    // OkHttp-level EOFException on read. Needed to diagnose
                    // the v0.33.x "WS re-connects every few seconds even
                    // though the server is healthy" reports.
                    val chain =
                        generateSequence(e as Throwable?) { it.cause }
                            .take(4)
                            .joinToString(" ← ") { "${it::class.simpleName}: ${it.message?.take(80)}" }
                    println("WsTransport: $wsUrl failed — $chain")
                    runCatching {
                        producer.trySend(
                            SessionEvent.Error(
                                sessionId = storageId,
                                ts = Clock.System.now(),
                                message = "WS ${e::class.simpleName}: ${e.message?.take(120) ?: "no message"}",
                            ),
                        )
                    }
                }

                if (isClosedForSend) break
                val wait = backoff + Random.nextLong(0, BACKOFF_JITTER_MS)
                delay(wait)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
            awaitClose()
        }

    /**
     * Opens a persistent WS connection that routes only global frames to their
     * hubs ([SessionsHub], [StatsHub], [PrdHub]) without subscribing to any
     * session. Intended for [SessionsViewModel] so it receives real-time
     * session-list pushes even when no session detail is open.
     *
     * The returned flow never emits — callers should `launchIn` and read from
     * the relevant hub. Reconnects with jittered exponential backoff until the
     * collecting coroutine is cancelled.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun globalStream(): Flow<Unit> =
        callbackFlow {
            val wsUrl = buildWsUrl(profile.baseUrl)
            var backoff = INITIAL_BACKOFF_MS
            println("WsTransport[global]: stream start → $wsUrl")
            while (!isClosedForSend) {
                val bearerHeader = tokenProvider?.invoke()?.let { "Bearer $it" }
                try {
                    client.webSocket(
                        urlString = wsUrl,
                        request = { bearerHeader?.let { header(HttpHeaders.Authorization, it) } },
                    ) {
                        println("WsTransport[global]: connected $wsUrl")
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    val dto = runCatching {
                                        json.decodeFromString(WsFrameDto.serializer(), text)
                                    }.getOrNull() ?: continue
                                    when (dto.type) {
                                        "stats" -> tryRouteStatsFrame(dto.data, json)
                                        "prd_update" -> tryRoutePrdUpdateFrame(dto.data, json)
                                        "sessions" -> tryRouteSessionsFrame(dto.data, json, profile.id)
                                        "session_state" -> tryRouteSessionStateFrame(dto.data, json, profile.id)
                                        "channel_reply", "channel_notify" -> tryRouteChannelFrame(dto.type, dto.data, dto.timestamp)
                                        "hook_update" -> HookHub.route(dto.data, profile.id)
                                    }
                                }
                                is Frame.Close -> {
                                    println("WsTransport[global]: server closed WS")
                                    return@webSocket
                                }
                                else -> {}
                            }
                        }
                    }
                    backoff = INITIAL_BACKOFF_MS
                } catch (e: Throwable) {
                    println("WsTransport[global]: $wsUrl failed — ${e.message?.take(80)}")
                }
                if (isClosedForSend) break
                delay(backoff + Random.nextLong(0, BACKOFF_JITTER_MS))
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
            awaitClose()
        }

    /** Converts `https://host:port` → `wss://host:port/ws` (and http→ws). */
    internal fun buildWsUrl(baseUrl: String): String {
        val base = Url(baseUrl)
        val wsScheme = if (base.protocol.name == "https") "wss" else "ws"
        val port = if (base.port == base.protocol.defaultPort) "" else ":${base.port}"
        return "$wsScheme://${base.host}$port/ws"
    }
}

/** Parse a `stats` WS frame and forward to [StatsHub] (B10). */
private fun tryRouteStatsFrame(
    data: kotlinx.serialization.json.JsonElement?,
    json: Json,
) {
    if (data == null) return
    runCatching {
        val dto = json.decodeFromJsonElement(com.dmzs.datawatchclient.transport.dto.StatsDto.serializer(), data)
        StatsHub.emit(dto)
    }.onFailure { println("WsTransport: failed to parse stats frame: ${it.message}") }
}

/** Parse a `prd_update` WS frame and forward to [PrdHub] (#178). */
private fun tryRoutePrdUpdateFrame(
    data: kotlinx.serialization.json.JsonElement?,
    json: Json,
) {
    if (data == null) return
    runCatching {
        val dto = json.decodeFromJsonElement(PrdDto.serializer(), data)
        PrdHub.emit(dto)
    }.onFailure { println("WsTransport: failed to parse prd_update frame: ${it.message}") }
}

/** Parse a `sessions` WS frame and forward to [SessionsHub] (#204). */
private fun tryRouteSessionsFrame(
    data: kotlinx.serialization.json.JsonElement?,
    json: Json,
    profileId: String,
) {
    if (data == null) return
    runCatching {
        val payload = json.decodeFromJsonElement(WsSessionsFrameDataDto.serializer(), data)
        val sessions = payload.sessions?.map { it.toDomain(profileId) } ?: return
        SessionsHub.emitFullList(SessionsUpdate(profileId, sessions))
    }.onFailure { println("WsTransport: failed to parse sessions frame: ${it.message}") }
}

/** Parse a `session_state` WS frame and forward to [SessionsHub] (#204). */
private fun tryRouteSessionStateFrame(
    data: kotlinx.serialization.json.JsonElement?,
    json: Json,
    profileId: String,
) {
    if (data == null) return
    runCatching {
        val payload = json.decodeFromJsonElement(WsSessionStateFrameDataDto.serializer(), data)
        val session = payload.session?.toDomain(profileId) ?: return
        SessionsHub.emitSingle(SessionStateUpdate(profileId, session))
    }.onFailure { println("WsTransport: failed to parse session_state frame: ${it.message}") }
}

// Preserve the old signature for test compatibility (takes sessionId but
// ignores it — the protocol rewrite moved filtering into EventMapper).
internal fun buildWsUrl(
    baseUrl: String,
    @Suppress("UNUSED_PARAMETER") sessionId: String,
): String {
    val base = Url(baseUrl)
    val wsScheme = if (base.protocol.name == "https") "wss" else "ws"
    val port = if (base.port == base.protocol.defaultPort) "" else ":${base.port}"
    return "$wsScheme://${base.host}$port/ws"
}
