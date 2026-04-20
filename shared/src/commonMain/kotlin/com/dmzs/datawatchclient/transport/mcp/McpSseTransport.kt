package com.dmzs.datawatchclient.transport.mcp

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Streams MCP JSON-RPC frames from a datawatch server's SSE endpoint
 * (default port 8081 per parent docs/mcp.md). This is a transport-layer
 * primitive — higher-level code wraps it into tool-call semantics.
 *
 * Frame format (per W3C SSE):
 *   event: <name>\n
 *   data: <payload>\n
 *   \n
 *
 * We surface each complete event as an [McpEvent]. Connection retries use the
 * same exponential-backoff strategy as WebSocketTransport — Sprint 3 keeps it
 * minimal (no Last-Event-ID replay, no auto-resume); v0.5.0 adds those once
 * the parent confirms its SSE replay semantics.
 *
 * The endpoint and auth token live on [McpSseEndpoint] rather than
 * [ServerProfile] because MCP SSE is opt-in per profile and runs on a
 * separate port from the main REST API — we don't want to conflate the two
 * bearer tokens.
 */
public class McpSseTransport(
    private val endpoint: McpSseEndpoint,
    private val client: HttpClient,
    private val json: Json = RestTransport.DefaultJson,
) {
    public fun listen(): Flow<McpEvent> =
        callbackFlow {
            val producer = this
            var backoff = INITIAL_BACKOFF_MS
            while (!isClosedForSend) {
                try {
                    client.get(endpoint.sseUrl) {
                        endpoint.bearerToken?.let {
                            header(HttpHeaders.Authorization, "Bearer $it")
                        }
                        header(HttpHeaders.Accept, "text/event-stream")
                        header(HttpHeaders.CacheControl, "no-cache")
                    }.bodyAsChannel().let { channel ->
                        var event: String? = null
                        var data = StringBuilder()
                        while (!channel.isClosedForRead) {
                            val line = channel.readUTF8Line() ?: break
                            when {
                                line.isBlank() -> {
                                    if (data.isNotEmpty()) {
                                        val payload =
                                            runCatching {
                                                json.parseToJsonElement(data.toString())
                                            }.getOrNull()
                                        producer.trySend(
                                            McpEvent(
                                                name = event ?: "message",
                                                rawData = data.toString(),
                                                payload = payload,
                                            ),
                                        )
                                    }
                                    event = null
                                    data = StringBuilder()
                                }
                                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> {
                                    if (data.isNotEmpty()) data.append('\n')
                                    data.append(line.removePrefix("data:").trimStart())
                                }
                                // ":" = comment, ignore.
                            }
                        }
                    }
                    backoff = INITIAL_BACKOFF_MS
                } catch (e: Throwable) {
                    producer.trySend(
                        McpEvent(
                            name = "error",
                            rawData = e.message ?: "unknown",
                            payload = null,
                        ),
                    )
                }
                if (isClosedForSend) break
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
            awaitClose { }
        }

    public companion object {
        internal const val INITIAL_BACKOFF_MS: Long = 1_000
        internal const val MAX_BACKOFF_MS: Long = 60_000
    }
}

public data class McpSseEndpoint(
    val sseUrl: String,
    val bearerToken: String?,
)

public data class McpEvent(
    val name: String,
    val rawData: String,
    val payload: JsonElement?,
)
