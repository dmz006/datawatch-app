package com.dmzs.datawatchclient.transport

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcmeStatusFormatTest {
    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private val now = Instant.parse("2026-10-07T00:00:00Z").toEpochMilliseconds()

    @Test
    fun disabledOrOlderServerHidesTheCard() {
        assertTrue(AcmeStatusFormat.healthLines(json("""{"enabled":false,"domains":[]}"""), now).isEmpty())
        assertEquals(AcmeStatusFormat.NOT_ISSUED_YET, AcmeStatusFormat.settingsLines(json("""{"enabled":false,"domains":[]}""")).single().text)
    }

    @Test
    fun healthTonesFollowTheWebThresholds() {
        val status =
            json(
                """{"enabled":true,"domains":[
                {"domain":"a.example","issued":true,"in_flight":false,"not_after":"2027-01-04T00:00:00Z"},
                {"domain":"b.example","issued":true,"in_flight":false,"not_after":"2026-10-17T00:00:00Z"},
                {"domain":"c.example","issued":false,"in_flight":false,"not_after":"0001-01-01T00:00:00Z","last_error":"dns: NXDOMAIN"},
                {"domain":"d.example","issued":false,"in_flight":true,"not_after":"0001-01-01T00:00:00Z"}
                ]}""",
            )
        val lines = AcmeStatusFormat.healthLines(status, now)
        assertEquals(listOf("success", "warning", "error", "error"), lines.map { it.tone })
        assertEquals("a.example — expires in 89d", lines[0].text)
        assertEquals("b.example — expires in 10d", lines[1].text)
        assertEquals("c.example — not issued", lines[2].text)
        assertEquals("dns: NXDOMAIN", lines[2].error)
        assertEquals("d.example — issuing…", lines[3].text)
    }

    @Test
    fun settingsLinesShowExpiryDate() {
        val status = json("""{"enabled":true,"domains":[{"domain":"a.example","issued":true,"not_after":"2027-01-04T12:00:00Z"}]}""")
        assertTrue(AcmeStatusFormat.settingsLines(status).single().text.startsWith("a.example: expires 2027-01-0"))
    }

    @Test
    fun verifyMessageMatchesWebToasts() {
        assertEquals(true, AcmeStatusFormat.verifyMessage(json("""{"ok":true}""")).second)
        assertEquals(false, AcmeStatusFormat.verifyMessage(json("""{"ok":false,"directory_reachable":false}""")).second)
    }

    @Test
    fun errorTextPullsTheServerMessage() {
        val ktor = "503: Server error(GET https://h/api/acme/verify: 503 . Text: \"{\"error\":\"acme not enabled\"}\n\""
        assertEquals("acme not enabled", AcmeStatusFormat.errorText(ktor))
        assertEquals("timeout", AcmeStatusFormat.errorText("timeout"))
    }
}
