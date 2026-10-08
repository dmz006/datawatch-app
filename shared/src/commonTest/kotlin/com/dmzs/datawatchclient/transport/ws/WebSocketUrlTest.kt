package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.ServerProfile
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

class WebSocketUrlTest {
    private fun profile(base: String) =
        ServerProfile(
            id = "p1",
            displayName = "t",
            baseUrl = base,
            bearerTokenRef = "",
            reachabilityProfileId = "lan",
            createdTs = 0L,
        )

    private fun transport(base: String): WebSocketTransport = WebSocketTransport(profile(base), HttpClient())

    @Test
    fun `https base becomes wss URL with explicit port preserved`() {
        assertEquals(
            "wss://datawatch.example:8443/ws",
            transport("https://datawatch.example:8443").buildWsUrl("https://datawatch.example:8443"),
        )
    }

    @Test
    fun `http base becomes ws URL - cleartext path for LAN`() {
        assertEquals(
            "ws://laptop.local:8080/ws",
            transport("http://laptop.local:8080").buildWsUrl("http://laptop.local:8080"),
        )
    }

    @Test
    fun `default https port 443 is omitted from ws URL`() {
        assertEquals(
            "wss://dw.example.com/ws",
            transport("https://dw.example.com").buildWsUrl("https://dw.example.com"),
        )
    }

    @Test
    fun `default http port 80 is omitted`() {
        assertEquals(
            "ws://dw.example.com/ws",
            transport("http://dw.example.com").buildWsUrl("http://dw.example.com"),
        )
    }

    @Test
    fun `proxied remote base keeps its path - PWA proxy WS shape`() {
        assertEquals(
            "wss://dw.example.com:8443/api/proxy/demo/ws",
            transport("https://dw.example.com:8443/api/proxy/demo")
                .buildWsUrl("https://dw.example.com:8443/api/proxy/demo"),
        )
    }

    @Test
    fun `trailing slash on base path is trimmed`() {
        assertEquals(
            "ws://dw.example.com/api/proxy/demo/ws",
            transport("http://dw.example.com/api/proxy/demo/").buildWsUrl("http://dw.example.com/api/proxy/demo/"),
        )
        assertEquals(
            "wss://dw.example.com/ws",
            transport("https://dw.example.com/").buildWsUrl("https://dw.example.com/"),
        )
    }

    @Test
    fun `legacy two-arg builder keeps the base path too`() {
        assertEquals(
            "wss://dw.example.com/api/proxy/a%20b/ws",
            buildWsUrl("https://dw.example.com/api/proxy/a%20b", "ignored"),
        )
    }
}
