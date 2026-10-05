package com.dmzs.datawatchclient.util

import com.dmzs.datawatchclient.transport.dto.OrchestratorEdgeDto
import com.dmzs.datawatchclient.transport.dto.OrchestratorNodeDto

/** One positioned DAG node. [x] is relative to the canvas centre line, [y] from the top. */
public data class PrdDagNode(
    val id: String,
    /** "S" story / "T" task / "•" other — the glyph drawn inside the node. */
    val kindGlyph: String,
    /** Name under the node, truncated to 14 chars like Android. */
    val label: String,
    /** Lower-cased node status (drives the colour). */
    val status: String,
    val x: Double,
    val y: Double,
)

/** One edge between two positioned nodes (same coordinate space as [PrdDagNode]). */
public data class PrdDagEdge(
    val fromX: Double,
    val fromY: Double,
    val toX: Double,
    val toY: Double,
)

public data class PrdDagLayoutResult(
    val nodes: List<PrdDagNode>,
    val edges: List<PrdDagEdge>,
    /** Total height of the laid-out graph (for the scroll / fit). */
    val height: Double,
)

/**
 * Automaton DAG layout — the same Kahn's-algorithm layered layout as Android's
 * `PrdDagCanvas` (composeApp autonomous/), shared so iOS draws the identical graph.
 * Units are dp / points: node radius 28, horizontal gap 24, vertical gap 56.
 */
public object PrdDagLayout {
    public const val NODE_RADIUS: Double = 28.0
    public const val H_SPACING: Double = 24.0
    public const val V_SPACING: Double = 56.0

    public fun layout(
        nodes: List<OrchestratorNodeDto>,
        edges: List<OrchestratorEdgeDto>,
    ): PrdDagLayoutResult {
        if (nodes.isEmpty()) return PrdDagLayoutResult(nodes = emptyList(), edges = emptyList(), height = 0.0)
        val r = NODE_RADIUS
        val idSet = nodes.map { it.id }.toSet()
        val inDegree = nodes.associate { it.id to 0 }.toMutableMap()
        val adj = nodes.associate { it.id to mutableListOf<String>() }
        for (e in edges) {
            if (e.from in idSet && e.to in idSet) {
                adj[e.from]?.add(e.to)
                inDegree[e.to] = (inDegree[e.to] ?: 0) + 1
            }
        }
        val rank = mutableMapOf<String, Int>()
        val queue = ArrayDeque<String>()
        inDegree.entries.filter { it.value == 0 }.forEach { queue.add(it.key) }
        var maxRank = 0
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val curRank = rank[cur] ?: 0
            if (curRank > maxRank) maxRank = curRank
            adj[cur]?.forEach { next ->
                val nextRank = curRank + 1
                if ((rank[next] ?: -1) < nextRank) rank[next] = nextRank
                inDegree[next] = (inDegree[next] ?: 1) - 1
                if (inDegree[next] == 0) queue.add(next)
            }
        }
        // Unranked (cycle) nodes go to rank 0, as on Android.
        nodes.forEach { if (it.id !in rank) rank[it.id] = 0 }

        val byRank = nodes.groupBy { rank[it.id] ?: 0 }
        val placed = mutableListOf<PrdDagNode>()
        for (layerIdx in 0..maxRank) {
            val layer = byRank[layerIdx] ?: emptyList()
            val y = layerIdx * (r * 2 + V_SPACING) + r + r * 0.5
            val totalW = layer.size * (r * 2 + H_SPACING) - H_SPACING
            layer.forEachIndexed { idx, node ->
                val x = -totalW / 2.0 + idx * (r * 2 + H_SPACING) + r
                val name = node.name ?: node.id
                val glyph: String =
                    when (node.kind?.lowercase()) {
                        "story" -> "S"
                        "task" -> "T"
                        else -> "•"
                    }
                placed +=
                    PrdDagNode(
                        id = node.id,
                        kindGlyph = glyph,
                        label = if (name.length > 14) name.take(13) + "…" else name,
                        status = node.status.lowercase(),
                        x = x,
                        y = y,
                    )
            }
        }
        val pos = placed.associateBy { it.id }
        val placedEdges =
            edges.mapNotNull { e ->
                val a = pos[e.from] ?: return@mapNotNull null
                val b = pos[e.to] ?: return@mapNotNull null
                PrdDagEdge(fromX = a.x, fromY = a.y, toX = b.x, toY = b.y)
            }
        val height = (maxRank + 1) * (r * 2 + V_SPACING)
        return PrdDagLayoutResult(nodes = placed, edges = placedEdges, height = height)
    }
}
