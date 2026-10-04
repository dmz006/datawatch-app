package com.dmzs.datawatchclient.dashboard

import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.domain.SessionState
import com.dmzs.datawatchclient.transport.dto.AnalyticsBucketDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

// ── View models returned by IosDashEngine ────────────────────────────────

public data class IosDashStatBar(
    val sessions: Int,
    /** running + waiting (PWA `running` incl. waiting_input). */
    val active: Int,
    /** running only (PWA burn-rate panel). */
    val running: Int,
    val costUsd: Double,
    val tasksDone: Int,
    val tasksTotal: Int,
    val block: Int,
    val warn: Int,
    val automata: Int,
)

public data class IosDashTreeStory(
    val title: String,
    val tone: String,
    val symbol: String,
    val tasksDone: Int,
    val tasksTotal: Int,
)

public data class IosDashTreePrd(
    val id: String,
    val title: String,
    val tone: String,
    val pct: Int,
    val expanded: Boolean,
    val stories: List<IosDashTreeStory>,
)

public data class IosDashTreeSession(
    val id: String,
    val name: String,
    val tone: String,
    /** alive · stale · none (PWA ● ◐ ○). */
    val health: String,
    val focus: String,
    /** SF Symbol for the runtime badge, or "". */
    val runtimeSymbol: String,
)

public data class IosDashTree(
    val prds: List<IosDashTreePrd>,
    val sessions: List<IosDashTreeSession>,
)

public data class IosDashEvent(
    val timeLabel: String,
    val name: String,
    val event: String,
    val symbol: String,
    val tool: String,
    val task: String,
    val tone: String,
)

public data class IosDashSparkRow(
    val sessionId: String,
    val name: String,
    val state: String,
    val tone: String,
    /** 60 × 2 s buckets, normalised 0..1. */
    val buckets: List<Double>,
)

public data class IosDashPulse(
    /** Seconds before "now". */
    val ageSec: Double,
    val dy: Double,
)

public data class IosDashEkgChannel(
    val sessionId: String,
    val name: String,
    val tone: String,
    val health: String,
    val pulses: List<IosDashPulse>,
)

public data class IosDashEkg(
    val channels: List<IosDashEkgChannel>,
    /** Global trace when no session is active (PWA fallback line). */
    val idlePulses: List<IosDashPulse>,
    val idleTone: String,
)

public data class IosDashTick(
    val fraction: Double,
    val label: String,
)

public data class IosDashGanttStory(
    val title: String,
    val tasks: String,
    val tone: String,
    val symbol: String,
    val startFraction: Double,
    val endFraction: Double,
    val pending: Boolean,
    val running: Boolean,
    val completed: Boolean,
)

public data class IosDashGanttPrd(
    val id: String,
    val title: String,
    val tone: String,
    val pct: Int,
    val expanded: Boolean,
    val stories: List<IosDashGanttStory>,
)

public data class IosDashGantt(
    val ticks: List<IosDashTick>,
    val prds: List<IosDashGanttPrd>,
)

public data class IosDashHeatCell(
    val heightFraction: Double,
    val tone: String,
    val alpha: Double,
)

public data class IosDashHeatLabel(
    val index: Int,
    val label: String,
)

public data class IosDashHeatmap(
    val loaded: Boolean,
    /** true = narrow 7-day bar chart, false = 30-day strip. */
    val bars: Boolean,
    val cells: List<IosDashHeatCell>,
    val labels: List<IosDashHeatLabel>,
    val total: Int,
)

public data class IosDashRuleBar(
    val rule: String,
    val fraction: Double,
    val tone: String,
)

public data class IosDashGuardrails(
    val block: Int,
    val warn: Int,
    val pass: Int,
    val rules: List<IosDashRuleBar>,
)

public data class IosDashCompactRow(
    val sessionId: String,
    val name: String,
    val tone: String,
    val symbol: String,
    val age: String,
    val active: Boolean,
)

public data class IosDashSmokePill(
    val key: String,
    val label: String,
    val selected: Boolean,
)

public data class IosDashSmokeRow(
    val name: String,
    /** pass · fail · skip · active */
    val result: String,
)

public data class IosDashSmokeView(
    val runs: List<IosDashSmokeRun>,
    val selectedId: String,
    /** Selected but detail not fetched yet. */
    val loading: Boolean,
    /** running · failed · done · "" (nothing selected). */
    val status: String,
    val done: Int,
    val total: Int,
    val pct: Int,
    val pass: Int,
    val fail: Int,
    val skip: Int,
    val remaining: Int,
    val pills: List<IosDashSmokePill>,
    val rows: List<IosDashSmokeRow>,
    /** Pending filter selected — show "N pending" instead of rows. */
    val pendingOnly: Boolean,
)

/**
 * Dashboard state machine — the PWA `_dash` object. Not thread-safe: Swift
 * drives it from the main actor (loader callbacks hop to main first).
 */
public class IosDashEngine {
    private var sessions: List<Session> = emptyList()
    private var prds: List<PrdDto> = emptyList()
    private val boards = LinkedHashMap<String, IosDashBoard>()
    private val ekg = ArrayDeque<Pulse>()
    private val eventLog = ArrayDeque<IosDashEvent>()
    private val ganttTimings = HashMap<String, Timing>()
    private val treeCollapsed = HashSet<String>()
    private var costToday: Double = 0.0
    private var heat: List<AnalyticsBucketDto> = emptyList()
    private var heatLoaded: Boolean = false
    private var smokeRuns: List<IosDashSmokeRun> = emptyList()
    private var smokeSelected: String = ""
    private var smokeDetail: IosDashSmokeDetail? = null
    private var smokeFilter: String = "all"
    private var memStats: IosDashMemStats? = null
    private var webSearch: IosDashWebSearch? = null

    private class Pulse(val t: Double, val sid: String, val dy: Double)

    private class Timing(var start: Double, var end: Double?, var lastSeen: Double)

    // ── Inputs ────────────────────────────────────────────────────────────

    public fun setSessions(list: List<Session>) {
        sessions = list
    }

    public fun allSessions(): List<Session> = sessions

    /** Full ids of running / waiting / new sessions (board seeding). */
    public fun activeSessionIds(): List<String> = sessions.filter { isActive(it.state) }.map { it.fullId }

    /** Keep running/blocked/planning automata (PWA `_dash._prds` filter). */
    public fun setPrds(all: List<PrdDto>) {
        prds = all.filter { it.status == "running" || it.status == "blocked" || it.status == "planning" }
    }

    public fun activePrds(): List<PrdDto> = prds

    public fun prd(id: String): PrdDto? = prds.firstOrNull { it.id == id }

    public fun session(id: String): Session? = sessions.firstOrNull { it.fullId == id || it.id == id }

    public fun board(sessionId: String): IosDashBoard? {
        boards[sessionId]?.let { return it }
        val s = session(sessionId) ?: return null
        return boards[s.fullId] ?: boards[s.id]
    }

    /**
     * Cache a board. [live] = it came from a `hook_update` frame: also feed
     * the EKG ring (400), the live-event log (40) and Gantt story timings.
     */
    public fun applyBoard(
        board: IosDashBoard,
        nowMs: Double,
        live: Boolean,
    ) {
        boards[board.sessionId] = board
        if (!live) return
        ekg.addLast(Pulse(t = nowMs, sid = board.sessionId, dy = 0.5 + Random.nextDouble() * 0.5))
        while (ekg.size > EKG_MAX) ekg.removeFirst()
        val sess = session(board.sessionId)
        eventLog.addLast(
            IosDashEvent(
                timeLabel = IosDashParsers.hhmm(nowMs, withSeconds = true),
                name = sess?.let { displayName(it) } ?: board.sessionId.take(8),
                event = board.lastEvent.ifBlank { "update" },
                symbol = eventSymbol(board.lastEvent.ifBlank { "update" }),
                tool = board.lastTool.take(14),
                task = board.eventTask.take(32),
                tone = boardTone(board.state),
            ),
        )
        while (eventLog.size > EVENT_MAX) eventLog.removeFirst()
        if (board.sprintPrdId.isNotBlank()) {
            val p = prds.firstOrNull { it.id == board.sprintPrdId }
            if (p != null) {
                val si = p.stories.indexOfFirst { it.id == board.sprintId || it.title == board.sprintTitle }
                if (si >= 0) {
                    val key = "${p.id}:$si"
                    val t = ganttTimings[key]
                    if (t == null) {
                        ganttTimings[key] = Timing(start = nowMs, end = null, lastSeen = nowMs)
                    } else {
                        t.lastSeen = nowMs
                    }
                }
            }
        }
    }

    public fun setCost(totalUsd: Double) {
        costToday = totalUsd
    }

    public fun setHeatmap(buckets: List<AnalyticsBucketDto>) {
        heat = buckets
        heatLoaded = true
    }

    public fun setMemStats(stats: IosDashMemStats?) {
        memStats = stats
    }

    public fun memStats(): IosDashMemStats? = memStats

    public fun setWebSearch(stats: IosDashWebSearch?) {
        webSearch = stats
    }

    public fun webSearch(): IosDashWebSearch? = webSearch

    public fun toggleTreeRow(prdId: String) {
        if (!treeCollapsed.remove(prdId)) treeCollapsed.add(prdId)
    }

    // ── Stat bar + burn rate ──────────────────────────────────────────────

    public fun statBar(): IosDashStatBar {
        var done = 0
        var total = 0
        var block = 0
        var warn = 0
        for (b in boards.values) {
            done += b.tasksDone
            total += b.tasksTotal
            block += b.verdicts.count { it.outcome == "block" }
            warn += b.verdicts.count { it.outcome == "warn" }
        }
        return IosDashStatBar(
            sessions = sessions.size,
            active = sessions.count { isActive(it.state) },
            running = sessions.count { it.state == SessionState.Running || it.state == SessionState.New },
            costUsd = costToday,
            tasksDone = done,
            tasksTotal = total,
            block = block,
            warn = warn,
            automata = prds.size,
        )
    }

    // ── Automata tree card ────────────────────────────────────────────────

    public fun tree(): IosDashTree {
        val prdRows =
            prds.map { p ->
                val all = p.stories.flatMap { it.tasks }
                val pct = if (all.isEmpty()) 0 else (all.count { IosDashParsers.isDone(it.status) } * 100.0 / all.size).roundToInt()
                IosDashTreePrd(
                    id = p.id,
                    title = (p.title?.takeIf { it.isNotBlank() } ?: p.id).take(20),
                    tone = prdTone(p.status),
                    pct = pct,
                    expanded = p.id !in treeCollapsed,
                    stories =
                        p.stories.mapIndexed { i, st ->
                            IosDashTreeStory(
                                title = st.title.ifBlank { "S${i + 1}" }.take(18),
                                tone = storyTone(st.status),
                                symbol = storySymbol(st.status),
                                tasksDone = st.tasks.count { IosDashParsers.isDone(it.status) },
                                tasksTotal = st.tasks.size,
                            )
                        },
                )
            }
        val sess =
            sessions.filter { isActive(it.state) }.take(8).map { s ->
                val b = board(s.fullId)
                IosDashTreeSession(
                    id = s.fullId,
                    name = displayName(s).take(20),
                    tone = if (s.state == SessionState.Waiting) "warning" else "accent",
                    health = healthKey(b),
                    focus = b?.focusTask.orEmpty().take(24),
                    runtimeSymbol = runtimeSymbol(s),
                )
            }
        return IosDashTree(prds = prdRows, sessions = sess)
    }

    // ── Live events card ──────────────────────────────────────────────────

    public fun events(): List<IosDashEvent> = eventLog.reversed()

    // ── Sessions sparkline card (5 fps) ───────────────────────────────────

    public fun sparkRows(nowMs: Double): List<IosDashSparkRow> {
        val start = nowMs - SPARK_BUCKETS * SPARK_BUCKET_MS
        return sessions.filter { isActive(it.state) }.take(5).map { s ->
            val counts = DoubleArray(SPARK_BUCKETS)
            for (p in ekg) {
                if (p.t < start || !matches(p.sid, s)) continue
                val bi = min(SPARK_BUCKETS - 1, floor((p.t - start) / SPARK_BUCKET_MS).toInt())
                if (bi >= 0) counts[bi] += 1.0
            }
            val maxB = max(1.0, counts.maxOrNull() ?: 1.0)
            IosDashSparkRow(
                sessionId = s.fullId,
                name = displayName(s).take(9),
                state = wireState(s.state),
                tone = sessionTone(s.state),
                buckets = counts.map { it / maxB },
            )
        }
    }

    // ── Multi-EKG card (full rate) ────────────────────────────────────────

    public fun ekg(
        nowMs: Double,
        windowSec: Double,
    ): IosDashEkg {
        val active = sessions.filter { isActive(it.state) }.take(6)
        val channels =
            active.map { s ->
                IosDashEkgChannel(
                    sessionId = s.fullId,
                    name = displayName(s).take(9),
                    tone = sessionTone(s.state),
                    health = healthKey(board(s.fullId)),
                    pulses = pulses(nowMs, windowSec) { matches(it, s) },
                )
            }
        val idle = if (active.isEmpty()) pulses(nowMs, windowSec) { true } else emptyList()
        return IosDashEkg(channels = channels, idlePulses = idle, idleTone = "accent")
    }

    private fun pulses(
        nowMs: Double,
        windowSec: Double,
        accept: (String) -> Boolean,
    ): List<IosDashPulse> {
        val out = mutableListOf<IosDashPulse>()
        for (p in ekg) {
            val age = (nowMs - p.t) / 1000.0
            if (age < 0.0 || age > windowSec || !accept(p.sid)) continue
            out.add(IosDashPulse(ageSec = age, dy = p.dy))
        }
        return out
    }

    // ── Network (constellation) narrow list ───────────────────────────────

    public fun compactRows(nowMs: Double): List<IosDashCompactRow> =
        sessions.map { s ->
            IosDashCompactRow(
                sessionId = s.fullId,
                name = displayName(s).take(18),
                tone = sessionTone(s.state),
                symbol = stateSymbol(s.state),
                age = ago(nowMs - s.createdAt.toEpochMilliseconds().toDouble()),
                active = isActive(s.state),
            )
        }

    // ── Timeline · 6h (Gantt) ─────────────────────────────────────────────

    public fun gantt(nowMs: Double): IosDashGantt {
        val winMs = 6.0 * HOUR_MS
        val winStart = nowMs - winMs
        fun frac(ts: Double): Double = ((ts - winStart) / winMs).coerceIn(0.0, 1.0)
        val ticks = mutableListOf<IosDashTick>()
        var h = kotlin.math.ceil(winStart / HOUR_MS)
        while (h * HOUR_MS <= nowMs) {
            val ts = h * HOUR_MS
            ticks.add(IosDashTick(fraction = frac(ts), label = IosDashParsers.hhmm(ts, withSeconds = false)))
            h += 1.0
        }
        val rows =
            prds.map { p ->
                val stories = p.stories
                val all = stories.flatMap { it.tasks }
                val pct = if (all.isEmpty()) 0 else (all.count { IosDashParsers.isDone(it.status) } * 100.0 / all.size).roundToInt()
                val completedCount = stories.count { IosDashParsers.isDone(it.status) }
                val slot = winMs / max(stories.size, 1)
                val storyRows =
                    stories.mapIndexed { si, st ->
                        val timing = ganttTimings["${p.id}:$si"]
                        val isRunning = st.status == "running" || st.status == "in_progress"
                        val isDone = IosDashParsers.isDone(st.status)
                        val start: Double
                        val end: Double
                        if (timing != null) {
                            start = timing.start
                            end = if (isDone) (timing.end ?: timing.lastSeen) else nowMs
                        } else if (isDone) {
                            start = winStart + si * slot
                            end = winStart + (si + 1) * slot
                        } else if (isRunning) {
                            start = winStart + completedCount * slot
                            end = nowMs
                        } else {
                            start = nowMs + (si - completedCount) * (winMs / 12.0)
                            end = start + winMs / 12.0
                        }
                        IosDashGanttStory(
                            title = st.title.ifBlank { "Story ${si + 1}" }.take(24),
                            tasks = if (st.tasks.isEmpty()) "" else "${st.tasks.count { IosDashParsers.isDone(it.status) }}/${st.tasks.size}",
                            tone = storyTone(st.status),
                            symbol = storySymbol(st.status),
                            startFraction = frac(start),
                            endFraction = frac(end),
                            pending = st.status.isBlank() || st.status == "draft" || st.status == "not_started" || st.status == "pending",
                            running = isRunning,
                            completed = isDone,
                        )
                    }
                IosDashGanttPrd(
                    id = p.id,
                    title = (p.title?.takeIf { it.isNotBlank() } ?: p.id).take(26),
                    tone = storyTone(p.status),
                    pct = pct,
                    expanded = p.id !in treeCollapsed,
                    stories = storyRows,
                )
            }
        return IosDashGantt(ticks = ticks, prds = rows)
    }

    // ── 30-Day Activity heatmap ───────────────────────────────────────────

    /** [narrow] = PWA narrowMode (card < 300 px or cs ≤ 3) → 7-day bars. */
    public fun heatmap(narrow: Boolean): IosDashHeatmap {
        if (!heatLoaded || heat.isEmpty()) {
            return IosDashHeatmap(loaded = false, bars = narrow, cells = emptyList(), labels = emptyList(), total = 0)
        }
        val total = heat.sumOf { it.sessionCount }
        if (narrow) {
            val last7 = heat.takeLast(7)
            val maxT = max(1, last7.maxOfOrNull { it.sessionCount } ?: 1)
            val cells =
                last7.map { b ->
                    val t = b.sessionCount
                    val errRate = if (t > 0) (b.failed + b.killed).toDouble() / t else 0.0
                    val tone: String =
                        when {
                            t == 0 -> "border"
                            errRate > 0.2 -> "error"
                            errRate >= 0.05 -> "warning"
                            else -> "success"
                        }
                    IosDashHeatCell(
                        heightFraction = max(0.02, t.toDouble() / maxT),
                        tone = tone,
                        alpha = if (t == 0) 1.0 else 0.8,
                    )
                }
            return IosDashHeatmap(loaded = true, bars = true, cells = cells, labels = emptyList(), total = total)
        }
        val cols = heat.takeLast(30)
        val maxC = max(1, heat.maxOfOrNull { it.sessionCount } ?: 1)
        val cells =
            cols.map { b ->
                val n = b.sessionCount
                val errRate = if (n > 0) (b.failed + b.killed).toDouble() / n else 0.0
                val t = n.toDouble() / maxC
                val tone: String
                val alpha: Double
                if (n == 0) {
                    tone = "border"
                    alpha = 1.0
                } else if (errRate > 0.3) {
                    tone = "error"
                    alpha = 0.7
                } else {
                    tone = "accent"
                    alpha = if (t < 0.25) 0.25 else if (t < 0.5) 0.5 else if (t < 0.75) 0.75 else 1.0
                }
                IosDashHeatCell(heightFraction = 1.0, tone = tone, alpha = alpha)
            }
        val labels = mutableListOf<IosDashHeatLabel>()
        var i = 0
        while (i < cols.size) {
            labels.add(IosDashHeatLabel(index = i, label = cols[i].date.drop(5)))
            i += 7
        }
        return IosDashHeatmap(loaded = true, bars = false, cells = cells, labels = labels, total = total)
    }

    // ── Guardrails card ───────────────────────────────────────────────────

    public fun guardrails(): IosDashGuardrails {
        var block = 0
        var warn = 0
        var pass = 0
        val byRule = LinkedHashMap<String, IntArray>()
        for (b in boards.values) {
            for (v in b.verdicts) {
                val idx: Int =
                    when (v.outcome) {
                        "block" -> 0
                        "warn" -> 1
                        else -> 2
                    }
                when (idx) {
                    0 -> block++
                    1 -> warn++
                    else -> pass++
                }
                val counts = byRule.getOrPut(v.guardrail) { IntArray(3) }
                counts[idx] = counts[idx] + 1
            }
        }
        val rules = byRule.entries.sortedByDescending { it.value[0] + it.value[1] }
        val maxV = max(1, rules.maxOfOrNull { it.value.sum() } ?: 1)
        val bars =
            rules.take(8).map { (rule, v) ->
                val tone: String =
                    when {
                        v[0] > 0 -> "error"
                        v[1] > 0 -> "warning"
                        else -> "success"
                    }
                IosDashRuleBar(
                    rule = if (rule.length > 28) rule.take(27) + "…" else rule,
                    fraction = v.sum().toDouble() / maxV,
                    tone = tone,
                )
            }
        return IosDashGuardrails(block = block, warn = warn, pass = pass, rules = bars)
    }

    // ── Smoke Run card ────────────────────────────────────────────────────

    /** Apply a fresh run list; mirrors the PWA keep/auto-select rules. Returns the selected id ("" = none). */
    public fun setSmokeRuns(runs: List<IosDashSmokeRun>): String {
        smokeRuns = runs
        if (smokeSelected.isNotEmpty() && runs.none { it.id == smokeSelected }) {
            smokeSelected = ""
            smokeDetail = null
        }
        if (smokeSelected.isEmpty()) {
            runs.firstOrNull { it.active }?.let { smokeSelected = it.id }
        }
        return smokeSelected
    }

    public fun hasActiveSmoke(): Boolean = smokeRuns.any { it.active }

    public fun smokeSelectedId(): String = smokeSelected

    /** Toggle selection (PWA `_smokeSelectRun`). Returns the id to fetch detail for, or "". */
    public fun selectSmokeRun(id: String): String {
        if (smokeSelected == id) {
            smokeSelected = ""
            smokeDetail = null
            return ""
        }
        smokeSelected = id
        smokeDetail = null
        return id
    }

    /** [detail] null = 404 (run gone) → drop the selection. */
    public fun setSmokeDetail(
        id: String,
        detail: IosDashSmokeDetail?,
    ) {
        if (id != smokeSelected) return
        if (detail == null) {
            smokeSelected = ""
            smokeDetail = null
        } else {
            smokeDetail = detail
        }
    }

    public fun setSmokeFilter(key: String) {
        smokeFilter = key
    }

    public fun removeSmokeRun(id: String) {
        smokeRuns = smokeRuns.filter { it.id != id }
        if (smokeSelected == id) {
            smokeSelected = ""
            smokeDetail = null
        }
    }

    public fun clearSmokeRuns() {
        smokeRuns = emptyList()
        smokeSelected = ""
        smokeDetail = null
    }

    public fun smokeView(): IosDashSmokeView {
        val d = smokeDetail
        if (smokeSelected.isEmpty() || d == null) {
            return IosDashSmokeView(
                runs = smokeRuns,
                selectedId = smokeSelected,
                loading = smokeSelected.isNotEmpty(),
                status = "",
                done = 0,
                total = 0,
                pct = 0,
                pass = 0,
                fail = 0,
                skip = 0,
                remaining = 0,
                pills = emptyList(),
                rows = emptyList(),
                pendingOnly = false,
            )
        }
        val done = if (d.sections.isNotEmpty()) d.sections.size else d.pass + d.fail + d.skip
        val remaining = max(0, d.total - done - (if (d.active) 1 else 0))
        val pct = if (d.total > 0) min(100, (done * 100.0 / d.total).roundToInt()) else 0
        val all = mutableListOf<IosDashSmokeRow>()
        if (d.active && d.currentId.isNotBlank()) {
            all.add(IosDashSmokeRow(name = d.currentName.ifBlank { d.currentId }, result = "active"))
        }
        for (s in d.sections) all.add(IosDashSmokeRow(name = s.name.ifBlank { s.id }, result = s.result))
        if (d.sections.isEmpty()) {
            if (d.pass > 0) all.add(IosDashSmokeRow(name = "${d.pass} tests passed", result = "pass"))
            if (d.fail > 0) all.add(IosDashSmokeRow(name = "${d.fail} tests failed", result = "fail"))
            if (d.skip > 0) all.add(IosDashSmokeRow(name = "${d.skip} tests skipped", result = "skip"))
        }
        val filt = smokeFilter
        val rows: List<IosDashSmokeRow> =
            when (filt) {
                "all" -> all
                "pending" -> emptyList()
                else -> all.filter { it.result == filt }
            }
        val pills = mutableListOf<IosDashSmokePill>()
        pills.add(IosDashSmokePill(key = "all", label = "All ${done + (if (d.active) 1 else 0)}", selected = filt == "all"))
        if (d.active) pills.add(IosDashSmokePill(key = "active", label = "▶ Active", selected = filt == "active"))
        pills.add(IosDashSmokePill(key = "pass", label = "✓ ${d.pass}", selected = filt == "pass"))
        if (d.fail > 0) pills.add(IosDashSmokePill(key = "fail", label = "✗ ${d.fail}", selected = filt == "fail"))
        if (d.skip > 0) pills.add(IosDashSmokePill(key = "skip", label = "⏭ ${d.skip}", selected = filt == "skip"))
        if (remaining > 0) pills.add(IosDashSmokePill(key = "pending", label = "○ $remaining", selected = filt == "pending"))
        val status: String =
            when {
                d.active -> "running"
                d.fail > 0 -> "failed"
                else -> "done"
            }
        return IosDashSmokeView(
            runs = smokeRuns,
            selectedId = smokeSelected,
            loading = false,
            status = status,
            done = done,
            total = d.total,
            pct = pct,
            pass = d.pass,
            fail = d.fail,
            skip = d.skip,
            remaining = remaining,
            pills = pills,
            rows = rows,
            pendingOnly = filt == "pending",
        )
    }

    // ── Helpers (shared with IosDashConstellation) ────────────────────────

    internal fun boardsSnapshot(): Map<String, IosDashBoard> = boards

    internal fun matches(
        sid: String,
        s: Session,
    ): Boolean = sid == s.fullId || sid == s.id

    public companion object {
        internal const val EKG_MAX: Int = 400
        internal const val EVENT_MAX: Int = 40
        internal const val SPARK_BUCKETS: Int = 60
        internal const val SPARK_BUCKET_MS: Double = 2000.0
        internal const val HOUR_MS: Double = 3_600_000.0

        public fun displayName(s: Session): String =
            s.name?.takeIf { it.isNotBlank() } ?: s.taskSummary?.takeIf { it.isNotBlank() } ?: s.id

        /** running / waiting / new count as "active" (PWA running|generating|waiting_input). */
        public fun isActive(state: SessionState): Boolean =
            state == SessionState.Running || state == SessionState.Waiting || state == SessionState.New

        /** Server wire state string (PWA shows the raw state). */
        public fun wireState(state: SessionState): String =
            when (state) {
                SessionState.New -> "new"
                SessionState.Running -> "running"
                SessionState.Waiting -> "waiting_input"
                SessionState.RateLimited -> "rate_limited"
                SessionState.Completed -> "complete"
                SessionState.Killed -> "killed"
                SessionState.Error -> "failed"
            }

        /** PWA `_dashNodeColor`. */
        public fun sessionTone(state: SessionState): String =
            when (state) {
                SessionState.Running, SessionState.New -> "accent"
                SessionState.Waiting -> "warning"
                SessionState.Completed -> "success"
                SessionState.Error -> "error"
                else -> "muted"
            }

        public fun boardTone(state: String): String =
            when (state) {
                "running", "generating" -> "accent"
                "waiting", "waiting_input" -> "warning"
                "completed", "complete", "done" -> "success"
                "failed", "blocked", "error" -> "error"
                else -> "muted"
            }

        public fun prdTone(status: String): String =
            when (status) {
                "running" -> "accent"
                "blocked" -> "error"
                "planning" -> "warning"
                else -> "muted"
            }

        public fun storyTone(status: String): String =
            when (status) {
                "completed", "complete" -> "success"
                "running", "in_progress" -> "accent"
                "blocked", "failed" -> "error"
                "needs_review" -> "warning"
                "planning" -> "accent2"
                else -> "muted"
            }

        public fun storySymbol(status: String): String =
            when (status) {
                "completed", "complete" -> "checkmark"
                "running", "in_progress" -> "play.fill"
                "blocked" -> "exclamationmark"
                "needs_review" -> "questionmark"
                "failed" -> "xmark"
                else -> "circle"
            }

        public fun stateSymbol(state: SessionState): String =
            when (state) {
                SessionState.Running, SessionState.New -> "play.fill"
                SessionState.Waiting -> "pause.fill"
                SessionState.Completed -> "checkmark"
                SessionState.Error -> "xmark"
                SessionState.RateLimited -> "exclamationmark"
                SessionState.Killed -> "circle"
            }

        public fun eventSymbol(event: String): String =
            when (event) {
                "Stop" -> "stop.fill"
                "PostToolUse" -> "play.fill"
                "UserPromptSubmit" -> "text.bubble"
                "SubagentStop" -> "stop"
                else -> "smallcircle.filled.circle"
            }

        public fun healthKey(b: IosDashBoard?): String =
            when {
                b == null -> "none"
                b.hookHealth == "alive" -> "alive"
                else -> "stale"
            }

        /** PWA `_runtimeBadge` (compute node → globe, proxy → arrow, local → desktop). */
        public fun runtimeSymbol(s: Session): String {
            if (!s.computeNodeRef.isNullOrBlank()) return "globe"
            val bf = s.backend.orEmpty().lowercase()
            if (bf.contains("proxy")) return "arrow.right"
            if (bf == "claude-code" || bf == "opencode-acp" || bf == "local") return "desktopcomputer"
            if (bf.isNotEmpty()) return "globe"
            return ""
        }

        /** PWA `timeAgo` short form. */
        public fun ago(ms: Double): String {
            val s = max(0.0, ms) / 1000.0
            return when {
                s < 60.0 -> "${s.toInt()}s"
                s < 3600.0 -> "${(s / 60.0).toInt()}m"
                s < 86400.0 -> "${(s / 3600.0).toInt()}h"
                else -> "${(s / 86400.0).toInt()}d"
            }
        }

        /** EKG trace decay for a pulse [ageSec] old (PWA exp(-(n-i)/50) per pixel ≈ 20 s). */
        public fun decay(ageSec: Double): Double = exp(-ageSec / 20.0)
    }
}
