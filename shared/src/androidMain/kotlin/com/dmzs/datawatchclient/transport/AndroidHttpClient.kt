package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

public actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 5_000
        socketTimeoutMillis = 30_000
    }
    install(ContentNegotiation) { json(RestTransport.DefaultJson) }
    expectSuccess = true
}

/**
 * Variant of [createHttpClient] that accepts ANY TLS certificate and any
 * hostname. Used when the user has flagged a server profile as self-signed —
 * the `selfSigned` checkbox on AddServerScreen becomes a real trust-override
 * here. Callers must only pass `true` when the user opted in; this disables
 * every TLS identity guarantee.
 *
 * Risk profile: a MITM on the path between the phone and the user's datawatch
 * server can present any certificate and we'll accept it. For a user's
 * personal tailnet this is acceptable. Do NOT use for public-internet servers.
 */
public fun createTrustAllHttpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustAll), SecureRandom())
            }
            sslSocketFactory(ctx.socketFactory, trustAll)
            hostnameVerifier { _, _ -> true }
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
