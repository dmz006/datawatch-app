package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

public actual fun createHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                // Keep connections alive for 10 minutes — the sessions list polls every
                // few seconds so the TCP+TLS connection to the Tailscale server is warm
                // by the time the detail screen opens, avoiding a cold-connect RTT.
                connectionPool(
                    okhttp3.ConnectionPool(5, 10, java.util.concurrent.TimeUnit.MINUTES),
                )
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) { json(RestTransport.DefaultJson) }
        expectSuccess = true
    }

/**
 * Variant of [createHttpClient] for a server profile whose user explicitly
 * opted in to "Trust all certificates (insecure)" (profile `trustAnchorSha256`
 * == trust-all sentinel; the server form shows a warning). Certificate and
 * hostname validation are skipped ONLY for [allowedHost] — the opted-in
 * profile's host. Any other host this client might reach (e.g. via a redirect)
 * gets full platform validation. See AndroidTrustAll.kt.
 *
 * Risk profile: a MITM on the path between the phone and that one server can
 * present any certificate and we'll accept it. Prefer certificate pinning
 * ([createPinnedHttpClient]) for self-signed servers.
 */
public fun createTrustAllHttpClient(allowedHost: String): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                connectionPool(
                    okhttp3.ConnectionPool(5, 10, java.util.concurrent.TimeUnit.MINUTES),
                )
                val tm = HostScopedTrustAllManager(allowedHost)
                sslSocketFactory(hostScopedTrustAllContext(tm).socketFactory, tm)
                hostnameVerifier(hostScopedHostnameVerifier(allowedHost, okhttp3.internal.tls.OkHostnameVerifier))
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) { json(RestTransport.DefaultJson) }
        expectSuccess = true
    }
