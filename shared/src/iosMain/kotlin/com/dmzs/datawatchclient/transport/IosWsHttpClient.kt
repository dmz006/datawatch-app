package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json

/**
 * iOS WS HttpClient. Trust-all is implemented by delegating to Darwin's
 * `challengeHandler` once the iOS content phase begins — for now the
 * parameter is accepted for API parity but has no effect (iOS app is
 * skeleton only through Sprint 6).
 */
public fun createHttpClientWithWebSockets(trustAll: Boolean = false): HttpClient =
    HttpClient(Darwin) {
        install(WebSockets)
        install(ContentNegotiation) { json(RestTransport.DefaultJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
        }
        expectSuccess = false
    }
