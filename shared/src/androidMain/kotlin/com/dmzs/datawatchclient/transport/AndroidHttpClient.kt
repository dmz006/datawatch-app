package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.rest.RestTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

public actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 5_000
        socketTimeoutMillis = 30_000
    }
    install(ContentNegotiation) { json(RestTransport.DefaultJson) }
    expectSuccess = true
}
