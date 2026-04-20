package com.dmzs.datawatchclient.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs ChannelsScreen — fetches /api/backends for the active server profile
 * once on init plus on user-triggered refresh. Sprint 4 will add a poll loop
 * once backend changes can be observed via WS.
 */
public class ChannelsViewModel : ViewModel() {

    public data class UiState(
        val llm: List<String> = emptyList(),
        val activeBackend: String? = null,
        val refreshing: Boolean = false,
        val banner: String? = null,
        val serverName: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    public fun refresh() {
        viewModelScope.launch {
            val profiles = ServiceLocator.profileRepository.observeAll().first()
            val activeId = ServiceLocator.activeServerStore.get()
            val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
                ?: profiles.firstOrNull { it.enabled }
                ?: run {
                    _state.value = UiState(banner = "No enabled server. Add or enable one in Settings.")
                    return@launch
                }
            _state.value = _state.value.copy(refreshing = true, serverName = profile.displayName)
            ServiceLocator.transportFor(profile).listBackends().fold(
                onSuccess = { v ->
                    _state.value = UiState(
                        llm = v.llm,
                        activeBackend = v.active,
                        refreshing = false,
                        serverName = profile.displayName,
                    )
                },
                onFailure = { err ->
                    android.util.Log.w(
                        "ChannelsVM",
                        "listBackends failed on ${profile.baseUrl}: ${err::class.simpleName}: ${err.message}",
                    )
                    _state.value = _state.value.copy(
                        refreshing = false,
                        banner = "Couldn't load backends on ${profile.displayName} — " +
                            (err.message ?: err::class.simpleName),
                    )
                },
            )
        }
    }
}
