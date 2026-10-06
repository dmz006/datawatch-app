package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.transport.dto.ComputeNodeCpuStatDto
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto
import com.dmzs.datawatchclient.transport.dto.ComputeNodeGpuStatDto
import com.dmzs.datawatchclient.transport.dto.ComputeNodeMemStatDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.StatsDto

/** Compute node an automaton is using right now; [ref] is null for local daemon stats. */
public data class PrdComputeResolution(
    val ref: String?,
    val detail: ComputeNodeDetailDto?,
)

/**
 * Resolves the compute node shown on the Automata detail while planning or
 * running — PWA `_loadPRDActiveSessionCard` order: live session
 * `compute_node_ref` > the backend LLM's `compute_nodes[0]` > local `/api/stats`.
 *
 * While planning the backend is the one the daemon actually decomposes with
 * (`decomposition_profile`, else global `autonomous.planning_backend` —
 * manager.go Decompose); while running it is the task's / automaton's
 * execution backend.
 */
public object PrdComputeResolver {
    private val planningStates: Set<String> = setOf("planning", "decomposing")

    public fun isPlanning(prd: PrdDto): Boolean = prd.status.lowercase() in planningStates

    public suspend fun resolve(
        transport: TransportClient,
        prd: PrdDto,
        sessionRef: String?,
        taskBackend: String? = null,
    ): PrdComputeResolution {
        if (!sessionRef.isNullOrBlank()) {
            val d: ComputeNodeDetailDto? = transport.getComputeNodeDetail(sessionRef).getOrNull()
            if (d != null) return PrdComputeResolution(ref = sessionRef, detail = d)
        }
        val backend: String? =
            if (isPlanning(prd)) {
                prd.decompositionProfile?.takeIf { it.isNotBlank() }
                    ?: transport.fetchAutonomousPlanningBackend().getOrNull()
            } else {
                taskBackend?.takeIf { it.isNotBlank() } ?: prd.backend?.takeIf { it.isNotBlank() }
            }
        if (backend != null) {
            val entry = transport.listLlms().getOrNull()?.firstOrNull { it.name == backend }
            val nodes: List<String> =
                entry?.computeNodes?.filter { it.isNotBlank() }?.ifEmpty { null }
                    ?: listOfNotNull(entry?.computeNode?.takeIf { it.isNotBlank() })
            val node: String? = nodes.firstOrNull()
            if (node != null) {
                val d: ComputeNodeDetailDto? = transport.getComputeNodeDetail(node).getOrNull()
                if (d != null) return PrdComputeResolution(ref = node, detail = d)
            }
        }
        val local: ComputeNodeDetailDto? = transport.stats().getOrNull()?.let { localDetail(it) }
        return PrdComputeResolution(ref = null, detail = local)
    }

    /** PWA `fetchLocalStats` — /api/stats reshaped into the node-detail bars. */
    public fun localDetail(s: StatsDto): ComputeNodeDetailDto {
        val cores: Int = s.cpuCores ?: 0
        val load: Double = s.cpuLoad1 ?: 0.0
        val cpuPct: Double = if (cores > 0) minOf(100.0, 100.0 * load / cores) else (s.cpuPct ?: 0.0)
        val gpu: List<ComputeNodeGpuStatDto> =
            if (!s.gpuName.isNullOrBlank()) {
                listOf(
                    ComputeNodeGpuStatDto(
                        name = s.gpuName,
                        utilPct = s.gpuUtilPct ?: 0.0,
                        tempC = s.gpuTemp ?: 0.0,
                        memUsedBytes = (s.gpuMemUsedMb ?: 0L) * 1_048_576L,
                        memTotalBytes = (s.gpuMemTotalMb ?: 0L) * 1_048_576L,
                    ),
                )
            } else {
                emptyList()
            }
        return ComputeNodeDetailDto(
            cpu = ComputeNodeCpuStatDto(pct = cpuPct, cores = cores, load1 = load),
            mem = ComputeNodeMemStatDto(usedBytes = s.memUsed ?: 0L, totalBytes = s.memTotal ?: 0L),
            gpu = gpu,
            cpuPct = cpuPct,
            gpuError = s.gpuError,
        )
    }
}
