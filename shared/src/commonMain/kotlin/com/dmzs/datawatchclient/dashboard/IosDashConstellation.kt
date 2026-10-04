package com.dmzs.datawatchclient.dashboard

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** One drawable node of the Network (constellation) card. */
public data class IosDashNode(
    val id: String,
    val x: Double,
    val y: Double,
    val radius: Double,
    val tone: String,
    val label: String,
    val state: String,
    val symbol: String,
    /** running / waiting → animated pulse ring. */
    val active: Boolean,
    /** alive · stale · "" — static health ring. */
    val ring: String,
    /** hook_health missing on a session node → 0.4 opacity. */
    val dim: Boolean,
    val threats: Int,
    val prd: Boolean,
)

public data class IosDashEdge(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
)

/**
 * Force-directed layout for the PWA `orbital` card (`_dashForceStep`,
 * `_dashReconcileNodes`). The PWA steps physics once per animation frame
 * (~60 Hz), redraws the SVG at ~10 fps and reconciles nodes at ~2 fps;
 * [advance] reproduces that from whatever cadence Swift's TimelineView
 * ticks at.
 */
public class IosDashConstellation(private val engine: IosDashEngine) {
    private class Node(
        var x: Double,
        var y: Double,
        var vx: Double,
        var vy: Double,
        val prd: Boolean,
    )

    private val nodes = LinkedHashMap<String, Node>()
    private var edges: List<Pair<String, String>> = emptyList()
    private var lastStepMs: Double = 0.0
    private var lastReconcileMs: Double = 0.0
    private var initialised: Boolean = false

    /** Step physics up to "now"; reconcile node set every 500 ms. */
    public fun advance(
        nowMs: Double,
        width: Double,
        height: Double,
    ) {
        if (!initialised) {
            init(width, height)
            lastStepMs = nowMs
            lastReconcileMs = nowMs
        }
        if (nowMs - lastReconcileMs >= 500.0) {
            reconcile(width, height)
            lastReconcileMs = nowMs
        }
        val steps = min(10, max(0, ((nowMs - lastStepMs) / FRAME_MS).toInt()))
        if (steps > 0) {
            repeat(steps) { step(width, height) }
            lastStepMs = nowMs
        }
    }

    /** Reduce Motion: reconcile and run the simulation to rest in one go (static frame). */
    public fun settle(
        width: Double,
        height: Double,
    ) {
        if (!initialised) init(width, height)
        reconcile(width, height)
        repeat(SETTLE_STEPS) { step(width, height) }
    }

    public fun snapshot(): List<IosDashNode> {
        val sessions = engine.allSessions()
        val out = mutableListOf<IosDashNode>()
        for ((id, n) in nodes) {
            if (n.prd) {
                val p = engine.prd(id) ?: continue
                out.add(
                    IosDashNode(
                        id = id,
                        x = n.x,
                        y = n.y,
                        radius = 22.0,
                        tone = IosDashEngine.prdTone(p.status),
                        label = trimLabel(p.title?.takeIf { it.isNotBlank() } ?: p.name.ifBlank { p.id }),
                        state = p.status,
                        symbol = IosDashEngine.storySymbol(p.status),
                        active = p.status == "running",
                        ring = "",
                        dim = false,
                        threats = 0,
                        prd = true,
                    ),
                )
            } else {
                val s = sessions.firstOrNull { it.fullId == id } ?: continue
                val b = engine.board(id)
                val health = b?.hookHealth ?: "missing"
                out.add(
                    IosDashNode(
                        id = id,
                        x = n.x,
                        y = n.y,
                        radius = 16.0,
                        tone = IosDashEngine.sessionTone(s.state),
                        label = trimLabel(IosDashEngine.displayName(s)),
                        state = IosDashEngine.wireState(s.state),
                        symbol = IosDashEngine.stateSymbol(s.state),
                        active = IosDashEngine.isActive(s.state),
                        ring = if (health == "alive" || health == "stale") health else "",
                        dim = health == "missing",
                        threats = b?.verdicts?.count { it.outcome == "block" || it.outcome == "warn" } ?: 0,
                        prd = false,
                    ),
                )
            }
        }
        return out
    }

    public fun edgeLines(): List<IosDashEdge> =
        edges.mapNotNull { (a, b) ->
            val na = nodes[a] ?: return@mapNotNull null
            val nb = nodes[b] ?: return@mapNotNull null
            IosDashEdge(x1 = na.x, y1 = na.y, x2 = nb.x, y2 = nb.y)
        }

    /** Node id under a tap (hit radius r + 6, PWA invisible hit circle); "" = none. */
    public fun hit(
        x: Double,
        y: Double,
    ): String {
        var best = ""
        var bestD = Double.MAX_VALUE
        for ((id, n) in nodes) {
            val r = (if (n.prd) 22.0 else 16.0) + 6.0
            val dx = n.x - x
            val dy = n.y - y
            val d = sqrt(dx * dx + dy * dy)
            if (d <= r && d < bestD) {
                best = id
                bestD = d
            }
        }
        return best
    }

    public fun isPrd(id: String): Boolean = nodes[id]?.prd ?: false

    // ── PWA _dashInitNodes / _dashReconcileNodes / _dashForceStep ─────────

    private fun init(
        w: Double,
        h: Double,
    ) {
        initialised = true
        nodes.clear()
        val sessions = engine.allSessions()
        val cx = w / 2.0
        val cy = h / 2.0
        val r = min(cx, cy) * 0.55
        sessions.forEachIndexed { i, s ->
            val angle = 2.0 * PI * i / max(1, sessions.size)
            nodes[s.fullId] = Node(x = cx + cos(angle) * r, y = cy + sin(angle) * r, vx = 0.0, vy = 0.0, prd = false)
        }
        for (p in engine.activePrds()) {
            if (nodes.containsKey(p.id)) continue
            nodes[p.id] =
                Node(
                    x = cx + (Random.nextDouble() - 0.5) * w * 0.4,
                    y = cy + (Random.nextDouble() - 0.5) * h * 0.4,
                    vx = 0.0,
                    vy = 0.0,
                    prd = true,
                )
        }
        rebuildEdges()
    }

    private fun reconcile(
        w: Double,
        h: Double,
    ) {
        val sessions = engine.allSessions()
        val prds = engine.activePrds()
        val live = HashSet<String>()
        sessions.forEach { live.add(it.fullId) }
        prds.forEach { live.add(it.id) }
        nodes.keys.retainAll(live)
        val cx = w / 2.0
        val cy = h / 2.0
        for (s in sessions) {
            if (nodes.containsKey(s.fullId)) continue
            nodes[s.fullId] =
                Node(
                    x = cx + (Random.nextDouble() - 0.5) * 60.0,
                    y = cy + (Random.nextDouble() - 0.5) * 60.0,
                    vx = (Random.nextDouble() - 0.5) * 2.0,
                    vy = (Random.nextDouble() - 0.5) * 2.0,
                    prd = false,
                )
        }
        for (p in prds) {
            if (nodes.containsKey(p.id)) continue
            nodes[p.id] =
                Node(
                    x = cx + (Random.nextDouble() - 0.5) * w * 0.3,
                    y = cy + (Random.nextDouble() - 0.5) * h * 0.3,
                    vx = 0.0,
                    vy = 0.0,
                    prd = true,
                )
        }
        rebuildEdges()
    }

    /** Edges from hook telemetry `parent_session_id` (PWA uses session parent_id). */
    private fun rebuildEdges() {
        val out = mutableListOf<Pair<String, String>>()
        for ((id, b) in engine.boardsSnapshot()) {
            val child = engine.session(id)?.fullId ?: continue
            val parent = engine.session(b.parentSessionId)?.fullId ?: continue
            if (nodes.containsKey(child) && nodes.containsKey(parent) && child != parent) out.add(parent to child)
        }
        edges = out
    }

    internal fun step(
        w: Double,
        h: Double,
    ) {
        val list = nodes.values.toList()
        if (list.size < 2) return
        val cx = w / 2.0
        val cy = h / 2.0
        for (i in list.indices) {
            val a = list[i]
            for (j in i + 1 until list.size) {
                val b = list[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val d2 = dx * dx + dy * dy + 1.0
                val f = min(2500.0 / d2, 3.0)
                val d = sqrt(d2)
                val fx = dx / d * f
                val fy = dy / d * f
                a.vx -= fx
                a.vy -= fy
                b.vx += fx
                b.vy += fy
            }
            a.vx += (cx - a.x) * 0.008
            a.vy += (cy - a.y) * 0.008
        }
        val target = min(w, h) * 0.22
        for ((from, to) in edges) {
            val a = nodes[from] ?: continue
            val b = nodes[to] ?: continue
            val dx = b.x - a.x
            val dy = b.y - a.y
            val d = sqrt(dx * dx + dy * dy) + 0.01
            val f = (d - target) * 0.015
            val fx = dx / d * f
            val fy = dy / d * f
            a.vx += fx
            a.vy += fy
            b.vx -= fx
            b.vy -= fy
        }
        for (n in list) {
            val r = if (n.prd) 20.0 else 14.0
            n.vx *= 0.72
            n.vy *= 0.72
            n.x = (n.x + n.vx).coerceIn(r + 2.0, max(r + 2.0, w - r - 2.0))
            n.y = (n.y + n.vy).coerceIn(r + 14.0, max(r + 14.0, h - r - 2.0))
        }
    }

    internal fun nodeCount(): Int = nodes.size

    private fun trimLabel(s: String): String {
        val l = s.take(18)
        return if (l.length > 16) l.take(15) + "…" else l
    }

    private companion object {
        const val FRAME_MS: Double = 1000.0 / 60.0
        const val SETTLE_STEPS: Int = 180
    }
}
