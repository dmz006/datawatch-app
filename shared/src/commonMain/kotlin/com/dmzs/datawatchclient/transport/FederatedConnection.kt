package com.dmzs.datawatchclient.transport

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a switch to a proxied remote stands (PWA `state._fedConnStatus.phase`). */
public enum class FedConnPhase {
    /** The authenticated probe (`GET /api/proxy/<name>/api/sessions`) is in flight. */
    CONNECTING,

    /** The probe answered; its session list is being stored for the UI. */
    LOADING_SESSIONS,

    /** The probe failed; [FedConnStatus.reason] / [FedConnStatus.authFailed] say why. */
    ERROR,
}

/**
 * Connection status shown while the active server is a proxied remote and no
 * real data has arrived yet (#235/#236.2, PWA v8.73.2–v8.73.3).
 *
 * @property serverName the remote's own name/label (PWA `state.activeServer`).
 * @property parentId / [parentName] the server it is reached through — the
 *   "Back to <parent>" target.
 * @property authFailed the remote (or the parent) answered 401/403: the UI shows
 *   the localized "Authentication failed…" text instead of [reason].
 */
public data class FedConnStatus(
    val profileId: String,
    val serverName: String,
    val parentId: String,
    val parentName: String,
    val phase: FedConnPhase,
    val reason: String? = null,
    val authFailed: Boolean = false,
)

/**
 * Drives [FedConnStatus] from real results only — never a canned sequence
 * (PWA `_checkFederatedConnection`). When the active profile becomes a
 * proxied remote it probes the remote's **authenticated** sessions endpoint
 * (not the public `/api/health`, which reports "connected" even with a bad
 * token, #236.3):
 *
 *  - in flight → [FedConnPhase.CONNECTING]
 *  - 2xx → [FedConnPhase.LOADING_SESSIONS] while [onSessions] stores the list,
 *    then the status clears (`null`) and the tab shows its normal content
 *  - failure → [FedConnPhase.ERROR]; the probe repeats with capped backoff
 *    (5 s … 60 s) and the error stays up until a probe succeeds, real data
 *    arrives ([reportData]) or the user switches server
 *
 * A real (non-proxied) profile, "All servers" (`null`) or a switch away
 * clears the status immediately. Re-sending the same profile id (e.g. its
 * display name resolved after a cold start) does not restart the probe.
 */
public class FederatedConnectionMonitor(
    scope: CoroutineScope,
    private val probe: suspend (ServerProfile) -> Result<List<Session>>,
    private val onSessions: suspend (ServerProfile, List<Session>) -> Unit = { _, _ -> },
    private val retryDelayMs: (Int) -> Long = { ProxiedServers.backoffMs(it, baseMs = 5_000L) },
) {
    private val active = MutableStateFlow<ServerProfile?>(null)
    private val _status = MutableStateFlow<FedConnStatus?>(null)

    /** Current status, or null when there is nothing to show. */
    public val status: StateFlow<FedConnStatus?> = _status.asStateFlow()

    init {
        scope.launch {
            active
                .distinctUntilChangedBy { it?.id }
                .collectLatest { run(it) }
        }
    }

    /** Feed the resolved active profile (null in "All servers" mode). */
    public fun onActiveProfile(profile: ServerProfile?) {
        active.value = profile
    }

    /**
     * Real authenticated data for [profileId] reached the UI some other way
     * (WebSocket `sessions` push, a tab's own REST refresh): the status is
     * stale whatever its phase (PWA clears `_fedConnStatus` on every `sessions`
     * frame).
     */
    public fun reportData(profileId: String) {
        _status.update { if (it?.profileId == profileId) null else it }
    }

    private suspend fun run(profile: ServerProfile?) {
        if (profile == null || !ProxiedServers.isProxied(profile.id)) {
            _status.value = null
            return
        }
        val base =
            FedConnStatus(
                profileId = profile.id,
                serverName = remoteLabel(profile),
                parentId = ProxiedServers.parentIdOf(profile.id),
                parentName = profile.displayName.substringBefore(ProxiedServers.NAME_JOINER),
                phase = FedConnPhase.CONNECTING,
            )
        _status.value = base
        var failures = 0
        while (true) {
            val result = probe(profile)
            if (result.isSuccess) {
                // Cleared meanwhile by reportData → keep it cleared.
                if (_status.value == null) return
                _status.value = base.copy(phase = FedConnPhase.LOADING_SESSIONS)
                runCatching { onSessions(profile, result.getOrThrow()) }
                _status.update { if (it?.profileId == profile.id) null else it }
                return
            }
            failures++
            val err = result.exceptionOrNull()
            _status.value =
                base.copy(
                    phase = FedConnPhase.ERROR,
                    reason = reasonOf(err),
                    authFailed = isAuthFailure(err),
                )
            delay(retryDelayMs(failures))
            // Real data cleared the error while we waited — nothing left to report.
            if (_status.value == null) return
        }
    }

    public companion object {
        /** 401/403 from the remote (or its parent): no valid token configured. */
        public fun isAuthFailure(e: Throwable?): Boolean = e is TransportError.Unauthorized

        /**
         * Short reason for the error body (`fed_conn_error_body` %2$s): the
         * proxy's own text for a 502 ("proxy error: dial tcp …"), the network
         * cause for an unreachable parent, else the transport message.
         */
        public fun reasonOf(e: Throwable?): String =
            when (e) {
                null -> "unreachable"
                is TransportError.Unreachable ->
                    e.cause?.message?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                        ?: (e.message ?: "unreachable")
                is TransportError.ServerError ->
                    ErrorText.of(e, if (e.status > 0) "HTTP ${e.status}" else "unreachable")
                else -> ErrorText.of(e, "unreachable")
            }

        /** The remote's own name/label — the part after "parent › ". */
        public fun remoteLabel(profile: ServerProfile): String =
            if (profile.displayName.contains(ProxiedServers.NAME_JOINER)) {
                profile.displayName.substringAfter(ProxiedServers.NAME_JOINER)
            } else {
                ProxiedServers.remoteNameOf(profile.id) ?: profile.displayName
            }
    }
}
