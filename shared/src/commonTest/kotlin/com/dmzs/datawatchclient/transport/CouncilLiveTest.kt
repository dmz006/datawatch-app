package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.dto.CouncilRoundDto
import com.dmzs.datawatchclient.transport.dto.CouncilRunDto
import com.dmzs.datawatchclient.transport.dto.CouncilRunEvent
import com.dmzs.datawatchclient.transport.dto.CouncilRunEventParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CouncilLiveTest {
    private fun frames(raw: String): List<CouncilSseFrame> {
        val p = CouncilSseLineParser()
        val out = raw.split("\n").mapNotNull { p.feed(it) }.toMutableList()
        p.finish()?.let { out += it }
        return out
    }

    @Test
    fun `sse parser splits frames - ignores comments - joins multi-line data`() {
        val got =
            frames(
                "event: hello\ndata: {}\nid: 0\n\n" +
                    ": keepalive\n\n" +
                    "event: persona_response\ndata: {\"a\":1,\ndata: \"b\":2}\nid: 7\r\n\r\n" +
                    "data: tail",
            )
        assertEquals(3, got.size)
        assertEquals(CouncilSseFrame("hello", "{}", "0"), got[0])
        assertEquals("persona_response", got[1].event)
        assertEquals("{\"a\":1,\n\"b\":2}", got[1].data)
        assertEquals("7", got[1].id)
        assertEquals("message", got[2].event)
        assertEquals("tail", got[2].data)
    }

    @Test
    fun `sse parser drops frames without data`() {
        val p = CouncilSseLineParser()
        assertNull(p.feed("event: x"))
        assertNull(p.feed(""))
        assertNull(p.finish())
    }

    @Test
    fun `event parser decodes server payloads`() {
        val started =
            CouncilRunEventParser.parse(
                "run_started",
                """{"run_id":"r1","mode":"debate","personas":["a","b"],"max_parallel":2,"rounds_total":3}""",
            )
        assertEquals("r1", started.runId)
        assertEquals("debate", started.mode)
        assertEquals(listOf("a", "b"), started.personas)
        assertEquals(2, started.maxParallel)
        assertEquals(3, started.roundsTotal)

        val resp =
            CouncilRunEventParser.parse(
                "persona_response",
                """{"run_id":"r1","round":2,"persona":"a","text":"hi","session_id":"s"}""",
            )
        assertEquals(2, resp.round)
        assertEquals("a", resp.persona)
        assertEquals("hi", resp.text)
        assertEquals("s", resp.sessionId)

        val done =
            CouncilRunEventParser.parse(
                "run_completed",
                """{"run_id":"r1","consensus":"yes","dissent":"no","finished_at":"2026-10-05T10:00:00Z"}""",
            )
        assertTrue(done.isTerminal)
        assertEquals("yes", done.consensus)
        assertEquals("2026-10-05T10:00:00Z", done.finishedAt)

        val bad = CouncilRunEventParser.parse("round_started", "not json")
        assertEquals("round_started", bad.type)
        assertEquals(0, bad.round)
        assertFalse(bad.isTerminal)
    }

    @Test
    fun `reducer builds rounds and replies from the live stream`() {
        var s = CouncilLiveState(runId = "r1", proposal = "p", mode = "quick")
        val evs =
            listOf(
                CouncilRunEvent(type = "hello"),
                CouncilRunEvent(type = "run_started", mode = "debate", personas = listOf("a", "b"), roundsTotal = 3),
                CouncilRunEvent(type = "round_started", round = 1),
                CouncilRunEvent(type = "persona_responding", round = 1, persona = "a"),
                CouncilRunEvent(type = "persona_response", round = 1, persona = "a", text = "A1"),
                CouncilRunEvent(type = "persona_error", round = 1, persona = "b", error = "boom"),
                CouncilRunEvent(type = "round_completed", round = 1),
                CouncilRunEvent(type = "synthesis_started"),
            )
        evs.forEach { s = CouncilLiveReducer.reduce(s, it) }
        assertEquals(CouncilLivePhase.SYNTHESIZING, s.phase)
        assertEquals("debate", s.mode)
        assertEquals(3, s.roundsTotal)
        assertEquals(1, s.rounds.size)
        val r = s.rounds[0]
        assertTrue(r.completed)
        assertEquals(listOf("a", "b"), r.replies.map { it.persona })
        assertEquals(CouncilReplyStatus.DONE, r.replies[0].status)
        assertEquals("A1", r.replies[0].text)
        assertEquals(CouncilReplyStatus.ERROR, r.replies[1].status)
        assertEquals("boom", r.replies[1].text)

        s = CouncilLiveReducer.reduce(s, CouncilRunEvent(type = "run_completed", consensus = "C", dissent = "D"))
        assertTrue(s.isTerminal)
        assertEquals(CouncilLivePhase.COMPLETED, s.phase)
        assertEquals("C", s.consensus)
        assertEquals("D", s.dissent)
    }

    @Test
    fun `reducer seeds waiting replies and tolerates a missed round_started`() {
        var s = CouncilLiveState(runId = "r1", personas = listOf("a", "b"))
        s = CouncilLiveReducer.reduce(s, CouncilRunEvent(type = "persona_response", round = 2, persona = "b", text = "B2"))
        assertEquals(CouncilLivePhase.RUNNING, s.phase)
        assertEquals(2, s.rounds.single().index)
        assertEquals(CouncilReplyStatus.WAITING, s.rounds[0].replies[0].status)
        assertEquals("B2", s.rounds[0].replies[1].text)
        s = CouncilLiveReducer.reduce(s, CouncilRunEvent(type = "run_cancelled"))
        assertEquals(CouncilLivePhase.CANCELLED, s.phase)
    }

    @Test
    fun `fromRun replays a persisted run`() {
        val run =
            CouncilRunDto(
                id = "r1",
                proposal = "p",
                personas = listOf("b", "a"),
                mode = "debate",
                rounds =
                    listOf(
                        CouncilRoundDto(index = 2, responses = mapOf("a" to "A2", "b" to "[b] error: x")),
                        CouncilRoundDto(index = 1, responses = mapOf("a" to "A1", "b" to "B1", "z" to "Z1")),
                    ),
                consensus = "C",
                finishedAt = "2026-10-05T10:00:00Z",
            )
        assertTrue(run.isFinished)
        assertEquals("completed", run.effectiveStatus)
        val s = CouncilLiveReducer.fromRun(run)
        assertEquals(CouncilLivePhase.COMPLETED, s.phase)
        assertEquals(listOf(1, 2), s.rounds.map { it.index })
        assertEquals(listOf("b", "a", "z"), s.rounds[0].replies.map { it.persona })
        assertEquals(CouncilReplyStatus.ERROR, s.rounds[1].replies[0].status)
        assertEquals("C", s.consensus)
        assertEquals(3, s.roundsTotal)
    }

    @Test
    fun `run detail with zero finished_at is still running`() {
        val run = CouncilRunDto(id = "r", finishedAt = "0001-01-01T00:00:00Z")
        assertFalse(run.isFinished)
        assertEquals("running", run.effectiveStatus)
        assertEquals("cancelled", run.copy(cancelled = true).effectiveStatus)
        assertEquals(CouncilLivePhase.RUNNING, CouncilLiveReducer.fromRun(run).phase)
    }
}
