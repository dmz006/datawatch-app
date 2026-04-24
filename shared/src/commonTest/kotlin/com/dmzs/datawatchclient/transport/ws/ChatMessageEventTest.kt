package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.transport.dto.WsFrameDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for `EventMapper.buildChatMessage` (v0.34.6).
 * The parent emits WS frames of type `chat_message` for chat-mode
 * sessions; we must parse the role / content / streaming tuple and
 * filter by session id (same short-or-full match logic as
 * pane_capture).
 */
class ChatMessageEventTest {
    private fun frame(data: JsonObject): WsFrameDto =
        WsFrameDto(type = "chat_message", data = data, timestamp = "2026-04-24T00:00:00Z")

    @Test
    fun `user role maps correctly`() {
        val ev =
            frame(
                buildJsonObject {
                    put("session_id", JsonPrimitive("ring-abcd"))
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive("hello"))
                },
            ).toDomainEvents("ring-abcd").single() as SessionEvent.ChatMessage
        assertEquals(SessionEvent.ChatMessage.Role.User, ev.role)
        assertEquals("hello", ev.content)
        assertEquals(false, ev.streaming)
    }

    @Test
    fun `assistant role maps when spelled ai or llm too`() {
        val roles = listOf("assistant", "AI", "llm", "Assistant")
        for (role in roles) {
            val ev =
                frame(
                    buildJsonObject {
                        put("session_id", JsonPrimitive("ring-abcd"))
                        put("role", JsonPrimitive(role))
                        put("content", JsonPrimitive("x"))
                    },
                ).toDomainEvents("ring-abcd").single() as SessionEvent.ChatMessage
            assertEquals(
                SessionEvent.ChatMessage.Role.Assistant,
                ev.role,
                "role=$role should map to Assistant",
            )
        }
    }

    @Test
    fun `streaming flag carries through`() {
        val ev =
            frame(
                buildJsonObject {
                    put("session_id", JsonPrimitive("ring-abcd"))
                    put("role", JsonPrimitive("assistant"))
                    put("content", JsonPrimitive("chunk"))
                    put("streaming", JsonPrimitive(true))
                },
            ).toDomainEvents("ring-abcd").single() as SessionEvent.ChatMessage
        assertTrue(ev.streaming)
    }

    @Test
    fun `unknown role defaults to system`() {
        val ev =
            frame(
                buildJsonObject {
                    put("session_id", JsonPrimitive("ring-abcd"))
                    put("role", JsonPrimitive("whatever"))
                    put("content", JsonPrimitive("?"))
                },
            ).toDomainEvents("ring-abcd").single() as SessionEvent.ChatMessage
        assertEquals(SessionEvent.ChatMessage.Role.System, ev.role)
    }

    @Test
    fun `frame for different session is filtered out`() {
        val events =
            frame(
                buildJsonObject {
                    put("session_id", JsonPrimitive("ring-zzzz"))
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive("x"))
                },
            ).toDomainEvents("ring-abcd")
        assertTrue(events.isEmpty())
    }

    @Test
    fun `short-id subscriber matches full-id frame`() {
        // Android navigates with short id; server frame carries the
        // full id. contains() should match either direction — same
        // contract as pane_capture.
        val ev =
            frame(
                buildJsonObject {
                    put("session_id", JsonPrimitive("ring-abcd"))
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive("x"))
                },
            ).toDomainEvents("abcd").single() as SessionEvent.ChatMessage
        assertEquals("ring-abcd", ev.sessionId)
    }
}
