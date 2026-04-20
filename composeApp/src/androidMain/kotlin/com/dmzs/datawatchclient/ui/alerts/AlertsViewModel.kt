package com.dmzs.datawatchclient.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the Alerts tab list and the bottom-nav alert badge. Sources sessions
 * for the active profile, filters to `needsInput && !muted`, and exposes the
 * count plus the projection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class AlertsViewModel : ViewModel() {
    public data class UiState(
        val alerts: List<Session> = emptyList(),
        val count: Int = 0,
    )

    private val activeProfileFlow =
        ServiceLocator.profileRepository.observeAll()
            .map { profiles -> profiles.firstOrNull { it.enabled } }

    public val state: StateFlow<UiState> =
        activeProfileFlow
            .flatMapLatest { profile ->
                if (profile == null) {
                    flowOf(emptyList())
                } else {
                    ServiceLocator.sessionRepository.observeForProfile(profile.id)
                }
            }
            .map { sessions ->
                val alerts = sessions.filter { it.needsInput && !it.muted }
                UiState(alerts = alerts, count = alerts.size)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    /**
     * Dismisses an alert by muting the underlying session — the
     * `needsInput && !muted` projection immediately drops the row, and the
     * bottom-nav badge re-counts. The session itself is preserved; the user
     * can unmute from Sessions-tab swipe-to-mute. v0.12+ may migrate to the
     * parent's `POST /api/alerts` (`markAlertRead`) once the /api/alerts
     * wire shape is fully confirmed — the transport method already exists.
     */
    public fun dismiss(sessionId: String) {
        viewModelScope.launch {
            ServiceLocator.sessionRepository.setMuted(sessionId, muted = true)
        }
    }
}
