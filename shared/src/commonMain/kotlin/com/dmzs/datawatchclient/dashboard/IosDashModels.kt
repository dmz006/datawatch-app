package com.dmzs.datawatchclient.dashboard

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

// ─────────────────────────────────────────────────────────────────────────
// iOS Dashboard parity (B36, D34a). Display-ready models for the PWA card
// grid (`renderDashboardView` / `DASH_CARD_DEFS`). Swift maps `tone` strings
// to DatawatchColors: accent · accent2 · warning · success · error · muted ·
// text · border. `symbol` fields are SF Symbol names. Lives in commonMain so
// it compiles and unit-tests on Linux; IosDashboard.kt is the thin iosMain
// callback bridge.
// ─────────────────────────────────────────────────────────────────────────

/** One entry of PWA `DASH_CARD_DEFS`. */
public data class IosDashCardDef(
    val id: String,
    val label: String,
    val symbol: String,
    val defaultCs: Int,
)

/** One card in the saved layout (`/api/dashboard/layout` `cards[]`). */
public data class IosDashCard(
    val id: String,
    val cs: Int,
    val rs: Int,
    val system: Boolean,
)

/** Grid metrics for a viewport width (PWA `.dboard-card-grid` + media queries). */
public data class IosDashGridMetrics(
    val rowHeight: Double,
    val gap: Double,
    val narrow: Boolean,
)

public object IosDashCatalog {
    public val defs: List<IosDashCardDef> =
        listOf(
            IosDashCardDef(id = "tree", label = "Automata", symbol = "list.bullet.indent", defaultCs = 2),
            IosDashCardDef(id = "orbital", label = "Network", symbol = "circle.hexagongrid", defaultCs = 6),
            IosDashCardDef(id = "events", label = "Live Events", symbol = "bolt", defaultCs = 2),
            IosDashCardDef(id = "sparklines", label = "Sessions", symbol = "chart.bar.xaxis", defaultCs = 2),
            IosDashCardDef(id = "gantt", label = "Timeline · 6h", symbol = "chart.bar.doc.horizontal", defaultCs = 12),
            IosDashCardDef(id = "heatmap", label = "30-Day Activity", symbol = "flame", defaultCs = 3),
            IosDashCardDef(id = "guardrails", label = "Guardrails", symbol = "shield", defaultCs = 3),
            IosDashCardDef(id = "ekg", label = "Multi-EKG", symbol = "waveform.path.ecg", defaultCs = 6),
            IosDashCardDef(id = "smoke", label = "Smoke Run", symbol = "testtube.2", defaultCs = 6),
            IosDashCardDef(id = "memory-scope", label = "Memory Scopes", symbol = "brain", defaultCs = 3),
            IosDashCardDef(id = "websearch-usage", label = "Search Usage", symbol = "magnifyingglass", defaultCs = 3),
        )

    /** PWA `DASH_DEFAULT_LAYOUT` (server `defaultDashCards` + websearch tile). */
    public val defaultLayout: List<IosDashCard> =
        listOf(
            IosDashCard(id = "tree", cs = 2, rs = 2, system = true),
            IosDashCard(id = "orbital", cs = 6, rs = 2, system = true),
            IosDashCard(id = "events", cs = 2, rs = 2, system = true),
            IosDashCard(id = "sparklines", cs = 2, rs = 1, system = true),
            IosDashCard(id = "gantt", cs = 12, rs = 1, system = true),
            IosDashCard(id = "heatmap", cs = 3, rs = 1, system = true),
            IosDashCard(id = "guardrails", cs = 3, rs = 1, system = true),
            IosDashCard(id = "memory-scope", cs = 3, rs = 1, system = true),
            IosDashCard(id = "websearch-usage", cs = 3, rs = 1, system = false),
            IosDashCard(id = "ekg", cs = 6, rs = 2, system = true),
            IosDashCard(id = "smoke", cs = 6, rs = 2, system = true),
        )

    private val spanCycle: List<Int> = listOf(2, 3, 4, 6, 8, 12)
    private val rowCycle: List<Int> = listOf(1, 2)

    public fun def(id: String): IosDashCardDef? = defs.firstOrNull { it.id == id }

    /** Parse `{cards:[...]}`; unknown ids dropped; empty/absent → [defaultLayout]. */
    public fun parseLayout(obj: JsonObject?): List<IosDashCard> {
        val arr = obj?.get("cards") as? JsonArray ?: return defaultLayout
        val out = mutableListOf<IosDashCard>()
        for (el in arr) {
            val o = el as? JsonObject ?: continue
            val id = o.str("id")
            val d = def(id) ?: continue
            if (out.any { it.id == id }) continue
            val cs = o.int("cs")?.takeIf { it in 1..12 } ?: d.defaultCs
            val rs = o.int("rs")?.takeIf { it in 1..2 } ?: 1
            val system = o.bool("system")
            out.add(IosDashCard(id = id, cs = cs, rs = rs, system = system))
        }
        return if (out.isEmpty()) defaultLayout else out
    }

    /** Body for PUT /api/dashboard/layout. */
    public fun layoutBody(cards: List<IosDashCard>): JsonObject =
        buildJsonObject {
            put(
                "cards",
                buildJsonArray {
                    for (c in cards) {
                        add(
                            buildJsonObject {
                                put("id", c.id)
                                put("cs", c.cs)
                                put("rs", c.rs)
                                if (c.system) put("system", true)
                            },
                        )
                    }
                },
            )
        }

    public fun nextSpan(cs: Int): Int {
        val idx = spanCycle.indexOf(cs)
        return spanCycle[(idx + 1) % spanCycle.size]
    }

    public fun nextRows(rs: Int): Int {
        val idx = rowCycle.indexOf(rs)
        return rowCycle[(idx + 1) % rowCycle.size]
    }

    public fun cycledSpan(cards: List<IosDashCard>, id: String): List<IosDashCard> =
        cards.map { if (it.id == id) it.copy(cs = nextSpan(it.cs)) else it }

    public fun cycledRows(cards: List<IosDashCard>, id: String): List<IosDashCard> =
        cards.map { if (it.id == id) it.copy(rs = nextRows(it.rs)) else it }

    public fun removed(cards: List<IosDashCard>, id: String): List<IosDashCard> = cards.filter { it.id != id }

    public fun added(cards: List<IosDashCard>, id: String): List<IosDashCard> {
        val d = def(id) ?: return cards
        if (cards.any { it.id == id }) return cards
        return cards + IosDashCard(id = id, cs = d.defaultCs, rs = 1, system = false)
    }

    /** Move [id] by [delta] positions (iOS stand-in for the PWA drag-and-drop reorder). */
    public fun moved(cards: List<IosDashCard>, id: String, delta: Int): List<IosDashCard> {
        val from = cards.indexOfFirst { it.id == id }
        if (from < 0) return cards
        val to = (from + delta).coerceIn(0, cards.size - 1)
        if (to == from) return cards
        val list = cards.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return list
    }

    /**
     * Column span out of 12 for a card at [viewportWidth] points — PWA
     * `_dashBuildGrid` narrow override (<600 → full width) and the ≤900px
     * media query (6-col grid: cs 6/12 full row, everything else half).
     */
    public fun effectiveSpan(cs: Int, viewportWidth: Double): Int =
        when {
            viewportWidth < 600.0 -> 12
            viewportWidth <= 900.0 -> if (cs == 6 || cs == 12) 12 else 6
            else -> cs.coerceIn(1, 12)
        }

    public fun metrics(viewportWidth: Double): IosDashGridMetrics =
        IosDashGridMetrics(
            rowHeight = if (viewportWidth <= 900.0) 160.0 else 180.0,
            gap = if (viewportWidth < 600.0) 6.0 else 8.0,
            narrow = viewportWidth < 600.0,
        )

    /** PWA `defsLink` slug for the per-card docs anchor (D26a). */
    public fun docsSlug(label: String): String {
        val sb = StringBuilder()
        var lastDash = false
        for (ch in label.lowercase()) {
            if (ch in 'a'..'z' || ch in '0'..'9') {
                sb.append(ch)
                lastDash = false
            } else if (!lastDash) {
                sb.append('-')
                lastDash = true
            }
        }
        return sb.toString().trim('-')
    }
}

// ── Hook board (hook_update frame / GET /status) ─────────────────────────

public data class IosDashVerdict(
    val guardrail: String,
    val outcome: String,
    val summary: String,
)

/** The slice of a SessionStatusBoard the dashboard reads. */
public data class IosDashBoard(
    val sessionId: String,
    val state: String,
    val hookHealth: String,
    val lastEvent: String,
    val lastTool: String,
    /** `last_event.payload.current_task` || `current_focus.task`. */
    val eventTask: String,
    /** `current_focus.task` only (Automata card focus line). */
    val focusTask: String,
    val tasksDone: Int,
    val tasksTotal: Int,
    val verdicts: List<IosDashVerdict>,
    val parentSessionId: String,
    val sprintPrdId: String,
    val sprintId: String,
    val sprintTitle: String,
)

// ── Smoke runs (GET /api/smoke/progress[/id]) ─────────────────────────────

public data class IosDashSmokeRun(
    val id: String,
    val type: String,
    val pass: Int,
    val fail: Int,
    val skip: Int,
    val total: Int,
    val active: Boolean,
    val version: String,
    /** Local "HH:mm" of `updated_at`, or "". */
    val timeLabel: String,
    val pct: Double,
)

public data class IosDashSmokeSection(
    val id: String,
    val name: String,
    val result: String,
)

public data class IosDashSmokeDetail(
    val pass: Int,
    val fail: Int,
    val skip: Int,
    val total: Int,
    val active: Boolean,
    val currentId: String,
    val currentName: String,
    val sections: List<IosDashSmokeSection>,
)

// ── Memory scopes + search usage tiles ───────────────────────────────────

public data class IosDashCountBar(
    val label: String,
    val count: Int,
    val fraction: Double,
    val errors: Int,
)

public data class IosDashMemStats(
    val total: Int,
    val rows: List<IosDashCountBar>,
)

public data class IosDashWebSearch(
    val enabled: Boolean,
    val total: Int,
    val today: Int,
    val week: Int,
    val month: Int,
    val cached: Int,
    /** daily_series counts normalised 0..1 (PWA `_sparkline`). */
    val series: List<Double>,
    val providers: List<IosDashCountBar>,
)

/** JSON → dashboard model parsers (pure; unit-tested). */
public object IosDashParsers {
    public fun board(
        sessionId: String,
        obj: JsonObject,
    ): IosDashBoard {
        val tel = obj["telemetry"] as? JsonObject
        val focus = obj["current_focus"] as? JsonObject
        val le = obj["last_event"] as? JsonObject
        val payload = le?.get("payload") as? JsonObject
        val focusTask = focus?.str("task").orEmpty()
        val eventTask = payload?.str("current_task")?.takeIf { it.isNotBlank() } ?: focusTask
        val tasks = (tel?.get("tasks") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val verdicts =
            (tel?.get("guardrail_verdicts") as? JsonArray).orEmpty().mapNotNull { el ->
                val v = el as? JsonObject ?: return@mapNotNull null
                val rule =
                    v.str("guardrail").ifBlank { v.str("rule_id") }.ifBlank { v.str("rule") }.ifBlank { "unknown" }
                IosDashVerdict(guardrail = rule, outcome = v.str("outcome").ifBlank { "pass" }, summary = v.str("summary"))
            }
        val sprint = tel?.get("sprint") as? JsonObject
        return IosDashBoard(
            sessionId = sessionId,
            state = obj.str("state").ifBlank { "unknown" },
            hookHealth = obj.str("hook_health").ifBlank { "missing" },
            lastEvent = le?.str("event").orEmpty(),
            lastTool = le?.str("tool").orEmpty(),
            eventTask = eventTask,
            focusTask = focusTask,
            tasksDone = tasks.count { isDone(it.str("status")) },
            tasksTotal = tasks.size,
            verdicts = verdicts,
            parentSessionId = tel?.str("parent_session_id").orEmpty(),
            sprintPrdId = sprint?.str("prd_id").orEmpty(),
            sprintId = sprint?.str("sprint_id").orEmpty(),
            sprintTitle = sprint?.str("title").orEmpty(),
        )
    }

    public fun smokeRuns(arr: JsonArray): List<IosDashSmokeRun> =
        arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id")
            if (id.isBlank()) return@mapNotNull null
            IosDashSmokeRun(
                id = id,
                type = o.str("type"),
                pass = o.int("pass") ?: 0,
                fail = o.int("fail") ?: 0,
                skip = o.int("skip") ?: 0,
                total = o.int("total") ?: 0,
                active = o.bool("active"),
                version = o.str("version"),
                timeLabel = hhmm(o.str("updated_at")),
                pct = o.dbl("pct") ?: 0.0,
            )
        }

    public fun smokeDetail(o: JsonObject): IosDashSmokeDetail {
        val sections =
            (o["sections"] as? JsonArray).orEmpty().mapNotNull { el ->
                val s = el as? JsonObject ?: return@mapNotNull null
                IosDashSmokeSection(id = s.str("id"), name = s.str("name"), result = s.str("result").ifBlank { "pass" })
            }
        return IosDashSmokeDetail(
            pass = o.int("pass") ?: 0,
            fail = o.int("fail") ?: 0,
            skip = o.int("skip") ?: 0,
            total = o.int("total") ?: 82,
            active = o.bool("active"),
            currentId = o.str("current_id"),
            currentName = o.str("current_name"),
            sections = sections,
        )
    }

    /** `/api/cost` total: server `total_usd`, PWA `total_cost_usd`, else Σ sessions[].est_cost_usd. */
    public fun costTotal(o: JsonObject): Double {
        o.dbl("total_usd")?.let { return it }
        o.dbl("total_cost_usd")?.let { return it }
        val sessions = (o["sessions"] as? JsonArray).orEmpty()
        var sum = 0.0
        for (el in sessions) {
            sum += (el as? JsonObject)?.dbl("est_cost_usd") ?: 0.0
        }
        return sum
    }

    private val scopeOrder: List<String> =
        listOf("session-local", "story-shared", "prd-shared", "project-shared", "persona-in-project", "persona-global")

    public fun memStats(o: JsonObject): IosDashMemStats {
        val scopes = o["scopes"] as? JsonObject
        val pairs = mutableListOf<Pair<String, Int>>()
        if (scopes != null) {
            for (k in scopeOrder) {
                val n = (scopes[k] as? JsonPrimitive)?.doubleOrNull ?: continue
                pairs.add(k to n.toInt())
            }
            for ((k, v) in scopes) {
                if (k in scopeOrder) continue
                val n = (v as? JsonPrimitive)?.doubleOrNull ?: continue
                pairs.add(k to n.toInt())
            }
        }
        val maxN = maxOf(1, pairs.maxOfOrNull { it.second } ?: 1)
        return IosDashMemStats(
            total = o.int("total_entries") ?: 0,
            rows =
                pairs.map { (k, n) ->
                    IosDashCountBar(label = k.replace('-', ' '), count = n, fraction = n.toDouble() / maxN, errors = 0)
                },
        )
    }

    public fun webSearch(dto: com.dmzs.datawatchclient.transport.dto.WebSearchStatsV2Dto): IosDashWebSearch {
        val sum = dto.summary
        val counts = dto.dailySeries.map { it.count }
        val maxC = maxOf(1, counts.maxOrNull() ?: 1)
        val maxP = maxOf(1, sum.providers.maxOfOrNull { it.total } ?: 1)
        return IosDashWebSearch(
            enabled = dto.enabled,
            total = sum.total,
            today = sum.today,
            week = sum.thisWeek,
            month = sum.thisMonth,
            cached = sum.cacheHits,
            series = counts.map { it.toDouble() / maxC },
            providers =
                sum.providers.map { p ->
                    IosDashCountBar(label = p.name, count = p.total, fraction = p.total.toDouble() / maxP, errors = p.errors)
                },
        )
    }

    internal fun isDone(status: String): Boolean = status == "completed" || status == "complete"

    /** ISO timestamp → local "HH:mm"; "" when unparsable. */
    internal fun hhmm(iso: String): String {
        if (iso.isBlank()) return ""
        val inst = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
        return hhmm(inst.toEpochMilliseconds().toDouble(), withSeconds = false)
    }

    internal fun hhmm(
        epochMs: Double,
        withSeconds: Boolean,
    ): String {
        val ldt = Instant.fromEpochMilliseconds(epochMs.toLong()).toLocalDateTime(TimeZone.currentSystemDefault())
        val hm = "${pad2(ldt.hour)}:${pad2(ldt.minute)}"
        return if (withSeconds) "$hm:${pad2(ldt.second)}" else hm
    }

    private fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()
}

// ── JSON helpers ─────────────────────────────────────────────────────────

internal fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.int(key: String): Int? = dbl(key)?.toInt()

internal fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

