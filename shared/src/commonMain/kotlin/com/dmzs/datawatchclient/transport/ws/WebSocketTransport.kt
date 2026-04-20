package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.transport.dto.WsFrameDto
import com.dmzs.datawatchclient.transport.dto.WsSubscribeDto
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
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun events(sessionId: String): Flow<SessionEvent> = callbackFlow {
        val producer = this
        // Critical protocol correction (v1.0.3): datawatch's /ws is a hub —
        // no query-param filter. After upgrade we send a `subscribe` frame.
        val wsUrl = buildWsUrl(profile.baseUrl)
        var backoff = INITIAL_BACKOFF_MS

        println("WsTransport: stream start for $sessionId → $wsUrl (trustAll=${profile.trustAnchorSha256 == "ALLOW_ALL_INSECURE"})")

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
                    println("WsTransport: connected $wsUrl; sending subscribe($sessionId)")
                    // Send subscribe immediately after upgrade — the server
                    // registers our client in its hub with no output stream
                    // until we opt in.
                    val subscribeFrame = buildJsonObject {
                        put("type", "subscribe")
                        put(
                            "data",
                            buildJsonObject { put("session_id", sessionId) },
                        )
                    }
                    send(json.encodeToString(JsonObject.serializer(), subscribeFrame))

                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                val dto = runCatching {
                                    json.decodeFromString(WsFrameDto.serializer(), text)
                                }.getOrNull()
                                if (dto == null) {
                                    println("WsTransport: unparseable frame: ${text.take(120)}")
                                    continue
                                }
                                val events = dto.toDomainEvents(sessionId)
                                if (events.isNotEmpty()) {
                                    for (ev in events) producer.trySend(ev)
                                }
                            }
                            is Frame.Close -> {
                                println("WsTransport: server closed WS")
                                return@webSocket
                            }
                            else -> { /* ignore binary / ping / pong */ }
                        }
                    }
                }
                backoff = INITIAL_BACKOFF_MS
            } catch (e: Throwable) {
                println("WsTransport: $wsUrl failed: ${e::class.simpleName}: ${e.message}")
                runCatching {
                    producer.trySend(
                        SessionEvent.Error(
                            sessionId = sessionId,
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

    /** Converts `https://host:port` → `wss://host:port/ws` (and http→ws). */
    internal fun buildWsUrl(baseUrl: String): String {
        val base = Url(baseUrl)
        val wsScheme = if (base.protocol.name == "https") "wss" else "ws"
        val port = if (base.port == base.protocol.defaultPort) "" else ":${base.port}"
        return "$wsScheme://${base.host}$port/ws"
    }
}

// Preserve the old signature for test compatibility (takes sessionId but
// ignores it — the protocol rewrite moved filtering into EventMapper).
internal fun buildWsUrl(baseUrl: String, @Suppress("UNUSED_PARAMETER") sessionId: String): String {
    val base = Url(baseUrl)
    val wsScheme = if (base.protocol.name == "https") "wss" else "ws"
    val port = if (base.port == base.protocol.defaultPort) "" else ":${base.port}"
    return "$wsScheme://${base.host}$port/ws"
}
