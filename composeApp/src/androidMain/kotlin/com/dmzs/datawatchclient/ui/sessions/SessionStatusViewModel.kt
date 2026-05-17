package com.dmzs.datawatchclient.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.transport.dto.SessionStatusBoardDto
import com.dmzs.datawatchclient.transport.dto.SessionTelemetryDto
import com.dmzs.datawatchclient.ui.common.ProfileResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

public class SessionStatusViewModel(
    private val sessionId: String,
    private val resolver: ProfileResolver = ProfileResolver.Default,
) : ViewModel() {

    public data class UiState(
        val board: SessionStatusBoardDto? = null,
        val telemetry: SessionTelemetryDto? = null,
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    public fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                fetchStatus()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    public fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    public fun refreshStatus() {
        viewModelScope.launch { fetchStatus() }
    }

    private suspend fun fetchStatus() {
        val (_, transport) = resolver.resolve() ?: return
        _state.value = _state.value.copy(loading = _state.value.board == null)
        transport.getSessionStatus(sessionId).fold(
            onSuccess = { board ->
                val telemetry = transport.getSessionTelemetry(sessionId).getOrNull()
                _state.value = UiState(board = board, telemetry = telemetry, loading = false, error = null)
            },
            onFailure = { err ->
                _state.value = _state.value.copy(loading = false, error = err.message)
            },
        )
    }

    public companion object {
        public const val POLL_INTERVAL_MS: Long = 5_000L
    }
}
