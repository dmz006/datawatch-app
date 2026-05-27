package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust

/**
 * iOS Ktor HttpClient with WebSockets installed. Mirrors [AndroidWsHttpClient].
 *
 * @param trustAll when true, installs a Darwin challenge handler that accepts
 *   any SSL certificate. Only used when the server profile has
 *   trustAnchorSha256 == TRUST_ALL_SENTINEL (user explicitly opted in for a
 *   self-signed or private-CA server). See ADR note in AndroidWsHttpClient.
 *
 * App Transport Security note: for HTTP (non-TLS) servers the Info.plist
 * NSExceptionDomains entry is required; see docs/transports.md § iOS ATS.
 */
public fun createHttpClientWithWebSockets(trustAll: Boolean = false): HttpClient =
    HttpClient(Darwin) {
        engine {
            if (trustAll) {
                // Accept any server certificate. Mirror of Android's blanket
                // X509TrustManager bypass — used only for user-owned servers where
                // the user has explicitly trusted the certificate in Settings.
                // Per-profile SHA-256 pinning (Task 2.3.1 future refinement) will
                // replace this once the iOS Settings UI is built in Story 7.
                handleChallenge { session, task, challenge, completionHandler ->
                    if (challenge.protectionSpace.authenticationMethod ==
                        NSURLAuthenticationMethodServerTrust
                    ) {
                        val credential = challenge.protectionSpace.serverTrust?.let {
                            NSURLCredential.credentialForTrust(it)
                        }
                        if (credential != null) {
                            completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
                        } else {
                            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                        }
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
