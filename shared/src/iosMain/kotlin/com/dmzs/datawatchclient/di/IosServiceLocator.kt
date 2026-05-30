package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.db.DatawatchDb
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.push.ApnsPushStore
import com.dmzs.datawatchclient.security.IosTokenStore
import com.dmzs.datawatchclient.storage.DatabaseFactory
import com.dmzs.datawatchclient.storage.ServerProfileRepository
import com.dmzs.datawatchclient.storage.SessionRepository
import com.dmzs.datawatchclient.transport.DeviceKind
import com.dmzs.datawatchclient.transport.DevicePlatform
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.createHttpClient
import com.dmzs.datawatchclient.transport.createHttpClientWithWebSockets
import com.dmzs.datawatchclient.transport.dto.AutomataTypeDto
import com.dmzs.datawatchclient.transport.dto.AutomataTypeRequestDto
import com.dmzs.datawatchclient.transport.rest.RestTransport
import com.dmzs.datawatchclient.transport.ws.WebSocketTransport
import io.ktor.client.HttpClient
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import platform.Foundation.NSData
import platform.Foundation.NSUUID

/**
 * iOS dependency graph. Mirror of Android's ServiceLocator and AutoServiceLocator.
 *
 * Call [init] once from DatawatchClientApp.init() (SwiftUI App entry point) before
 * any member is accessed. Lazy initialization is safe here because all properties
 * are single-threaded through the main actor on iOS.
 */
public object IosServiceLocator {
    private var initialized = false

    /** Background scope for coroutine-to-callback bridge methods. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun init() {
        if (initialized) return
        initialized = true
    }

    private val _db: DatawatchDb by lazy {
        DatawatchDb(DatabaseFactory().driver())
    }

    public val tokenStore: IosTokenStore by lazy { IosTokenStore() }

    public val pushStore: ApnsPushStore by lazy { ApnsPushStore() }

    public val profileRepository: ServerProfileRepository by lazy {
        ServerProfileRepository(_db, Dispatchers.IO)
    }

    public val sessionRepository: SessionRepository by lazy {
        SessionRepository(_db, Dispatchers.IO)
    }

    private val httpClient: HttpClient by lazy { createHttpClient() }
    private val trustAllClient: HttpClient by lazy {
        createHttpClientWithWebSockets(trustAll = true)
    }
    private val wsClient: HttpClient by lazy {
        createHttpClientWithWebSockets(trustAll = false)
    }
    private val trustAllWsClient: HttpClient by lazy {
        createHttpClientWithWebSockets(trustAll = true)
    }

    public const val TRUST_ALL_SENTINEL: String = "ALLOW_ALL_INSECURE"

    /** REST transport for a given profile (reads bearer token from iOS Keychain). */
    public fun transportFor(profile: ServerProfile): TransportClient {
        val alias = profile.bearerTokenRef.takeIf { it.isNotBlank() }
        val tokenProvider: (suspend () -> String)? =
            alias?.let { { tokenStore.get(it) ?: error("Missing token for profile ${profile.id}") } }
        val client = if (profile.trustAnchorSha256 == TRUST_ALL_SENTINEL) trustAllClient else httpClient
        return RestTransport(profile = profile, client = client, tokenProvider = tokenProvider)
    }

    /** WebSocket transport for a given profile. */
    public fun wsTransportFor(profile: ServerProfile): WebSocketTransport {
        val alias = profile.bearerTokenRef.takeIf { it.isNotBlank() }
        val tokenProvider: (suspend () -> String)? =
            alias?.let { { tokenStore.get(it) ?: error("Missing token for profile ${profile.id}") } }
        val client = if (profile.trustAnchorSha256 == TRUST_ALL_SENTINEL) trustAllWsClient else wsClient
        return WebSocketTransport(profile = profile, client = client, tokenProvider = tokenProvider)
    }

    /** Store a bearer token in the iOS Keychain and return the alias for ServerProfile. */
    public fun storeToken(profileId: String, token: String): String =
        tokenStore.put(profileId, token)

    /** Remove the bearer token for a deleted profile. */
    public fun removeToken(alias: String) = tokenStore.remove(alias)

    // ── Flow accessors (use FlowAdapter.stream(from:) on Swift side) ──────

    /** Live list of server profiles, ordered by creation time. */
    public fun profilesFlow(): Flow<List<ServerProfile>> = profileRepository.observeAll()

    // ── Callback wrappers for suspend operations ──────────────────────────

    /**
     * Persist [profile] (insert or update). Resolves and stores [tokenValue]
     * in the iOS Keychain when non-null; leaves the existing token untouched
     * when null. Runs the health + listSessions probe first so callers get an
     * error before persistence if the server is unreachable.
     */
    public fun saveProfile(
        profile: ServerProfile,
        tokenValue: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            try {
                // 1. Store token if provided.
                val alias = when {
                    tokenValue != null && tokenValue.isNotBlank() -> {
                        tokenStore.put(profile.id, tokenValue)
                    }
                    profile.bearerTokenRef.isNotBlank() -> profile.bearerTokenRef
                    else -> ""
                }
                val resolved = profile.copy(bearerTokenRef = alias)
                // 2. Probe (health + sessions).
                val transport = transportFor(resolved)
                transport.ping()
                    .mapCatching { transport.listSessions().getOrThrow() }
                    .fold(
                        onSuccess = {
                            profileRepository.upsert(resolved)
                            onSuccess()
                        },
                        onFailure = { err ->
                            onError(err.message ?: "Probe failed — check URL and token.")
                        },
                    )
            } catch (e: Throwable) {
                onError(e.message ?: "Unexpected error.")
            }
        }
    }

    /** Delete a profile and its Keychain token. Deregisters the APNs device on the server (best-effort). */
    public fun deleteProfile(
        profile: ServerProfile,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            try {
                // Best-effort APNs deregistration — swallow errors so profile delete
                // always succeeds even if the server is unreachable.
                val deviceId = pushStore.deviceIdFor(profile.id)
                if (deviceId != null) {
                    runCatching { transportFor(profile).unregisterDevice(deviceId) }
                    pushStore.clearProfile(profile.id)
                }
                if (profile.bearerTokenRef.isNotBlank()) tokenStore.remove(profile.bearerTokenRef)
                profileRepository.delete(profile.id)
                onSuccess()
            } catch (e: Throwable) {
                onError(e.message ?: "Delete failed.")
            }
        }
    }

    /** Quick health probe — does NOT persist anything. */
    public fun probeProfile(
        profile: ServerProfile,
        tokenValue: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            try {
                // Temporarily store token for the probe if supplied.
                val alias = when {
                    tokenValue != null && tokenValue.isNotBlank() -> tokenStore.put(profile.id + ".probe", tokenValue)
                    else -> profile.bearerTokenRef
                }
                val testProfile = profile.copy(bearerTokenRef = alias)
                val transport = transportFor(testProfile)
                val result = transport.ping()
                // Clean up temporary probe token.
                if (tokenValue != null && tokenValue.isNotBlank()) tokenStore.remove(alias)
                result.fold(
                    onSuccess = { onSuccess() },
                    onFailure = { err -> onError(err.message ?: "Probe failed.") },
                )
            } catch (e: Throwable) {
                onError(e.message ?: "Probe error.")
            }
        }
    }

    /** Generate a new profile id (UUID). */
    public fun newProfileId(): String =
        NSUUID().UUIDString.lowercase()

    /** Epoch millis for use in [ServerProfile.createdTs]. */
    public fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    // ── Session callbacks ─────────────────────────────────────────────────

    /** Fetch sessions for [profile] from the server. */
    public fun listSessions(
        profile: ServerProfile,
        onSuccess: (List<com.dmzs.datawatchclient.domain.Session>) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).listSessions().fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Failed to load sessions.") },
            )
        }
    }

    /** Kill a session on the server. */
    public fun killSession(
        profile: ServerProfile,
        sessionId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).killSession(sessionId).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Kill failed.") },
            )
        }
    }

    /** Restart a completed/killed/error session on the server. */
    public fun restartSession(
        profile: ServerProfile,
        sessionId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).restartSession(sessionId).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Restart failed.") },
            )
        }
    }

    /** Delete a session from the server. */
    public fun deleteSession(
        profile: ServerProfile,
        sessionId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).deleteSession(sessionId).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Delete failed.") },
            )
        }
    }

    /** Rename a session. */
    public fun renameSession(
        profile: ServerProfile,
        sessionId: String,
        name: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).renameSession(sessionId = sessionId, name = name).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Rename failed.") },
            )
        }
    }

    /** Reply to a waiting session with a text response. */
    public fun replyToSession(
        profile: ServerProfile,
        sessionId: String,
        text: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).replyToSession(sessionId, text).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Reply failed.") },
            )
        }
    }

    // ── Alert callbacks ───────────────────────────────────────────────────

    /** Fetch alerts from the server. Returns unreadCount + alert list. */
    public fun listAlerts(
        profile: ServerProfile,
        onSuccess: (com.dmzs.datawatchclient.transport.AlertsView) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).listAlerts().fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Failed to load alerts.") },
            )
        }
    }

    /** Mark all alerts as read on the server (dismiss all). */
    public fun markAllAlertsRead(
        profile: ServerProfile,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).markAlertRead(all = true).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Failed to dismiss alerts.") },
            )
        }
    }

    /** Mark a single alert as read on the server. */
    public fun markAlertRead(
        profile: ServerProfile,
        alertId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).markAlertRead(alertId = alertId, all = false).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Failed to mark alert read.") },
            )
        }
    }

    // ── Server stats callbacks ────────────────────────────────────────────

    /** Fetch server stats (CPU, memory, disk, VRAM, uptime). */
    public fun getStats(
        profile: ServerProfile,
        onSuccess: (com.dmzs.datawatchclient.transport.dto.StatsDto) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).stats().fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Failed to load stats.") },
            )
        }
    }

    /** Fetch server info (version, hostname, platform). */
    public fun fetchServerInfo(
        profile: ServerProfile,
        onSuccess: (com.dmzs.datawatchclient.domain.ServerInfo) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).fetchInfo().fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "Failed to load server info.") },
            )
        }
    }

    // ── WebSocket transport accessor ──────────────────────────────────────

    /** Returns a configured [WebSocketTransport] for [profile]. */
    public fun wsTransport(profile: ServerProfile): WebSocketTransport = wsTransportFor(profile)

    // ── Keychain token accessor ───────────────────────────────────────────

    /**
     * Synchronous Keychain read — safe to call from Swift without a coroutine.
     * Returns null when the alias is blank, the item is missing, or the device
     * is locked.
     */
    public fun getToken(alias: String): String? =
        alias.takeIf { it.isNotBlank() }?.let { tokenStore.get(it) }

    // ── Automata type callbacks ────────────────────────────────────────────

    /** Fetch all registered automata types for [profile]. */
    public fun listAutomataTypes(
        profile: ServerProfile,
        onSuccess: (List<AutomataTypeDto>) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).listAutomataTypes().fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "error") },
            )
        }
    }

    /** Register a new automata type for [profile]. */
    public fun registerAutomataType(
        profile: ServerProfile,
        req: AutomataTypeRequestDto,
        onSuccess: (AutomataTypeDto) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).registerAutomataType(req).fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onError(it.message ?: "error") },
            )
        }
    }

    /** Delete an automata type by [id] for [profile]. */
    public fun deleteAutomataType(
        profile: ServerProfile,
        id: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).deleteAutomataType(id).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "error") },
            )
        }
    }

    // ── APNs push registration ────────────────────────────────────────────

    /**
     * Called when APNs returns a device token. Stores the token locally, then
     * registers it with every enabled profile's server. Idempotent: profiles
     * that already have a device_id are skipped unless the token changed.
     *
     * Errors are swallowed per profile — push failure never blocks the user.
     */
    public fun registerApnsToken(token: String) {
        val previous = pushStore.apnsToken()
        pushStore.setApnsToken(token)
        val tokenChanged = previous != token
        ioScope.launch {
            val profiles = profileRepository.observeAll().first()
                .filter { it.enabled }
            for (profile in profiles) {
                if (!tokenChanged && pushStore.deviceIdFor(profile.id) != null) continue
                registerApnsForProfile(profile, token)
            }
        }
    }

    /**
     * Re-register all enabled profiles with the current APNs token.
     * Call from app foreground / profile-added events.
     */
    public fun reregisterAllProfiles(onComplete: (() -> Unit)? = null) {
        ioScope.launch {
            val token = pushStore.apnsToken() ?: return@launch
            val profiles = profileRepository.observeAll().first()
                .filter { it.enabled }
            for (profile in profiles) {
                registerApnsForProfile(profile, token)
            }
            onComplete?.invoke()
        }
    }

    // ── Config + LLM callbacks (used by Settings → Session) ───────────────

    /**
     * Fetch session summarizer settings from GET /api/config.
     * Parses session.summarizer.enabled and session.summarizer.llm_ref
     * in Kotlin (avoids JsonElement bridge complexity in Swift).
     */
    /** GET /api/sessions/{id}/current-status — AI summary of the live tmux pane. */
    public fun fetchSessionCurrentStatus(
        sessionId: String,
        profile: ServerProfile,
        onSuccess: (short: String, long: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).getSessionCurrentStatus(sessionId).fold(
                onSuccess = { dto -> onSuccess(dto.currentStatus, dto.currentStatusLong) },
                onFailure = { onError(it.message ?: "Failed to fetch current status.") },
            )
        }
    }

    /** POST /api/sessions/{id}/summarize — manually trigger re-summarization. */
    public fun resummarizeSession(
        sessionId: String,
        profile: ServerProfile,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).summarizeSession(sessionId).fold(
                onSuccess = { dto -> onSuccess(dto.summary) },
                onFailure = { onError(it.message ?: "Failed to re-summarize.") },
            )
        }
    }

    public fun fetchSummarizerConfig(
        profile: ServerProfile,
        onSuccess: (enabled: Boolean, llmRef: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).fetchConfig().fold(
                onSuccess = { cfg ->
                    val enabled = (cfg.raw["session.summarizer.enabled"]
                        as? kotlinx.serialization.json.JsonPrimitive)
                        ?.content?.toBooleanStrictOrNull() ?: false
                    val llmRef = (cfg.raw["session.summarizer.llm_ref"]
                        as? kotlinx.serialization.json.JsonPrimitive)
                        ?.content ?: ""
                    onSuccess(enabled, llmRef)
                },
                onFailure = { onError(it.message ?: "Failed to load config.") },
            )
        }
    }

    /** PUT /api/config with a single boolean key/value pair. */
    public fun writeConfigBool(
        profile: ServerProfile,
        key: String,
        value: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).writeConfig(
                kotlinx.serialization.json.buildJsonObject {
                    put(key, kotlinx.serialization.json.JsonPrimitive(value))
                },
            ).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Failed to save config.") },
            )
        }
    }

    /** POST /api/voice/transcribe — sends audio bytes, returns transcript string. */
    public fun transcribeAudio(
        audio: ByteArray,
        audioMime: String,
        sessionId: String?,
        profile: ServerProfile,
        onSuccess: (transcript: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).transcribeAudio(
                audio = audio,
                audioMime = audioMime,
                sessionId = sessionId,
                autoExec = false,
            ).fold(
                onSuccess = { result -> onSuccess(result.transcript.trim()) },
                onFailure = { onError(it.message ?: "Transcription failed.") },
            )
        }
    }

    /** POST /api/voice/transcribe — NSData overload for Swift callers (avoids KotlinByteArray in Swift). */
    @OptIn(ExperimentalForeignApi::class)
    public fun transcribeAudioData(
        audioData: NSData,
        audioMime: String,
        sessionId: String?,
        profile: ServerProfile,
        onSuccess: (transcript: String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val length = audioData.length.toInt()
        val bytes = if (length == 0) ByteArray(0) else {
            ByteArray(length).also { dst ->
                dst.usePinned { pinned ->
                    audioData.getBytes(pinned.addressOf(0), length.toULong())
                }
            }
        }
        transcribeAudio(bytes, audioMime, sessionId, profile, onSuccess, onError)
    }

    /** Returns true if whisper.enabled is set in server config. */
    public fun fetchWhisperEnabled(
        profile: ServerProfile,
        onResult: (Boolean) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).fetchConfig().fold(
                onSuccess = { cfg ->
                    val enabled = (cfg.raw["whisper"] as? kotlinx.serialization.json.JsonObject)
                        ?.get("enabled")
                        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content == "true" }
                        ?: false
                    onResult(enabled)
                },
                onFailure = { onResult(false) },
            )
        }
    }

    /** POST /api/summarizer/test — validates the summarizer LLM is reachable; returns latency in ms. */
    public fun testSummarizer(
        profile: ServerProfile,
        onSuccess: (latencyMs: Long) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).testSummarizer().fold(
                onSuccess = { result -> onSuccess(result.latencyMs) },
                onFailure = { onError(it.message ?: "Summarizer test failed.") },
            )
        }
    }

    /** PUT /api/config with a single string key/value pair. */
    public fun writeConfigString(
        profile: ServerProfile,
        key: String,
        value: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).writeConfig(
                kotlinx.serialization.json.buildJsonObject {
                    put(key, kotlinx.serialization.json.JsonPrimitive(value))
                },
            ).fold(
                onSuccess = { onSuccess() },
                onFailure = { onError(it.message ?: "Failed to save config.") },
            )
        }
    }

    /**
     * GET /api/llms filtered to kind == "ollama".
     * Returns only the names (Strings) — sufficient for the LLM picker.
     */
    public fun listOllamaLlmNames(
        profile: ServerProfile,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        ioScope.launch {
            transportFor(profile).listLlms().fold(
                onSuccess = { list ->
                    onSuccess(list.filter { it.kind == "ollama" }.map { it.name })
                },
                onFailure = { onError(it.message ?: "Failed to load LLMs.") },
            )
        }
    }

    private suspend fun registerApnsForProfile(profile: ServerProfile, token: String) {
        runCatching {
            transportFor(profile).registerDevice(
                deviceToken = token,
                kind = DeviceKind.Apns,
                appVersion = Version.VERSION,
                platform = DevicePlatform.Ios,
                profileHint = profile.displayName,
            ).onSuccess { deviceId ->
                pushStore.setDeviceIdFor(profile.id, deviceId)
            }
        }
    }
}
