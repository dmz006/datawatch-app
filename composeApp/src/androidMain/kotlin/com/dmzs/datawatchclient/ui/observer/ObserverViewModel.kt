package com.dmzs.datawatchclient.ui.observer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
public class ObserverViewModel : ViewModel() {
    public data class UiState(
        val activeProfile: ServerProfile? = null,
        val allProfiles: List<ServerProfile> = emptyList(),
        val loading: Boolean = true,
    )

    private val _allProfiles = ServiceLocator.profileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeId = ServiceLocator.activeServerStore.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _computedActiveProfile: StateFlow<ServerProfile?> =
        combine(_allProfiles, _activeId) { profiles, storedId ->
            val enabled = profiles.filter { it.enabled }
            if (storedId == ActiveServerStore.SENTINEL_ALL_SERVERS) return@combine enabled.firstOrNull()
            storedId?.let { id -> enabled.firstOrNull { it.id == id } } ?: enabled.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _loading = MutableStateFlow(true)

    public val state: StateFlow<UiState> =
        combine(_allProfiles, _computedActiveProfile, _loading) { profiles, active, loading ->
            UiState(activeProfile = active, allProfiles = profiles.filter { it.enabled }, loading = loading)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            _computedActiveProfile.collectLatest {
                _loading.value = true
                delay(1_500)
                _loading.value = false
            }
        }
    }

    public val reachable: StateFlow<Boolean?> = _computedActiveProfile
        .flatMapLatest { profile ->
            if (profile == null) flowOf<Boolean?>(null)
            else ServiceLocator.transportFor(profile).isReachable.map { it as Boolean? }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    public val lastProbeEpochMs: StateFlow<Long?> = reachable
        .runningFold(null as Long?) { acc, r -> if (r == true) System.currentTimeMillis() else acc }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    public fun selectProfile(profileId: String) {
        ServiceLocator.activeServerStore.set(profileId)
    }
}
