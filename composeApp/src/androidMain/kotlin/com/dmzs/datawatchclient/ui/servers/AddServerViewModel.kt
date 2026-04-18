package com.dmzs.datawatchclient.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Handles the "add server" flow: form state, health-check probe, persist on success.
 * Lives inside `:composeApp` (not `:shared`) because it depends on Android-only
 * [ServiceLocator]; the ViewModel wrapper is Android AAC ViewModel.
 */
public class AddServerViewModel : ViewModel() {

    public data class UiState(
        val displayName: String = "",
        val baseUrl: String = "",
        val token: String = "",
        val noToken: Boolean = false,
        val selfSigned: Boolean = false,
        val probing: Boolean = false,
        val error: String? = null,
        val canSubmit: Boolean = false,
        val added: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    public fun onDisplayName(v: String): Unit = _state.update { it.copy(displayName = v).recompute() }
    public fun onBaseUrl(v: String): Unit = _state.update { it.copy(baseUrl = v).recompute() }
    public fun onToken(v: String): Unit = _state.update { it.copy(token = v).recompute() }
    public fun onNoToken(v: Boolean): Unit = _state.update {
        // When the user opts into no-auth we also clear whatever's in the token
        // field so it can't accidentally get sent on a later edit.
        it.copy(noToken = v, token = if (v) "" else it.token).recompute()
    }
    public fun onSelfSigned(v: Boolean): Unit = _state.update { it.copy(selfSigned = v).recompute() }

    @OptIn(ExperimentalUuidApi::class)
    public fun submit() {
        val snapshot = _state.value
        if (!snapshot.canSubmit || snapshot.probing) return
        _state.update { it.copy(probing = true, error = null) }
        viewModelScope.launch {
            val profileId = "srv-${Uuid.random().toString().take(8)}"
            // Empty string sentinel in bearerTokenRef = "no token" path. Keeps
            // the existing NOT NULL schema unchanged (ADR-0016 frozen in v0.2).
            val alias: String = if (snapshot.noToken) {
                ""
            } else {
                ServiceLocator.tokenVault.put(profileId, snapshot.token)
            }
            val profile = ServerProfile(
                id = profileId,
                displayName = snapshot.displayName.trim(),
                baseUrl = snapshot.baseUrl.trim().trimEnd('/'),
                bearerTokenRef = alias,
                trustAnchorSha256 = null,
                reachabilityProfileId = "lan-default",
                enabled = true,
                createdTs = Clock.System.now().toEpochMilliseconds(),
            )

            val transport = ServiceLocator.transportFor(profile)
            val probe = transport.ping()
            probe.fold(
                onSuccess = {
                    ServiceLocator.profileRepository.upsert(profile)
                    _state.update { it.copy(probing = false, added = true) }
                },
                onFailure = { err ->
                    // Probe failed — roll back the vault write (if any) so we don't
                    // leave a token behind when the profile was never persisted.
                    if (alias.isNotBlank()) ServiceLocator.tokenVault.remove(alias)
                    val msg = when (err) {
                        is TransportError.Unauthorized ->
                            if (snapshot.noToken) {
                                "Server requires a bearer token — uncheck \"No bearer token\"."
                            } else {
                                "Token rejected by server."
                            }
                        is TransportError.Unreachable -> "Server not reachable. Check URL, Tailscale, or VPN."
                        is TransportError.TrustFailure -> "Certificate not trusted. Import your self-signed CA first."
                        is TransportError -> err.message ?: "Probe failed."
                        else -> "Probe failed: ${err.message ?: err::class.simpleName}"
                    }
                    _state.update { it.copy(probing = false, error = msg) }
                },
            )
        }
    }

    private fun UiState.recompute(): UiState = copy(
        canSubmit = displayName.isNotBlank() &&
            baseUrl.trim().let { it.startsWith("http://") || it.startsWith("https://") } &&
            (noToken || token.isNotBlank()),
    )
}
