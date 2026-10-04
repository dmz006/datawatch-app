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
        /** Parity D91a — pinned leaf SHA-256 (lowercase hex), null when unpinned. */
        val pinnedSha: String? = null,
        val pinning: Boolean = false,
        val pinCandidate: com.dmzs.datawatchclient.transport.CertFingerprint? = null,
        val pinError: String? = null,
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

    public fun onNoToken(v: Boolean): Unit =
        _state.update {
            // When the user opts into no-auth we also clear whatever's in the token
            // field so it can't accidentally get sent on a later edit.
            it.copy(noToken = v, token = if (v) "" else it.token).recompute()
        }

    public fun onSelfSigned(v: Boolean): Unit =
        _state.update { it.copy(selfSigned = v, pinnedSha = if (v) null else it.pinnedSha).recompute() }

    /** Parity D91a — fetch the server's leaf certificate for the user to review. */
    public fun probePin() {
        val url = _state.value.baseUrl.trim()
        _state.update { it.copy(pinning = true, pinError = null) }
        viewModelScope.launch {
            com.dmzs.datawatchclient.transport.probeServerCertificate(url).fold(
                onSuccess = { fp -> _state.update { it.copy(pinning = false, pinCandidate = fp) } },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            pinning = false,
                            pinError = err.message ?: "Could not reach the server or it presented no certificate.",
                        )
                    }
                },
            )
        }
    }

    public fun confirmPin(): Unit =
        _state.update { s ->
            val fp = s.pinCandidate ?: return@update s
            s.copy(pinnedSha = fp.sha256Hex, selfSigned = false, pinCandidate = null).recompute()
        }

    public fun cancelPin(): Unit = _state.update { it.copy(pinCandidate = null) }

    public fun removePin(): Unit = _state.update { it.copy(pinnedSha = null).recompute() }

    @OptIn(ExperimentalUuidApi::class)
    public fun submit() {
        val snapshot = _state.value
        if (!snapshot.canSubmit || snapshot.probing) return
        _state.update { it.copy(probing = true, error = null) }
        viewModelScope.launch {
            val profileId = "srv-${Uuid.random().toString().take(8)}"
            // Empty string sentinel in bearerTokenRef = "no token" path. Keeps
            // the existing NOT NULL schema unchanged (ADR-0016 frozen in v0.2).
            val alias: String =
                if (snapshot.noToken) {
                    ""
                } else {
                    ServiceLocator.tokenVault.put(profileId, snapshot.token)
                }
            val profile =
                ServerProfile(
                    id = profileId,
                    displayName = snapshot.displayName.trim(),
                    baseUrl = snapshot.baseUrl.trim().trimEnd('/'),
                    bearerTokenRef = alias,
                    // Trust-all switch → ServiceLocator picks the trust-all Ktor client
                    // (no TLS identity checks for this server). A pinned SHA-256 →
                    // a client that accepts exactly that leaf cert (D91a).
                    trustAnchorSha256 =
                        if (snapshot.selfSigned) {
                            ServiceLocator.TRUST_ALL_SENTINEL
                        } else {
                            snapshot.pinnedSha
                        },
                    reachabilityProfileId = "lan-default",
                    enabled = true,
                    createdTs = Clock.System.now().toEpochMilliseconds(),
                )

            val transport = ServiceLocator.transportFor(profile)
            // Two-step probe: health confirms reachability + TLS; then listSessions
            // confirms the datawatch REST API shapes match what we deserialize —
            // prevents "added successfully but sessions tab fails" class of bugs.
            val probe =
                transport.ping().fold(
                    onSuccess = { transport.listSessions().map { Unit } },
                    onFailure = { Result.failure(it) },
                )
            probe.fold(
                onSuccess = {
                    ServiceLocator.profileRepository.upsert(profile)
                    _state.update { it.copy(probing = false, added = true) }
                },
                onFailure = { err ->
                    // Probe failed — roll back the vault write (if any) so we don't
                    // leave a token behind when the profile was never persisted.
                    if (alias.isNotBlank()) ServiceLocator.tokenVault.remove(alias)
                    val msg =
                        when (err) {
                            is TransportError.Unauthorized ->
                                if (snapshot.noToken) {
                                    "Server requires a bearer token — uncheck \"No bearer token\"."
                                } else {
                                    "Token rejected by server."
                                }
                            is TransportError.Unreachable -> "Server not reachable. Check URL, Tailscale, or VPN."
                            is TransportError.TrustFailure ->
                                if (snapshot.pinnedSha != null) {
                                    "Certificate doesn't match the pin, or doesn't name this host."
                                } else {
                                    "Certificate not trusted. Pin the server certificate, or import your CA."
                                }
                            is TransportError -> err.message ?: "Probe failed."
                            else -> "Probe failed: ${err.message ?: err::class.simpleName}"
                        }
                    _state.update { it.copy(probing = false, error = msg) }
                },
            )
        }
    }

    private fun UiState.recompute(): UiState =
        copy(
            canSubmit =
                displayName.isNotBlank() &&
                    baseUrl.trim().let { it.startsWith("http://") || it.startsWith("https://") } &&
                    (noToken || token.isNotBlank()),
        )
}
