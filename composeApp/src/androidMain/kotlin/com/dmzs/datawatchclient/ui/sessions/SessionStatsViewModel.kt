package com.dmzs.datawatchclient.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.transport.ws.StatsHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val SPARKLINE_SIZE = 60

public class SessionStatsViewModel(private val sessionId: String) : ViewModel() {

    public data class UiState(
        val cpuSamples: List<Float> = emptyList(),
        val rssSamples: List<Float> = emptyList(),
    )

    private val cpuBuf = ArrayDeque<Float>(SPARKLINE_SIZE)
    private val rssBuf = ArrayDeque<Float>(SPARKLINE_SIZE)

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            StatsHub.flow.collect { dto ->
                val env = dto.envelopes.firstOrNull { env ->
                    env.kind == "session" && (
                        env.id == sessionId ||
                            sessionId.startsWith(env.id) ||
                            env.id.startsWith(sessionId)
                        )
                } ?: return@collect
                push(cpuBuf, env.cpuPct.toFloat())
                push(rssBuf, env.rssBytes.toFloat())
                _state.value = UiState(cpuSamples = cpuBuf.toList(), rssSamples = rssBuf.toList())
            }
        }
    }

    private fun push(buf: ArrayDeque<Float>, value: Float) {
        if (buf.size >= SPARKLINE_SIZE) buf.removeFirst()
        buf.addLast(value)
    }
}
