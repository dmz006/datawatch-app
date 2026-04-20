package com.dmzs.datawatchclient.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Backs ChannelsScreen — fetches /api/backends for the active server profile
 * once on init plus on user-triggered refresh. Sprint 4 will add a poll loop
 * once backend changes can be observed via WS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class ChannelsViewModel : ViewModel() {
    public data class UiState(
        val llm: List<String> = emptyList(),
        val activeBackend: String? = null,
        val refreshing: Boolean = false,
        val banner: String? = null,
        val serverName: String? = null,
        /**
         * True while the parent server's support for `POST /api/backends/active`
         * is unconfirmed or positive. Flipped to false on a [TransportError.NotFound]
         * so the radio list greys out — users can still see the active backend
         * but cannot switch from the mobile client until the parent lands the
         * endpoint.
         */
        val setActiveSupported: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Re-fetch whenever the active profile becomes reachable. Fixes the
        // sticky "server unreachable" banner the user reported: on cold boot
        // the first probe sometimes fails before the network is up, and
        // without this observer the banner never clears until a manual tap.
        ServiceLocator.profileRepository.observeAll()
            .flatMapLatest { profiles ->
                val profile = profiles.firstOrNull { it.enabled } ?: return@flatMapLatest flowOf(null)
                ServiceLocator.transportFor(profile).isReachable
                    .map { reachable -> if (reachable) profile else null }
            }
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    /**
     * Switch the active LLM backend on the server, then refresh. The UI gates
     * on [UiState.setActiveSupported] — a NotFound from the parent flips that
     * flag off and surfaces a banner pointing at dmz006/datawatch.
     */
    public fun setActive(name: String) {
        viewModelScope.launch {
            val profiles = ServiceLocator.profileRepository.observeAll().first()
            val activeId = ServiceLocator.activeServerStore.get()
            val profile =
                profiles.firstOrNull { it.id == activeId && it.enabled }
                    ?: profiles.firstOrNull { it.enabled }
                    ?: return@launch
            ServiceLocator.transportFor(profile).setActiveBackend(name).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    if (err is TransportError.NotFound) {
                        _state.value =
                            _state.value.copy(
                                setActiveSupported = false,
                                banner =
                                    "This server doesn't expose POST /api/backends/active " +
                                        "yet. Tracked upstream at dmz006/datawatch.",
                            )
                    } else {
                        _state.value =
                            _state.value.copy(
                                banner = "Backend switch failed — ${err.message ?: err::class.simpleName}",
                            )
                    }
                },
            )
        }
    }

    public fun refresh() {
        viewModelScope.launch {
            val profiles = ServiceLocator.profileRepository.observeAll().first()
            val activeId = ServiceLocator.activeServerStore.get()
            val profile =
                profiles.firstOrNull { it.id == activeId && it.enabled }
                    ?: profiles.firstOrNull { it.enabled }
                    ?: run {
                        _state.value = UiState(banner = "No enabled server. Add or enable one in Settings.")
                        return@launch
                    }
            _state.value = _state.value.copy(refreshing = true, serverName = profile.displayName)
            ServiceLocator.transportFor(profile).listBackends().fold(
                onSuccess = { v ->
                    _state.value =
                        _state.value.copy(
                            llm = v.llm,
                            activeBackend = v.active,
                            refreshing = false,
                            serverName = profile.displayName,
                            banner = null,
                        )
                },
                onFailure = { err ->
                    android.util.Log.w(
                        "ChannelsVM",
                        "listBackends failed on ${profile.baseUrl}: ${err::class.simpleName}: ${err.message}",
                    )
                    _state.value =
                        _state.value.copy(
                            refreshing = false,
                            banner =
                                "Couldn't load backends on ${profile.displayName} — " +
                                    (err.message ?: err::class.simpleName),
                        )
                },
            )
        }
    }
}
