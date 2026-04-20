package com.dmzs.datawatchclient.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.TransportError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Edits an existing [ServerProfile]. On save, runs the same two-step probe as
 * Add (health + listSessions) before persisting the change, so a typo in the
 * URL or a revoked token surfaces before the user re-enters the Sessions tab.
 *
 * Token semantics: if the user clears the token field (and does not check
 * "no token"), we treat it as "keep existing token" to avoid surprising secret
 * deletion. Only an explicit "no token" opt-in or a new non-empty value
 * modifies the vault.
 */
public class EditServerViewModel(private val profileId: String) : ViewModel() {
    public data class UiState(
        val loading: Boolean = true,
        val displayName: String = "",
        val baseUrl: String = "",
        val tokenPlaceholder: String = "",
        val newToken: String = "",
        val noToken: Boolean = false,
        val selfSigned: Boolean = false,
        val probing: Boolean = false,
        val deleting: Boolean = false,
        val error: String? = null,
        val canSubmit: Boolean = false,
        val saved: Boolean = false,
        val deleted: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state.asStateFlow()

    private var original: ServerProfile? = null

    init {
        viewModelScope.launch {
            val profiles = ServiceLocator.profileRepository.observeAll().first()
            val p =
                profiles.firstOrNull { it.id == profileId } ?: run {
                    _state.update { it.copy(loading = false, error = "Profile not found.") }
                    return@launch
                }
            original = p
            _state.update {
                it.copy(
                    loading = false,
                    displayName = p.displayName,
                    baseUrl = p.baseUrl,
                    tokenPlaceholder = if (p.bearerTokenRef.isBlank()) "" else "••••••••",
                    noToken = p.bearerTokenRef.isBlank(),
                    selfSigned = p.trustAnchorSha256 == ServiceLocator.TRUST_ALL_SENTINEL,
                ).recompute()
            }
        }
    }

    public fun onDisplayName(v: String): Unit = _state.update { it.copy(displayName = v).recompute() }

    public fun onBaseUrl(v: String): Unit = _state.update { it.copy(baseUrl = v).recompute() }

    public fun onNewToken(v: String): Unit = _state.update { it.copy(newToken = v).recompute() }

    public fun onNoToken(v: Boolean): Unit =
        _state.update {
            it.copy(noToken = v, newToken = if (v) "" else it.newToken).recompute()
        }

    public fun onSelfSigned(v: Boolean): Unit = _state.update { it.copy(selfSigned = v).recompute() }

    public fun save() {
        val snapshot = _state.value
        val orig = original ?: return
        if (!snapshot.canSubmit || snapshot.probing) return
        _state.update { it.copy(probing = true, error = null) }

        viewModelScope.launch {
            // Resolve the token we'll use for the probe — either the new value,
            // the cleared state, or the existing vault value.
            val newAlias: String
            val newTokenValue: String?
            when {
                snapshot.noToken -> {
                    newAlias = ""
                    newTokenValue = null
                }
                snapshot.newToken.isNotBlank() -> {
                    newAlias =
                        orig.bearerTokenRef.ifBlank {
                            com.dmzs.datawatchclient.security.TokenVault.aliasFor(orig.id)
                        }
                    newTokenValue = snapshot.newToken
                }
                else -> {
                    newAlias = orig.bearerTokenRef
                    newTokenValue = null // keep existing vault entry
                }
            }

            // Preview-persist the token in the vault before the probe so that
            // transportFor's tokenProvider reads the right value. We roll back
            // on probe failure.
            val prevTokenBackup =
                newTokenValue?.let { _ ->
                    ServiceLocator.tokenVault.get(newAlias)
                }
            if (newTokenValue != null) {
                ServiceLocator.tokenVault.put(orig.id, newTokenValue)
            } else if (snapshot.noToken && orig.bearerTokenRef.isNotBlank()) {
                ServiceLocator.tokenVault.remove(orig.bearerTokenRef)
            }

            val updated =
                orig.copy(
                    displayName = snapshot.displayName.trim(),
                    baseUrl = snapshot.baseUrl.trim().trimEnd('/'),
                    bearerTokenRef = newAlias,
                    trustAnchorSha256 = if (snapshot.selfSigned) ServiceLocator.TRUST_ALL_SENTINEL else null,
                )

            val transport = ServiceLocator.transportFor(updated)
            val probe =
                transport.ping().fold(
                    onSuccess = { transport.listSessions().map { } },
                    onFailure = { Result.failure(it) },
                )
            probe.fold(
                onSuccess = {
                    ServiceLocator.profileRepository.upsert(updated)
                    _state.update { it.copy(probing = false, saved = true) }
                },
                onFailure = { err ->
                    // Roll back vault mutations so we don't leave a half-applied change.
                    if (newTokenValue != null) {
                        if (prevTokenBackup != null) {
                            ServiceLocator.tokenVault.put(orig.id, prevTokenBackup)
                        } else if (orig.bearerTokenRef.isBlank()) {
                            ServiceLocator.tokenVault.remove(newAlias)
                        }
                    }
                    val msg = describe(err, snapshot.noToken)
                    _state.update { it.copy(probing = false, error = msg) }
                },
            )
        }
    }

    public fun delete() {
        val orig = original ?: return
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            if (orig.bearerTokenRef.isNotBlank()) {
                ServiceLocator.tokenVault.remove(orig.bearerTokenRef)
            }
            ServiceLocator.profileRepository.delete(orig.id)
            val active = ServiceLocator.activeServerStore.get()
            if (active == orig.id) ServiceLocator.activeServerStore.set(null)
            _state.update { it.copy(deleting = false, deleted = true) }
        }
    }

    private fun describe(
        err: Throwable,
        noToken: Boolean,
    ): String =
        when (err) {
            is TransportError.Unauthorized ->
                if (noToken) {
                    "Server requires a bearer token — uncheck \"No bearer token\"."
                } else {
                    "Token rejected by server."
                }
            is TransportError.Unreachable -> "Server not reachable. Check URL, Tailscale, or VPN."
            is TransportError.TrustFailure -> "Certificate not trusted. Import your self-signed CA first."
            is TransportError -> err.message ?: "Probe failed."
            else -> "Probe failed: ${err.message ?: err::class.simpleName}"
        }

    private fun UiState.recompute(): UiState =
        copy(
            canSubmit =
                displayName.isNotBlank() &&
                    baseUrl.trim().let { it.startsWith("http://") || it.startsWith("https://") },
        )
}
