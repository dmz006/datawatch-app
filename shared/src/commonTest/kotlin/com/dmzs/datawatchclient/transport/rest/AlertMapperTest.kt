package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.AlertSeverity
import com.dmzs.datawatchclient.transport.dto.AlertDto
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AlertDto → Alert mapping — regression guard for v0.34.8's schema
 * fix. The server emits `{level, title, body}`; pre-v0.34.8 the DTO
 * expected `{type, severity, message}` and every live row silently
 * failed decode. Mapper now prefers the new triple but tolerates
 * the legacy one so older servers still parse.
 */
class AlertMapperTest {
    @Test
    fun `maps new level title body shape to domain`() {
        val dto =
            AlertDto(
                id = "a1",
                level = "warn",
                title = "ring: datawatch-app [17db]: waiting for input",
                body = "Prompt: continue?",
                sessionId = "ring-17db",
                createdAt = "2026-04-24T00:00:00Z",
                read = false,
            )
        val alert = dto.toDomain("profile-1")
        assertEquals("a1", alert.id)
        assertEquals(AlertSeverity.Warning, alert.severity)
        assertEquals("ring: datawatch-app [17db]: waiting for input", alert.title)
        assertEquals("Prompt: continue?", alert.message)
        assertEquals("ring-17db", alert.sessionId)
        assertEquals(Instant.parse("2026-04-24T00:00:00Z"), alert.createdAt)
        assertEquals(false, alert.read)
    }

    @Test
    fun `falls back to legacy type severity message when new triple absent`() {
        val dto =
            AlertDto(
                id = "a2",
                type = "input_needed",
                severity = "warn",
                message = "Session needs your input",
                sessionId = "ring-17db",
                createdAt = "2026-04-24T00:00:00Z",
                read = false,
            )
        val alert = dto.toDomain("profile-1")
        assertEquals("a2", alert.id)
        assertEquals(AlertSeverity.Warning, alert.severity)
        assertEquals("input_needed", alert.type)
        assertEquals("Session needs your input", alert.message)
        assertEquals("", alert.title) // no title in legacy shape
    }

    @Test
    fun `severity derived from level takes precedence over legacy severity`() {
        val dto =
            AlertDto(
                id = "a3",
                level = "error",
                // legacy fields populated — should be ignored when level is present
                type = "old",
                severity = "info",
                message = "legacy message",
                title = "new title",
                body = "new body",
                createdAt = "2026-04-24T00:00:00Z",
            )
        val alert = dto.toDomain("profile-1")
        assertEquals(AlertSeverity.Error, alert.severity)
        // body wins over legacy message
        assertEquals("new body", alert.message)
        assertEquals("new title", alert.title)
    }

    @Test
    fun `missing body falls back to legacy message`() {
        val dto =
            AlertDto(
                id = "a4",
                level = "info",
                title = "Hello",
                body = null,
                message = "fallback body",
                createdAt = "2026-04-24T00:00:00Z",
            )
        val alert = dto.toDomain("profile-1")
        assertEquals("fallback body", alert.message)
    }

    @Test
    fun `empty title is preserved as empty string not null`() {
        val dto = AlertDto(id = "a5", level = "info", body = "body")
        val alert = dto.toDomain("profile-1")
        assertEquals("", alert.title)
    }

    @Test
    fun `missing created_at resolves to DISTANT_PAST`() {
        val dto = AlertDto(id = "a6", level = "info", body = "x")
        val alert = dto.toDomain("profile-1")
        assertEquals(Instant.DISTANT_PAST, alert.createdAt)
    }
}
