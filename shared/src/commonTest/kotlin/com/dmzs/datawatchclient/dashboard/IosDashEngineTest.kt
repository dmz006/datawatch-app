package com.dmzs.datawatchclient.dashboard

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.AnalyticsBucketDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.PrdStoryDto
import com.dmzs.datawatchclient.transport.dto.PrdTaskDto
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosDashEngineTest {
    private fun session(
        id: String,
        state: SessionState,
        name: String? = null,
    ): Session =
        Session(
            id = id,
            serverProfileId = "p",
            hostnamePrefix = "host",
            state = state,
            createdAt = Instant.fromEpochMilliseconds(0),
            lastActivityAt = Instant.fromEpochMilliseconds(0),
            name = name,
        )

    private fun board(
        sid: String,
        verdicts: List<IosDashVerdict> = emptyList(),
        parent: String = "",
    ): IosDashBoard =
        IosDashBoard(
            sessionId = sid,
            state = "running",
            hookHealth = "alive",
            lastEvent = "PostToolUse",
            lastTool = "Bash",
            eventTask = "build",
            focusTask = "build",
            tasksDone = 1,
            tasksTotal = 3,
            verdicts = verdicts,
            parentSessionId = parent,
            sprintPrdId = "",
            sprintId = "",
            sprintTitle = "",
        )

    @Test
    fun layoutParsesKnownCardsAndFallsBack() {
        val obj = Json.parseToJsonElement("""{"cards":[{"id":"ekg","cs":6,"rs":2},{"id":"nope","cs":3},{"id":"ekg","cs":2}]}""") as JsonObject
        val cards = IosDashCatalog.parseLayout(obj)
        assertEquals(1, cards.size)
        assertEquals(IosDashCard(id = "ekg", cs = 6, rs = 2, system = false), cards[0])
        assertEquals(IosDashCatalog.defaultLayout, IosDashCatalog.parseLayout(null))
    }

    @Test
    fun layoutEditOps() {
        val base = IosDashCatalog.defaultLayout
        assertEquals(3, IosDashCatalog.cycledSpan(base, "tree").first { it.id == "tree" }.cs)
        assertEquals(1, IosDashCatalog.cycledRows(base, "tree").first { it.id == "tree" }.rs)
        assertEquals("orbital", IosDashCatalog.moved(base, "orbital", -1).first().id)
        assertFalse(IosDashCatalog.removed(base, "smoke").any { it.id == "smoke" })
        assertEquals(base, IosDashCatalog.added(base, "smoke"))
        assertEquals(12, IosDashCatalog.nextSpan(8))
        assertEquals(2, IosDashCatalog.nextSpan(12))
    }

    @Test
    fun effectiveSpanFollowsPwaBreakpoints() {
        assertEquals(12, IosDashCatalog.effectiveSpan(2, 390.0))
        assertEquals(6, IosDashCatalog.effectiveSpan(3, 800.0))
        assertEquals(12, IosDashCatalog.effectiveSpan(6, 800.0))
        assertEquals(3, IosDashCatalog.effectiveSpan(3, 1200.0))
    }

    @Test
    fun statBarAndGuardrailsAggregateBoards() {
        val e = IosDashEngine()
        e.setSessions(listOf(session("a", SessionState.Running), session("b", SessionState.Waiting), session("c", SessionState.Completed)))
        e.applyBoard(board("host-a", listOf(IosDashVerdict("security", "block", ""), IosDashVerdict("rules", "warn", ""))), 1000.0, live = true)
        e.applyBoard(board("host-b", listOf(IosDashVerdict("rules", "pass", ""))), 1000.0, live = false)
        val sb = e.statBar()
        assertEquals(3, sb.sessions)
        assertEquals(2, sb.active)
        assertEquals(2, sb.tasksDone)
        assertEquals(6, sb.tasksTotal)
        assertEquals(1, sb.block)
        assertEquals(1, sb.warn)
        val g = e.guardrails()
        assertEquals(1, g.block)
        assertEquals(1, g.pass)
        assertEquals("error", g.rules.first { it.rule == "security" }.tone)
        assertEquals("warning", g.rules.first { it.rule == "rules" }.tone)
        // Only the live board fed the event log / EKG.
        assertEquals(1, e.events().size)
        assertEquals("PostToolUse", e.events()[0].event)
        assertEquals(1, e.ekg(2000.0, 60.0).channels.first { it.sessionId == "host-a" }.pulses.size)
        assertEquals(0, e.ekg(2000.0, 60.0).channels.first { it.sessionId == "host-b" }.pulses.size)
    }

    @Test
    fun sparklineBucketsArePerSession() {
        val e = IosDashEngine()
        e.setSessions(listOf(session("a", SessionState.Running)))
        e.applyBoard(board("host-a"), 100_000.0, live = true)
        e.applyBoard(board("host-a"), 100_500.0, live = true)
        val row = e.sparkRows(101_000.0).single()
        assertEquals(60, row.buckets.size)
        assertEquals(1.0, row.buckets.maxOrNull())
    }

    @Test
    fun treeAndGanttFromPrds() {
        val e = IosDashEngine()
        val prd =
            PrdDto(
                id = "p1",
                name = "p1",
                title = "Ship it",
                status = "running",
                stories =
                    listOf(
                        PrdStoryDto(id = "s1", title = "One", status = "completed", tasks = listOf(PrdTaskDto(id = "t", status = "completed"))),
                        PrdStoryDto(id = "s2", title = "Two", status = "in_progress", tasks = listOf(PrdTaskDto(id = "u", status = "pending"))),
                        PrdStoryDto(id = "s3", title = "Three", status = "pending"),
                    ),
            )
        e.setPrds(listOf(prd, PrdDto(id = "x", name = "x", status = "completed")))
        val tree = e.tree()
        assertEquals(1, tree.prds.size)
        assertEquals(50, tree.prds[0].pct)
        assertEquals("checkmark", tree.prds[0].stories[0].symbol)
        e.toggleTreeRow("p1")
        assertFalse(e.tree().prds[0].expanded)
        e.toggleTreeRow("p1")
        val now = 10.0 * 3_600_000.0
        val g = e.gantt(now)
        assertTrue(g.ticks.isNotEmpty())
        val stories = g.prds[0].stories
        assertEquals(0.0, stories[0].startFraction)
        assertTrue(stories[1].running && stories[1].endFraction == 1.0)
        assertTrue(stories[2].pending)
    }

    @Test
    fun heatmapModes() {
        val e = IosDashEngine()
        assertFalse(e.heatmap(narrow = false).loaded)
        e.setHeatmap(
            (1..30).map { d ->
                AnalyticsBucketDto(date = "2026-09-${if (d < 10) "0$d" else "$d"}", sessionCount = d % 5, failed = if (d == 30) 4 else 0)
            },
        )
        val grid = e.heatmap(narrow = false)
        assertEquals(30, grid.cells.size)
        assertEquals("09-01", grid.labels.first().label)
        assertEquals("border", grid.cells[4].tone)
        val bars = e.heatmap(narrow = true)
        assertEquals(7, bars.cells.size)
        assertEquals("border", bars.cells.last().tone)
    }

    @Test
    fun smokeSelectionLifecycle() {
        val e = IosDashEngine()
        val runs =
            IosDashParsers.smokeRuns(
                Json.parseToJsonElement(
                    """[{"id":"r1","type":"e2e","pass":2,"fail":0,"total":10,"active":true,"updated_at":"2026-10-04T10:00:00Z"},{"id":"r0","pass":5,"total":5}]""",
                ) as JsonArray,
            )
        assertEquals("r1", e.setSmokeRuns(runs))
        assertTrue(e.smokeView().loading)
        e.setSmokeDetail(
            "r1",
            IosDashParsers.smokeDetail(
                Json.parseToJsonElement(
                    """{"pass":2,"fail":1,"total":10,"active":true,"current_id":"c","current_name":"Cur","sections":[{"id":"a","name":"A","result":"pass"},{"id":"b","name":"B","result":"fail"},{"id":"c2","name":"C","result":"pass"}]}""",
                ) as JsonObject,
            ),
        )
        val v = e.smokeView()
        assertEquals("running", v.status)
        assertEquals(3, v.done)
        assertEquals(6, v.remaining)
        assertEquals(4, v.rows.size)
        assertTrue(v.pills.any { it.key == "pending" })
        e.setSmokeFilter("fail")
        assertEquals(1, e.smokeView().rows.size)
        e.setSmokeDetail("r1", null)
        assertEquals("", e.smokeSelectedId())
        assertEquals("", e.selectSmokeRun("r0").let { e.selectSmokeRun("r0") })
    }

    @Test
    fun constellationSettlesWithinBounds() {
        val e = IosDashEngine()
        e.setSessions((1..6).map { session("s$it", SessionState.Running) })
        e.applyBoard(board("host-s2", parent = "host-s1"), 0.0, live = false)
        val c = IosDashConstellation(e)
        c.settle(500.0, 300.0)
        val nodes = c.snapshot()
        assertEquals(6, nodes.size)
        assertTrue(nodes.all { it.x in 0.0..500.0 && it.y in 0.0..300.0 })
        assertEquals(1, c.edgeLines().size)
        val n = nodes.first()
        assertEquals(n.id, c.hit(n.x + 1.0, n.y))
        e.setSessions(emptyList())
        c.settle(500.0, 300.0)
        assertTrue(c.snapshot().isEmpty())
    }

    @Test
    fun memStatsOrdersScopes() {
        val m = IosDashParsers.memStats(Json.parseToJsonElement("""{"total_entries":9,"scopes":{"zeta":1,"prd-shared":4,"session-local":4}}""") as JsonObject)
        assertEquals(9, m.total)
        assertEquals(listOf("session local", "prd shared", "zeta"), m.rows.map { it.label })
        assertEquals(1.0, m.rows[0].fraction)
    }
}
