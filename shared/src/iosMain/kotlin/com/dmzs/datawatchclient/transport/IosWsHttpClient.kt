package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust

/**
 * iOS Ktor HttpClient with WebSockets installed. Mirrors [AndroidWsHttpClient].
 *
 * @param trustAll when true, accepts any server certificate (the iOS analogue of
 *   Android's accept-all X509TrustManager). Only selected when the profile has
 *   `trustAnchorSha256 == TRUST_ALL_SENTINEL`. The preferred path for self-signed
 *   servers is installing the server CA on the device, which URLSession honours
 *   with no exception here. Per-profile SHA-256 pinning is the planned follow-up.
 */
public fun createHttpClientWithWebSockets(trustAll: Boolean = false): HttpClient =
    HttpClient(Darwin) {
        if (trustAll) {
            engine {
                handleChallenge { _, _, challenge, completionHandler ->
                    val trust = challenge.protectionSpace.serverTrust
                    if (trust != null) {
                        completionHandler(
                            NSURLSessionAuthChallengeUseCredential,
                            NSURLCredential.credentialForTrust(trust),
                        )
                    } else {
                        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                    }
                }
            }
        }
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
