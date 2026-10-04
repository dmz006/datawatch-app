package com.dmzs.datawatchclient.ui.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PWA createExitHook / loadExitHooks / pushQueueItem rules. */
class ExitHooksWorkQueueTest {
    @Test
    fun `restart body omits notify fields and defaults cooldown`() {
        val b = exitHookCreateBody(" build ", "restart", "other", "msg", "")
        assertEquals("build", (b["name"] as JsonPrimitive).content)
        assertEquals("300", (b["cooldown_seconds"] as JsonPrimitive).content)
        assertFalse(b.containsKey("notify_session"))
    }

    @Test
    fun `notify body carries target and optional message`() {
        val b = exitHookCreateBody("a", "notify", "lead", "", "60")
        assertEquals("lead", (b["notify_session"] as JsonPrimitive).content)
        assertFalse(b.containsKey("notify_message"))
        assertEquals("60", (b["cooldown_seconds"] as JsonPrimitive).content)
    }

    @Test
    fun `zero last_fired_at reads as never`() {
        val o =
            Json.parseToJsonElement(
                """{"id":"h","name":"n","action":"restart","cooldown_seconds":300,"enabled":true,"last_fired_at":"0001-01-01T00:00:00Z"}""",
            ) as JsonObject
        val row = ExitHookRow.from(o)
        assertTrue(row.enabled)
        assertEquals("", row.lastFiredAt)
    }

    @Test
    fun `queue payload must be a json object`() {
        assertTrue(parseQueuePayload("").getOrThrow().isEmpty())
        assertEquals("x", (parseQueuePayload("""{"task":"x"}""").getOrThrow()["task"] as JsonPrimitive).content)
        assertTrue(parseQueuePayload("[1]").isFailure)
        assertTrue(parseQueuePayload("{bad").isFailure)
    }
}
