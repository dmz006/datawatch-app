package com.dmzs.datawatchclient.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.dto.DashboardCardDto
import com.dmzs.datawatchclient.transport.dto.StatsDto
import com.dmzs.datawatchclient.transport.TransportClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

public data class DashboardState(
    val cards: List<DashboardCardDto> = emptyList(),
    val cardsLoaded: Boolean = false,
    val sessions: List<Session> = emptyList(),
    val stats: StatsDto? = null,
    val error: String? = null,
)

public class DashboardViewModel : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    init {
        viewModelScope.launch { start() }
    }

    private suspend fun resolveTransport(): TransportClient? {
        val activeId = ServiceLocator.activeServerStore.get()
        return runCatching {
            ServiceLocator.profileRepository.observeAll()
                .first { list -> list.any { it.enabled } }
                .let { list ->
                    if (activeId == null) list.filter { it.enabled }.firstOrNull()
                    else list.firstOrNull { it.id == activeId && it.enabled }
                }
                ?.let { ServiceLocator.transportFor(it) }
        }.getOrNull()
    }

    private suspend fun start() {
        val transport = resolveTransport()
        transport?.listDashboardCards()?.onSuccess { cards ->
            _state.value = _state.value.copy(cards = cards)
        }
        _state.value = _state.value.copy(cardsLoaded = true)

        while (currentCoroutineContext().isActive) {
            val t = resolveTransport()
            if (t != null) {
                t.listSessions()
                    .onSuccess { sessions -> _state.value = _state.value.copy(sessions = sessions, error = null) }
                    .onFailure { e -> _state.value = _state.value.copy(error = e.message ?: "Error") }
                t.stats().onSuccess { stats -> _state.value = _state.value.copy(stats = stats) }
            }
            delay(POLL_MS)
        }
    }

    private companion object {
        const val POLL_MS = 10_000L
    }
}
