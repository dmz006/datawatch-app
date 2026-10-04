package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json

/**
 * iOS Ktor HttpClient with WebSockets installed. Mirrors [AndroidWsHttpClient].
 *
 * TLS policy lives in [IosTls.challengeHandler]: system validation by default,
 * per-host leaf-certificate pins from the server profile, or accept-all when
 * [trustAll] (profile sentinel; insecure, parity with Android's trust manager).
 */
public fun createHttpClientWithWebSockets(trustAll: Boolean = false): HttpClient =
    HttpClient(Darwin) {
        engine { handleChallenge(IosTls.challengeHandler(trustAll)) }
        install(WebSockets) {
            pingInterval = 30_000
        }
        install(ContentNegotiation) { json(RestTransport.DefaultJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            // Long.MAX_VALUE lets Darwin's URLSession ping mechanism manage liveness.
            requestTimeoutMillis = Long.MAX_VALUE
            socketTimeoutMillis = Long.MAX_VALUE
        }
        expectSuccess = false
    }
