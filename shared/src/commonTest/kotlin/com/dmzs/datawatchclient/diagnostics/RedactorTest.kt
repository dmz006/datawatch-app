package com.dmzs.datawatchclient.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactorTest {
    private fun r(s: String) = Redactor.redact(s)

    @Test
    fun authorizationHeaderValueIsRemoved() {
        assertEquals("Authorization: <redacted>", r("Authorization: Bearer abc.def"))
        assertEquals("proxy-authorization=<redacted>", r("proxy-authorization=Basic dXNlcjpwYXNz"))
        assertEquals("{\"Authorization\": <redacted>", r("{\"Authorization\": \"Bearer x\"}"))
        // Only the header line is touched.
        assertEquals("Authorization: <redacted>\nnext line", r("Authorization: secret\nnext line"))
    }

    @Test
    fun bearerTokensAreRemoved() {
        assertEquals("sent Bearer <redacted> ok", r("sent Bearer s3cr3t-T0ken_value= ok"))
        assertEquals("Bearer <redacted>", r("bearer abc"))
    }

    @Test
    fun jwtIsRemoved() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig_part-1"
        assertEquals("jwt <token> end", r("jwt $jwt end"))
        assertFalse(r("value $jwt").contains("eyJ"))
    }

    @Test
    fun secretKeyValuePairsAreRemoved() {
        assertEquals("<server>/api?token=<redacted>&x=1", r("https://h.example.com/api?token=abc123&x=1"))
        assertEquals("\"password\": \"<redacted>\"", r("\"password\": \"hunter2\""))
        assertEquals("csrf_token=<redacted>", r("csrf_token=zzz"))
        assertEquals("api_key: <redacted>", r("api_key: k1"))
        // Words merely containing "key"/"auth" are left alone.
        assertEquals("monkey=3 OAuth: on", r("monkey=3 OAuth: on"))
    }

    @Test
    fun urlsBecomeServer() {
        assertEquals(
            "GET <server>/api/sessions failed",
            r("GET https://datawatch.example.com:8443/api/sessions failed"),
        )
        assertEquals("ws <server>/ws", r("ws wss://10.1.2.3:8443/ws"))
        assertEquals("<server>/x", r("http://user:pw@host.example.com/x"))
        assertEquals("<server>/", r("http://[fd7a:115c::1]:8443/"))
        assertEquals("<server>", r("https://host.example.com"))
    }

    @Test
    fun hostNamesBecomeServer() {
        assertEquals(
            "Unable to resolve host \"<server>\": No address",
            r("Unable to resolve host \"datawatch.example.com\": No address"),
        )
        assertEquals("Failed to connect to <server>", r("Failed to connect to box.example.org:443"))
        assertEquals("peer <server> down", r("peer ring.tail1234.ts.net down"))
        assertEquals("peer <server> down", r("peer nas.local:8080 down"))
        assertEquals("at <server>.", r("at build.lab.internal."))
    }

    @Test
    fun ipAddressesBecomeIp() {
        assertEquals("peer <ip>:8443 refused", r("peer 100.64.1.2:8443 refused"))
        assertEquals("Failed to connect to /<server>", r("Failed to connect to /100.64.1.2:8443"))
        assertEquals("from <ip>.", r("from 192.168.0.10."))
        assertEquals("v6 <ip> and <ip>", r("v6 fe80::1 and 2001:db8:0:0:0:0:0:1"))
        assertEquals("loopback <ip>", r("loopback ::1"))
    }

    @Test
    fun versionsTimesAndStackFramesSurvive() {
        val keep =
            listOf(
                "App version: 1.28.17 (673)",
                "Time: 2026-10-14T09:30:00.123Z",
                "at 12:34:56 the socket closed",
                "\tat com.dmzs.datawatchclient.transport.rest.RestTransport.listSessions(RestTransport.kt:120)",
                "Caused by: java.net.SocketTimeoutException: timeout",
                "OS version: Version 17.5 (Build 21F79)",
                "Device: iPhone15,2",
                "binaryUUID 8A3B1C2D-1111-2222-3333-444455556666",
                "/var/mobile/Containers/Data/Application/report.txt",
                "io.ktor.client.engine.internal.Foo.local.Bar",
                "kotlinx.coroutines.internal.DispatchedContinuationKt.resumeWith(DispatchedContinuation.kt:367)",
            )
        keep.forEach { assertEquals(it, r(it)) }
    }

    @Test
    fun tokenLikeRunsAreRemoved() {
        assertEquals("x <token> y", r("x 0123456789abcdef0123456789abcdef y"))
        assertEquals("k=<token>", r("k=dGhpcyBpcyBhIHNlY3JldCB0b2tlbiB2YWx1ZTEy"))
        assertEquals("<token>", r("dGhpcyBpcyBhIHNlY3JldCB0b2tlbiB2YWx1ZTEy=="))
        // Long identifiers without digits are not tokens.
        assertEquals("AutoSessionDetailScreenTemplateBuilderHelper", r("AutoSessionDetailScreenTemplateBuilderHelper"))
        // Shorter than 32 chars stays.
        assertEquals("abc123def456", r("abc123def456"))
    }

    @Test
    fun redactionIsIdempotent() {
        val once =
            r(
                "Authorization: Bearer x\nGET https://10.0.0.1:8443/api?token=t " +
                    "host \"a.example.com\" 0123456789abcdef0123456789abcdef fe80::1",
            )
        assertEquals(once, r(once))
        assertTrue(once.contains("<server>"))
        assertFalse(once.contains("10.0.0.1"))
    }
}
