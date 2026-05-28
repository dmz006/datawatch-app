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
 * @param trustAll reserved for future use — iOS currently uses ATS for certificate
 *   policy. For self-signed / private-CA servers configure NSExceptionDomains in
 *   Info.plist, or use a certificate issued by a public CA (Let's Encrypt).
 *   Per-profile SHA-256 pinning via SecTrustEvaluateWithError is tracked for v1.1.
 *
 * App Transport Security note: for HTTP (non-TLS) servers add the server's host
 * to NSExceptionDomains with NSExceptionAllowsInsecureHTTPLoads = true.
 */
public fun createHttpClientWithWebSockets(trustAll: Boolean = false): HttpClient =
    HttpClient(Darwin) {
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
