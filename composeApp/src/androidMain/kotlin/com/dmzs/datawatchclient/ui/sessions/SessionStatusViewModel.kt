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
        /** Blocked verdicts approved from this screen (PWA swaps the button for ✓). */
        val approvedGuardrails: Set<String> = emptySet(),
        /** Guardrail currently running via "Run guardrail". */
        val runningGuardrail: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    public fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob =
            viewModelScope.launch {
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
                _state.value =
                    _state.value.copy(board = board, telemetry = telemetry, loading = false, error = null)
            },
            onFailure = { err ->
                _state.value = _state.value.copy(loading = false, error = err.message)
            },
        )
    }

    /**
     * PWA `approveGuardrailVerdict` (GH#153) — operator-approve one blocked
     * verdict; the result goes to the alert dock.
     */
    public fun approveGuardrail(name: String) {
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            transport.approveGuardrailVerdict(sessionId, name).fold(
                onSuccess = { unblocked ->
                    _state.value = _state.value.copy(approvedGuardrails = _state.value.approvedGuardrails + name)
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(
                        if (unblocked) "$name: approved — session unblocked" else "$name: approved",
                    )
                },
                onFailure = { e ->
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(
                        "approve failed: ${e.message ?: e::class.simpleName}",
                        com.dmzs.datawatchclient.ui.shell.DockLevel.Error,
                    )
                },
            )
        }
    }

    /** PWA quick-command "Guardrails" group — run one named guardrail, then refresh. */
    public fun runGuardrail(name: String) {
        if (_state.value.runningGuardrail != null) return
        viewModelScope.launch {
            val (_, transport) = resolver.resolve() ?: return@launch
            _state.value = _state.value.copy(runningGuardrail = name)
            transport.runNamedSessionGuardrail(sessionId, name).fold(
                onSuccess = { v ->
                    val outcome = (v["outcome"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "unknown"
                    val summary = (v["summary"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(
                        "$name: $outcome" + if (summary.isNotBlank()) " — ${summary.take(60)}" else "",
                        if (outcome == "pass") {
                            com.dmzs.datawatchclient.ui.shell.DockLevel.Info
                        } else {
                            com.dmzs.datawatchclient.ui.shell.DockLevel.Error
                        },
                    )
                },
                onFailure = { e ->
                    com.dmzs.datawatchclient.ui.shell.AlertDockChannel.post(
                        "Guardrail error: ${e.message ?: e::class.simpleName}",
                        com.dmzs.datawatchclient.ui.shell.DockLevel.Error,
                    )
                },
            )
            _state.value = _state.value.copy(runningGuardrail = null)
            fetchStatus()
        }
    }

    public companion object {
        public const val POLL_INTERVAL_MS: Long = 5_000L

        /** PWA built-in guardrails offered by "Run guardrail" (app.js BL303 S3 T14). */
        public val BUILTIN_GUARDRAILS: List<String> = listOf("sast-scan", "secrets-scan", "deps-scan")
    }
}
