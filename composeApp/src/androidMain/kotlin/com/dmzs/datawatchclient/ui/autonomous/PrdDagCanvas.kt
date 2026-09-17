package com.dmzs.datawatchclient.ui.autonomous

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.transport.dto.OrchestratorEdgeDto
import com.dmzs.datawatchclient.transport.dto.OrchestratorGraphDto
import com.dmzs.datawatchclient.transport.dto.OrchestratorNodeDto
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private fun nodeColor(status: String): Color = when (status.lowercase()) {
    "running", "in_progress" -> Color(0xFF3B82F6)
    "complete", "completed", "done" -> Color(0xFF10B981)
    "failed" -> Color(0xFFEF4444)
    "blocked" -> Color(0xFFF59E0B)
    "cancelled", "canceled" -> Color(0xFF94A3B8)
    "needs_review", "revisions_asked" -> Color(0xFF8B5CF6)
    else -> Color(0xFF64748B) // pending / unknown
}

private data class NodeLayout(
    val node: OrchestratorNodeDto,
    val x: Float,
    val y: Float,
)

/**
 * Assign ranks via BFS (Kahn's algorithm), then lay nodes out in horizontal layers.
 * Returns a map from node id → (x, y) in graph coordinate space.
 */
private fun layoutGraph(
    nodes: List<OrchestratorNodeDto>,
    edges: List<OrchestratorEdgeDto>,
    nodeRadius: Float,
    hSpacing: Float,
    vSpacing: Float,
): List<NodeLayout> {
    if (nodes.isEmpty()) return emptyList()

    val idSet = nodes.map { it.id }.toSet()
    val inDegree = nodes.associate { it.id to 0 }.toMutableMap()
    val adj = nodes.associate { it.id to mutableListOf<String>() }.toMutableMap()

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
    // Any unranked node (cycle or disconnected) goes at rank 0
    nodes.forEach { if (it.id !in rank) rank[it.id] = 0 }

    val byRank = nodes.groupBy { rank[it.id] ?: 0 }
    val layers = (0..maxRank).map { r -> byRank[r] ?: emptyList() }

    val result = mutableListOf<NodeLayout>()
    layers.forEachIndexed { layerIdx, layerNodes ->
        val y = layerIdx * (nodeRadius * 2 + vSpacing) + nodeRadius
        val totalW = layerNodes.size * (nodeRadius * 2 + hSpacing) - hSpacing
        layerNodes.forEachIndexed { idx, node ->
            val x = -totalW / 2f + idx * (nodeRadius * 2 + hSpacing) + nodeRadius
            result.add(NodeLayout(node, x, y))
        }
    }
    return result
}

@Composable
internal fun PrdDagCanvas(
    graph: OrchestratorGraphDto,
    modifier: Modifier = Modifier,
) {
    val nodeRadius = 28.dp
    val hSpacing = 24.dp
    val vSpacing = 56.dp

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.3f, 4f)
                    offset += pan
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            val nodeRadiusPx = nodeRadius.toPx()
            val hSpacingPx = hSpacing.toPx()
            val vSpacingPx = vSpacing.toPx()
            val textSize = (nodeRadiusPx * 0.38f).coerceIn(10f, 16f)
            val labelSize = textSize * 0.82f

            val layouts = layoutGraph(
                graph.nodes, graph.edges,
                nodeRadiusPx, hSpacingPx, vSpacingPx,
            )
            val posById = layouts.associate { it.node.id to Offset(size.width / 2 + it.x, it.y + nodeRadiusPx * 0.5f) }

            // Draw edges
            for (edge in graph.edges) {
                val from = posById[edge.from] ?: continue
                val to = posById[edge.to] ?: continue
                drawArrow(
                    from = from,
                    to = to,
                    nodeRadius = nodeRadiusPx,
                    color = surfaceVariant.copy(alpha = 0.9f),
                )
            }

            // Draw nodes
            drawIntoCanvas { canvas ->
                val nativePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                layouts.forEach { layout ->
                    val center = posById[layout.node.id] ?: return@forEach
                    val color = nodeColor(layout.node.status)

                    // Shadow / halo
                    drawCircle(color.copy(alpha = 0.15f), nodeRadiusPx + 4f, center)
                    // Fill
                    drawCircle(color.copy(alpha = 0.85f), nodeRadiusPx, center)
                    // Stroke
                    drawCircle(color, nodeRadiusPx, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))

                    // Node label (kind abbreviation)
                    nativePaint.color = android.graphics.Color.WHITE
                    nativePaint.textSize = textSize
                    nativePaint.isFakeBoldText = true
                    val kindChar = when (layout.node.kind?.lowercase()) {
                        "story" -> "S"
                        "task" -> "T"
                        else -> "•"
                    }
                    canvas.nativeCanvas.drawText(
                        kindChar,
                        center.x,
                        center.y + textSize * 0.38f,
                        nativePaint,
                    )

                    // Name below node
                    nativePaint.color = onSurface.run {
                        android.graphics.Color.argb(
                            (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
                        )
                    }
                    nativePaint.textSize = labelSize
                    nativePaint.isFakeBoldText = false
                    val label = (layout.node.name ?: layout.node.id).let {
                        if (it.length > 14) it.take(13) + "…" else it
                    }
                    canvas.nativeCanvas.drawText(
                        label,
                        center.x,
                        center.y + nodeRadiusPx + labelSize + 2f,
                        nativePaint,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, nodeRadius: Float, color: Color) {
    val angle = atan2(to.y - from.y, to.x - from.x)
    val startX = from.x + nodeRadius * cos(angle)
    val startY = from.y + nodeRadius * sin(angle)
    val endX = to.x - nodeRadius * cos(angle)
    val endY = to.y - nodeRadius * sin(angle)

    drawLine(color = color, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 2f)

    // Arrowhead
    val arrowLen = 10f
    val arrowAngle = 0.4f
    val path = Path().apply {
        moveTo(endX, endY)
        lineTo(
            endX - arrowLen * cos(angle - arrowAngle),
            endY - arrowLen * sin(angle - arrowAngle),
        )
        moveTo(endX, endY)
        lineTo(
            endX - arrowLen * cos(angle + arrowAngle),
            endY - arrowLen * sin(angle + arrowAngle),
        )
    }
    drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
}
