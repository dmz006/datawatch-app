package com.dmzs.datawatchclient.transport.ws

import com.dmzs.datawatchclient.domain.Prompt
import com.dmzs.datawatchclient.domain.SessionEvent
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.WsFrameDto
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Converts [WsFrameDto] JSON-wire events into domain [SessionEvent] sealed
 * types. Forward-compat: unknown `type` values become [SessionEvent.Unknown]
 * rather than throwing, so a newer server doesn't crash the client.
 */
internal fun WsFrameDto.toDomain(fallbackSessionId: String): SessionEvent {
    val sid = sessionId ?: fallbackSessionId
    val instant = ts?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: Clock.System.now()
    return when (type) {
        "output" -> SessionEvent.Output(
            sessionId = sid,
            ts = instant,
            body = body ?: "",
            stream = when (stream?.lowercase()) {
                "stderr" -> SessionEvent.Output.Stream.Stderr
                "system" -> SessionEvent.Output.Stream.System
                else -> SessionEvent.Output.Stream.Stdout
            },
        )
        "state_change", "state" -> SessionEvent.StateChange(
            sessionId = sid,
            ts = instant,
            from = from?.let { SessionState.fromWire(it) } ?: SessionState.New,
            to = to?.let { SessionState.fromWire(it) } ?: SessionState.Error,
        )
        "prompt", "prompt_detected", "needs_input" -> SessionEvent.PromptDetected(
            sessionId = sid,
            ts = instant,
            prompt = Prompt(
                sessionId = sid,
                text = prompt ?: body ?: "",
                detectedAt = instant,
                kind = when (promptKind?.lowercase()) {
                    "approval", "yes_no" -> Prompt.Kind.Approval
                    "choice" -> Prompt.Kind.Choice
                    "rate_limit" -> Prompt.Kind.RateLimit
                    else -> Prompt.Kind.FreeForm
                },
            ),
        )
        "rate_limited", "rate_limit" -> SessionEvent.RateLimited(
            sessionId = sid,
            ts = instant,
            retryAfter = retryAfter?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
        "completed", "done" -> SessionEvent.Completed(
            sessionId = sid,
            ts = instant,
            exitCode = exitCode,
        )
        "error", "failed" -> SessionEvent.Error(
            sessionId = sid,
            ts = instant,
            message = message ?: body ?: "unknown error",
        )
        else -> SessionEvent.Unknown(sessionId = sid, ts = instant, type = type)
    }
}
