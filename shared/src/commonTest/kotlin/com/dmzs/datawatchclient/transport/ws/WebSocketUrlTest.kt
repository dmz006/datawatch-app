package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.ServerProfile
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

class WebSocketUrlTest {

    private fun profile(base: String) = ServerProfile(
        id = "p1",
        displayName = "t",
        baseUrl = base,
        bearerTokenRef = "",
        reachabilityProfileId = "lan",
        createdTs = 0L,
    )

    private fun transport(base: String): WebSocketTransport =
        WebSocketTransport(profile(base), HttpClient())

    @Test
    fun `https base becomes wss URL with explicit port preserved`() {
        val url = transport("https://ralfthewise:8443").buildWsUrl(
            "https://ralfthewise:8443", "a3f2",
        )
        assertEquals("wss://ralfthewise:8443/ws?session=a3f2", url)
    }

    @Test
    fun `http base becomes ws URL (cleartext path for LAN)`() {
        val url = transport("http://laptop.local:8080").buildWsUrl(
            "http://laptop.local:8080", "b4c9",
        )
        assertEquals("ws://laptop.local:8080/ws?session=b4c9", url)
    }

    @Test
    fun `default https port 443 is omitted from ws URL`() {
        val url = transport("https://dw.example.com").buildWsUrl(
            "https://dw.example.com", "x1",
        )
        assertEquals("wss://dw.example.com/ws?session=x1", url)
    }

    @Test
    fun `default http port 80 is omitted`() {
        val url = transport("http://dw.example.com").buildWsUrl(
            "http://dw.example.com", "x1",
        )
        assertEquals("ws://dw.example.com/ws?session=x1", url)
    }
}
