package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json

/**
 * Ktor HttpClient with the WebSockets plugin installed — used by
 * [com.dmzs.datawatchclient.transport.ws.WebSocketTransport]. Separate from
 * the REST-only client because WebSockets requires the plugin and a
 * slightly different timeout profile (long-lived connections).
 *
 * @param trustAllHost when non-null, certificate + hostname validation are
 *   skipped for exactly this host (every other host keeps platform
 *   validation). Only passed when the user-owned server profile opted in
 *   (`trustAnchorSha256 == TRUST_ALL_SENTINEL`). See AndroidTrustAll.kt.
 * @param pinSha256 when non-null (and [trustAllHost] is null), accept exactly the
 *   leaf certificate with this SHA-256 (parity D91a); hostname verification
 *   stays at OkHttp's default.
 */
public fun createHttpClientWithWebSockets(
    trustAllHost: String? = null,
    pinSha256: String? = null,
): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                // WS needs a long read timeout because frames can be far apart.
                readTimeout(60L, java.util.concurrent.TimeUnit.MINUTES)
                pingInterval(30L, java.util.concurrent.TimeUnit.SECONDS)
                // OkHttp connection pool eviction is aggressive on mobile; disable
                // keep-alive for the WS client so a stale route after a first
                // connect doesn't get reused on reconnect attempts.
                retryOnConnectionFailure(true)
                if (trustAllHost != null) {
                    val tm = HostScopedTrustAllManager(trustAllHost)
                    sslSocketFactory(hostScopedTrustAllContext(tm).socketFactory, tm)
                    hostnameVerifier(hostScopedHostnameVerifier(trustAllHost, okhttp3.internal.tls.OkHostnameVerifier))
                } else if (pinSha256 != null) {
                    val tm = PinnedTrustManager(pinSha256)
                    sslSocketFactory(pinnedSslContext(tm).socketFactory, tm)
                }
            }
        }
        install(WebSockets) {
            pingInterval = 30_000
        }
        install(ContentNegotiation) { json(RestTransport.DefaultJson) }
        install(HttpTimeout) {
            // 5 s is enough for Tailscale connections — halves the wait on a
            // failed first attempt before the 500ms-backoff retry fires.
            connectTimeoutMillis = 5_000
            // Critical: Ktor's default requestTimeoutMillis cuts long-lived
            // WebSockets after ~15 s. Setting these to Long.MAX_VALUE lets
            // the OkHttp ping mechanism manage liveness instead.
            requestTimeoutMillis = Long.MAX_VALUE
            socketTimeoutMillis = Long.MAX_VALUE
        }
        expectSuccess = false // WS upgrade handling returns non-2xx; let Ktor manage
    }
