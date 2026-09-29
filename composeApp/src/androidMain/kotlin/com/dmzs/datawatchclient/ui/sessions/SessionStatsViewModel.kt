package com.dmzs.datawatchclient.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.transport.dto.ComputeNodeDetailDto
import com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto
import com.dmzs.datawatchclient.ui.common.ProfileResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SPARKLINE_SIZE = 60
private const val POLL_MS = 5_000L

public class SessionStatsViewModel(
    private val sessionId: String,
    private val resolver: ProfileResolver = ProfileResolver.Default,
) : ViewModel() {
    public data class UiState(
        val cpuSamples: List<Float> = emptyList(),
        val rssSamples: List<Float> = emptyList(),
        val gpuUtilSamples: List<Float> = emptyList(),
        val gpuTempSamples: List<Float> = emptyList(),
        val ollamaCpuSamples: List<Float> = emptyList(),
        val envelope: StatEnvelopeDto? = null,
        val computeNodeDetail: ComputeNodeDetailDto? = null,
    )

    private val cpuBuf = ArrayDeque<Float>(SPARKLINE_SIZE)
    private val rssBuf = ArrayDeque<Float>(SPARKLINE_SIZE)
    private val gpuUtilBuf = ArrayDeque<Float>(SPARKLINE_SIZE)
    private val gpuTempBuf = ArrayDeque<Float>(SPARKLINE_SIZE)
    private val ollamaCpuBuf = ArrayDeque<Float>(SPARKLINE_SIZE)
    @Volatile private var computeNodeRef: String? = null
    @Volatile private var backendFamily: String? = null
    @Volatile private var computeNodeRefResolved = false

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    public fun updateComputeNodeRef(ref: String?, backendFamily: String? = null) {
        computeNodeRef = ref
        if (backendFamily != null) this.backendFamily = backendFamily
        // Only mark resolved when the caller has a real ref. The LaunchedEffect in
        // SessionStatsPanel fires with null on first composition (session not yet
        // loaded), and setting computeNodeRefResolved=true there would skip the
        // auto-resolve-from-session-list path on the first poll.
        if (ref != null) computeNodeRefResolved = true
    }

    public fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob =
            viewModelScope.launch {
                while (isActive) {
                    fetchEnvelopes()
                    delay(POLL_MS)
                }
            }
    }

    public fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun fetchEnvelopes() {
        val (_, transport) = resolver.resolve() ?: return
        // Auto-resolve computeNodeRef from session list on first poll if not provided externally
        if (!computeNodeRefResolved) {
            transport.listSessions().getOrNull()?.firstOrNull { it.id == sessionId }?.let { session ->
                computeNodeRef = session.computeNodeRef
                backendFamily = session.backend
            }
            computeNodeRefResolved = true
        }

        // Fetch compute node detail independently — matches PWA parallel-fetch approach.
        // Must run regardless of whether a matching envelope is found.
        val detail = computeNodeRef?.let { transport.getComputeNodeDetail(it).getOrNull() }
        if (detail != null) {
            detail.gpu.firstOrNull()?.let { gpu ->
                push(gpuUtilBuf, gpu.utilPct.toFloat())
                push(gpuTempBuf, gpu.tempC.toFloat())
            }
            detail.ollamaStats?.let { ollama ->
                push(ollamaCpuBuf, ollama.cpuPct.toFloat())
            }
        }

        // Use getAllEnvelopes (no session_id filter) — the server groups envelopes by
        // backend/container, never by individual session. Match the PWA's two-step
        // approach: session-kind first (exact id), then backend-kind by backend_family.
        transport.getAllEnvelopes().onSuccess { envelopes ->
            val bf = backendFamily
            val env =
                envelopes.firstOrNull {
                    it.kind == "session" &&
                        (it.sessionId == sessionId || it.id == "session:$sessionId")
                } ?: if (bf != null) {
                        envelopes.firstOrNull {
                            it.kind == "backend" && (it.id == "backend:$bf" || it.id == "backend:$bf-docker")
                        }
                    } else {
                        null
                    }
            if (env != null) {
                push(cpuBuf, env.cpuPct.toFloat())
                push(rssBuf, env.rssBytes.toFloat())
            }
            _state.value =
                UiState(
                    cpuSamples = cpuBuf.toList(),
                    rssSamples = rssBuf.toList(),
                    gpuUtilSamples = gpuUtilBuf.toList(),
                    gpuTempSamples = gpuTempBuf.toList(),
                    ollamaCpuSamples = ollamaCpuBuf.toList(),
                    envelope = env,
                    computeNodeDetail = detail,
                )
        }.onFailure {
            // Envelopes unavailable — still surface compute node detail.
            if (detail != null) {
                _state.value = _state.value.copy(
                    gpuUtilSamples = gpuUtilBuf.toList(),
                    gpuTempSamples = gpuTempBuf.toList(),
                    ollamaCpuSamples = ollamaCpuBuf.toList(),
                    computeNodeDetail = detail,
                )
            }
        }
    }

    private fun push(
        buf: ArrayDeque<Float>,
        value: Float,
    ) {
        if (buf.size >= SPARKLINE_SIZE) buf.removeFirst()
        buf.addLast(value)
    }
}
