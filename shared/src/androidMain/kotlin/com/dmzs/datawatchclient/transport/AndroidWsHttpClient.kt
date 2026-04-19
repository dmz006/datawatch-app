package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Ktor HttpClient with the WebSockets plugin installed — used by
 * [com.dmzs.datawatchclient.transport.ws.WebSocketTransport]. Separate from
 * the REST-only client because WebSockets requires the plugin and a
 * slightly different timeout profile (long-lived connections).
 *
 * @param trustAll when true, installs an accept-anything X509TrustManager
 *   + hostname verifier bypass. Only used when the user-owned server
 *   profile has `trustAnchorSha256 == TRUST_ALL_SENTINEL`.
 */
public fun createHttpClientWithWebSockets(trustAll: Boolean = false): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                // WS needs a long read timeout because frames can be far apart.
                readTimeout(60L, java.util.concurrent.TimeUnit.MINUTES)
                pingInterval(30L, java.util.concurrent.TimeUnit.SECONDS)
                if (trustAll) {
                    val tm = object : X509TrustManager {
                        override fun checkClientTrusted(
                            chain: Array<X509Certificate>, authType: String,
                        ) = Unit
                        override fun checkServerTrusted(
                            chain: Array<X509Certificate>, authType: String,
                        ) = Unit
                        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    }
                    val ctx = SSLContext.getInstance("TLS").apply {
                        init(null, arrayOf(tm), SecureRandom())
                    }
                    sslSocketFactory(ctx.socketFactory, tm)
                    hostnameVerifier { _, _ -> true }
                }
            }
        }
        install(WebSockets)
        install(ContentNegotiation) { json(RestTransport.DefaultJson) }
        install(HttpTimeout) {
            // Connect must be quick; read is effectively ping-governed
            connectTimeoutMillis = 5_000
        }
        expectSuccess = false  // WS upgrade handling returns non-2xx; let Ktor manage
    }
