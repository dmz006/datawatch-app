package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.transport.dto.WsFrameDto
import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Streams [SessionEvent]s over `wss://<host>/ws?session=<id>` with automatic
 * exponential-backoff reconnection. The returned Flow is cold — one
 * WebSocket per collector — and cancelling the collecting scope cleanly
 * closes the socket.
 *
 * Per ADR-0013 the client does not queue writes while disconnected; this
 * transport is receive-only. Replies go via `RestTransport.replyToSession`.
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
        val wsUrl = buildWsUrl(profile.baseUrl, sessionId)
        var backoff = INITIAL_BACKOFF_MS

        while (!isClosedForSend) {
            // Resolve the bearer token once per (re)connect attempt in the
            // suspend scope — the Ktor request-builder lambda below is NOT
            // suspend so it cannot invoke tokenProvider directly.
            val bearerHeader = tokenProvider?.invoke()?.let { "Bearer $it" }

            try {
                client.webSocket(
                    urlString = wsUrl,
                    request = {
                        bearerHeader?.let { header(HttpHeaders.Authorization, it) }
                    },
                ) {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                val parsed = runCatching {
                                    json.decodeFromString(WsFrameDto.serializer(), text)
                                }.getOrNull()
                                val event = parsed?.toDomain(sessionId)
                                    ?: SessionEvent.Error(
                                        sessionId = sessionId,
                                        ts = Clock.System.now(),
                                        message = "unparseable frame: ${text.take(80)}",
                                    )
                                producer.trySend(event)
                            }
                            is Frame.Close -> return@webSocket
                            else -> { /* ignore binary / ping / pong */ }
                        }
                    }
                }
                // Clean close — reset backoff before reconnecting.
                backoff = INITIAL_BACKOFF_MS
            } catch (e: Throwable) {
                // Surface structured error without killing the stream; retry loop
                // picks it up after backoff.
                runCatching {
                    producer.trySend(
                        SessionEvent.Error(
                            sessionId = sessionId,
                            ts = Clock.System.now(),
                            message = "WebSocket disconnected: ${e.message}",
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

    /** Converts `https://host:port` → `wss://host:port/ws?session=…` (and http→ws). */
    internal fun buildWsUrl(baseUrl: String, sessionId: String): String {
        val base = Url(baseUrl)
        val wsScheme = if (base.protocol.name == "https") "wss" else "ws"
        val port = if (base.port == base.protocol.defaultPort) "" else ":${base.port}"
        return "$wsScheme://${base.host}$port/ws?session=$sessionId"
    }
}
