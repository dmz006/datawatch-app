package com.dmzs.datawatchclient.ui.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.SavedCommand
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
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
 * Drives the Settings → Saved commands card plus the
 * "From library" dropdown on NewSessionScreen. Reads + writes to
 * /api/commands on the active server profile. Shared VM class — each
 * call site gets its own instance via `viewModel()` at composition scope,
 * which keeps selection state (NewSessionScreen) independent of list
 * state (Settings). Both talk to the same server endpoint.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class SavedCommandsViewModel : ViewModel() {
    public data class UiState(
        val commands: List<SavedCommand> = emptyList(),
        val refreshing: Boolean = false,
        val banner: String? = null,
        val serverName: String? = null,
        /** Flips false only against a pre-v4.0.3 server. */
        val supported: Boolean = true,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        ServiceLocator.activeProfileFlow()
            .flatMapLatest { profile ->
                if (profile == null) {
                    flowOf(null)
                } else {
                    ServiceLocator.transportFor(profile).isReachable
                        .map { reachable -> if (reachable) profile else null }
                }
            }
            .filterNotNull()
            .distinctUntilChanged { a, b -> a.id == b.id }
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    public fun refresh() {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            _state.value = _state.value.copy(refreshing = true, serverName = profile.displayName)
            ServiceLocator.transportFor(profile).listCommands().fold(
                onSuccess = { list ->
                    _state.value =
                        _state.value.copy(
                            commands = list.sortedBy { it.name.lowercase() },
                            refreshing = false,
                            banner = null,
                            supported = true,
                        )
                },
                onFailure = { err ->
                    if (err is TransportError.NotFound) {
                        _state.value =
                            _state.value.copy(
                                refreshing = false,
                                supported = false,
                                banner =
                                    "This server doesn't expose /api/commands. Upgrade datawatch to v4.0.3+.",
                            )
                    } else {
                        _state.value =
                            _state.value.copy(
                                refreshing = false,
                                banner =
                                    "Couldn't load saved commands — ${err.message ?: err::class.simpleName}",
                            )
                    }
                },
            )
        }
    }

    public fun save(
        name: String,
        command: String,
    ) {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            ServiceLocator.transportFor(profile).saveCommand(name, command).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Save failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun delete(name: String) {
        viewModelScope.launch {
            val profile = resolveActiveProfile() ?: return@launch
            ServiceLocator.transportFor(profile).deleteCommand(name).fold(
                onSuccess = { refresh() },
                onFailure = { err ->
                    _state.value =
                        _state.value.copy(
                            banner = "Delete failed — ${err.message ?: err::class.simpleName}",
                        )
                },
            )
        }
    }

    public fun dismissBanner() {
        _state.value = _state.value.copy(banner = null)
    }

    private suspend fun resolveActiveProfile(): ServerProfile? {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            }
                ?: profiles.firstOrNull { it.enabled }
        if (profile == null) {
            _state.value = UiState(banner = "No enabled server.")
        }
        return profile
    }
}
