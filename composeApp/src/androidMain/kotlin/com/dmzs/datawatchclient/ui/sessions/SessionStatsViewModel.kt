package com.dmzs.datawatchclient.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        val envelope: StatEnvelopeDto? = null,
    )

    private val cpuBuf = ArrayDeque<Float>(SPARKLINE_SIZE)
    private val rssBuf = ArrayDeque<Float>(SPARKLINE_SIZE)

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    public fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
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
        transport.getSessionEnvelopes(sessionId).onSuccess { envelopes ->
            val env = envelopes.firstOrNull { it.kind == "session" }
                ?: envelopes.firstOrNull()
                ?: return
            push(cpuBuf, env.cpuPct.toFloat())
            push(rssBuf, env.rssBytes.toFloat())
            _state.value = UiState(
                cpuSamples = cpuBuf.toList(),
                rssSamples = rssBuf.toList(),
                envelope = env,
            )
        }
    }

    private fun push(buf: ArrayDeque<Float>, value: Float) {
        if (buf.size >= SPARKLINE_SIZE) buf.removeFirst()
        buf.addLast(value)
    }
}
