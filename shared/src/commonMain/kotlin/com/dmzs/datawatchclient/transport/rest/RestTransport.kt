package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ConfigView
import com.dmzs.datawatchclient.domain.FileList
import com.dmzs.datawatchclient.domain.SavedCommand
import com.dmzs.datawatchclient.domain.Schedule
import com.dmzs.datawatchclient.domain.ServerInfo
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.AlertsView
import com.dmzs.datawatchclient.transport.DeviceKind
import com.dmzs.datawatchclient.transport.DevicePlatform
import com.dmzs.datawatchclient.transport.LogsView
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.TransportError
import com.dmzs.datawatchclient.transport.dto.AlertsListResponseDto
import com.dmzs.datawatchclient.transport.dto.CreateScheduleDto
import com.dmzs.datawatchclient.transport.dto.DeleteSessionDto
import com.dmzs.datawatchclient.transport.dto.DeviceRegisterDto
import com.dmzs.datawatchclient.transport.dto.DeviceRegisterResponseDto
import com.dmzs.datawatchclient.transport.dto.FederationResponseDto
import com.dmzs.datawatchclient.transport.dto.FilesListResponseDto
import com.dmzs.datawatchclient.transport.dto.HealthDto
import com.dmzs.datawatchclient.transport.dto.MarkAlertReadDto
import com.dmzs.datawatchclient.transport.dto.RenameSessionDto
import com.dmzs.datawatchclient.transport.dto.ReplyDto
import com.dmzs.datawatchclient.transport.dto.ReplyResponseDto
import com.dmzs.datawatchclient.transport.dto.RestartSessionDto
import com.dmzs.datawatchclient.transport.dto.SaveCommandDto
import com.dmzs.datawatchclient.transport.dto.SavedCommandDto
import com.dmzs.datawatchclient.transport.dto.ScheduleDto
import com.dmzs.datawatchclient.transport.dto.ServerInfoDto
import com.dmzs.datawatchclient.transport.dto.SessionDto
import com.dmzs.datawatchclient.transport.dto.SetActiveBackendDto
import com.dmzs.datawatchclient.transport.dto.StartSessionDto
import com.dmzs.datawatchclient.transport.dto.StartSessionResponseDto
import com.dmzs.datawatchclient.transport.dto.StatsDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Ktor-based REST transport. Constructed with a pre-configured [HttpClient] so
 * platform-specific engines (OkHttp on Android, Darwin on iOS) can be injected
 * without this class knowing the difference.
 *
 * [tokenProvider] is a suspend lambda so the Android side can unwrap the bearer
 * token from the Keystore-backed vault on demand instead of holding it in memory.
 */
public class RestTransport(
    override val profile: ServerProfile,
    private val client: HttpClient,
    /**
     * Resolves the bearer token on each request. `null` means the profile was
     * configured with "no bearer token" (opt-in, insecure) — in that case the
     * `Authorization` header is omitted.
     */
    private val tokenProvider: (suspend () -> String)? = null,
) : TransportClient {
    private val _isReachable = MutableStateFlow(false)
    override val isReachable: StateFlow<Boolean> = _isReachable.asStateFlow()

    override suspend fun ping(): Result<Unit> =
        request {
            val res: HttpResponse =
                client.get("${profile.baseUrl}/api/health") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }
            // health often unauth; tolerate 401 as still-reachable if handshake completed.
            if (res.status != HttpStatusCode.OK && res.status != HttpStatusCode.Unauthorized) {
                throw TransportError.ServerError(res.status.value, "unexpected status")
            }
            // Try to parse body but don't fail if absent.
            runCatching { res.body<HealthDto>() }
        }

    override suspend fun listSessions(): Result<List<Session>> =
        request {
            // Datawatch returns /api/sessions as a bare JSON array, not wrapped in
            // {"sessions": [...]} — verified against a live server 2026-04-18.
            val dto: List<SessionDto> =
                client.get("${profile.baseUrl}/api/sessions") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            dto.map { it.toDomain(profile.id) }
        }

    override suspend fun startSession(
        task: String,
        serverHint: String?,
        workingDir: String?,
        profileName: String?,
        name: String?,
        backend: String?,
        resumeId: String?,
        autoGitInit: Boolean?,
        autoGitCommit: Boolean?,
    ): Result<String> =
        request {
            val res: StartSessionResponseDto =
                client.post("${profile.baseUrl}/api/sessions/start") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(
                        StartSessionDto(
                            task = task,
                            serverHint = serverHint,
                            workingDir = workingDir,
                            profile = profileName,
                            name = name,
                            backend = backend,
                            resumeId = resumeId,
                            autoGitInit = autoGitInit,
                            autoGitCommit = autoGitCommit,
                        ),
                    )
                }.body()
            res.sessionId ?: res.id ?: error("server returned no session id")
        }

    override suspend fun replyToSession(
        sessionId: String,
        text: String,
    ): Result<Unit> =
        request {
            val res: ReplyResponseDto =
                client.post("${profile.baseUrl}/api/sessions/reply") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(ReplyDto(sessionId = sessionId, text = text))
                }.body()
            if (!res.ok) throw TransportError.ServerError(200, "server rejected reply")
        }

    override suspend fun killSession(sessionId: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/sessions/kill") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                // Server expects `{"id": fullId}` (see datawatch internal/server/api.go:handleKillSession).
                // Caller passes session.fullId (e.g. "ring-2db6"); short ids 404.
                setBody(mapOf("id" to sessionId))
            }
        }

    override suspend fun overrideSessionState(
        sessionId: String,
        state: com.dmzs.datawatchclient.domain.SessionState,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/sessions/state") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    com.dmzs.datawatchclient.transport.dto.StateOverrideDto(
                        sessionId = sessionId,
                        state = state.name.lowercase(),
                    ),
                )
            }
        }

    override suspend fun stats(): Result<StatsDto> =
        request {
            client.get("${profile.baseUrl}/api/stats") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun observerStats(): Result<com.dmzs.datawatchclient.transport.dto.ObserverStatsDto> =
        request {
            client.get("${profile.baseUrl}/api/observer/stats") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun observerPeers(): Result<com.dmzs.datawatchclient.transport.dto.ObserverPeersDto> =
        request {
            client.get("${profile.baseUrl}/api/observer/peers") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun listPlugins(): Result<com.dmzs.datawatchclient.transport.dto.PluginsDto> =
        request {
            client.get("${profile.baseUrl}/api/plugins") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun listBackends(): Result<com.dmzs.datawatchclient.transport.BackendsView> =
        request {
            // PWA ships `{llm: [{name, enabled, ...}, ...], active}`; older servers
            // sent `{llm: ["ollama", ...]}`. Accept both without a DTO.
            // Mirrors PWA renderBackendSelect: skip enabled===false and "shell".
            val nonLlm = setOf("shell")
            val root: kotlinx.serialization.json.JsonObject =
                client.get("${profile.baseUrl}/api/backends") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            val llm =
                (root["llm"] as? kotlinx.serialization.json.JsonArray)
                    .orEmpty()
                    .mapNotNull { el ->
                        when (el) {
                            is kotlinx.serialization.json.JsonPrimitive ->
                                el.content.takeIf { it.isNotEmpty() && it !in nonLlm }
                            is kotlinx.serialization.json.JsonObject -> {
                                val enabled = el["enabled"]
                                // skip explicitly disabled backends (content == "false" covers both quoted and unquoted)
                                if (enabled is kotlinx.serialization.json.JsonPrimitive &&
                                    enabled.content == "false"
                                ) return@mapNotNull null
                                (el["name"] as? kotlinx.serialization.json.JsonPrimitive)
                                    ?.content?.takeIf { it.isNotEmpty() && it !in nonLlm }
                            }
                            else -> null
                        }
                    }
            val active =
                (root["active"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.takeIf { it.isString }?.content
            com.dmzs.datawatchclient.transport.BackendsView(llm = llm, active = active)
        }

    override suspend fun listOllamaModels(): Result<List<String>> =
        request {
            val root: kotlinx.serialization.json.JsonElement =
                client.get("${profile.baseUrl}/api/ollama/models") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            // Shape: { models: [{name,...}, ...] } or bare array
            val arr = when (root) {
                is kotlinx.serialization.json.JsonObject ->
                    root["models"] as? kotlinx.serialization.json.JsonArray
                is kotlinx.serialization.json.JsonArray -> root
                else -> null
            } ?: kotlinx.serialization.json.JsonArray(emptyList())
            arr.mapNotNull { el ->
                when (el) {
                    is kotlinx.serialization.json.JsonPrimitive -> el.content
                    is kotlinx.serialization.json.JsonObject ->
                        (el["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    else -> null
                }
            }
        }

    override suspend fun listOpenWebUiModels(): Result<List<String>> =
        request {
            val root: kotlinx.serialization.json.JsonElement =
                client.get("${profile.baseUrl}/api/openwebui/models") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            // Shape: { data: [{id,...}, ...] } or bare array
            val arr = when (root) {
                is kotlinx.serialization.json.JsonObject ->
                    root["data"] as? kotlinx.serialization.json.JsonArray
                        ?: root["models"] as? kotlinx.serialization.json.JsonArray
                is kotlinx.serialization.json.JsonArray -> root
                else -> null
            } ?: kotlinx.serialization.json.JsonArray(emptyList())
            arr.mapNotNull { el ->
                when (el) {
                    is kotlinx.serialization.json.JsonPrimitive -> el.content
                    is kotlinx.serialization.json.JsonObject ->
                        (el["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            ?: (el["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    else -> null
                }
            }
        }

    override suspend fun transcribeAudio(
        audio: ByteArray,
        audioMime: String,
        sessionId: String?,
        autoExec: Boolean,
    ): Result<com.dmzs.datawatchclient.transport.VoiceTranscript> =
        request {
            val boundary = "dw-${kotlin.random.Random.nextLong()}"
            val dto: com.dmzs.datawatchclient.transport.dto.VoiceTranscribeResponseDto =
                client.post("${profile.baseUrl}/api/voice/transcribe") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    setBody(
                        io.ktor.client.request.forms.MultiPartFormDataContent(
                            parts =
                                io.ktor.client.request.forms.formData {
                                    append(
                                        key = "audio",
                                        value = audio,
                                        headers =
                                            io.ktor.http.Headers.build {
                                                append(io.ktor.http.HttpHeaders.ContentType, audioMime)
                                                append(
                                                    io.ktor.http.HttpHeaders.ContentDisposition,
                                                    "filename=\"voice.${audioMime.substringAfter('/')}\"",
                                                )
                                            },
                                    )
                                    sessionId?.let { append("session_id", it) }
                                    append("auto_exec", if (autoExec) "true" else "false")
                                    append(
                                        "ts_client",
                                        kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString(),
                                    )
                                },
                            boundary = boundary,
                        ),
                    )
                }.body()
            com.dmzs.datawatchclient.transport.VoiceTranscript(
                transcript = dto.transcript,
                confidence = dto.confidence,
                action = dto.action,
                sessionId = dto.sessionId,
                latencyMs = dto.latencyMs,
            )
        }

    override suspend fun registerDevice(
        deviceToken: String,
        kind: DeviceKind,
        appVersion: String,
        platform: DevicePlatform,
        profileHint: String,
    ): Result<String> =
        request {
            val res: DeviceRegisterResponseDto =
                client.post("${profile.baseUrl}/api/devices/register") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(
                        DeviceRegisterDto(
                            deviceToken = deviceToken,
                            kind = kind.wire,
                            appVersion = appVersion,
                            platform = platform.wire,
                            profileHint = profileHint,
                        ),
                    )
                }.body()
            res.deviceId
        }

    override suspend fun unregisterDevice(deviceId: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/devices/$deviceId") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
        }

    override suspend fun federationSessions(
        sinceEpochMs: Long?,
        states: List<com.dmzs.datawatchclient.domain.SessionState>,
        includeProxied: Boolean,
    ): Result<com.dmzs.datawatchclient.transport.FederationView> =
        request {
            val params =
                buildList {
                    sinceEpochMs?.let { add("since=$it") }
                    if (states.isNotEmpty()) add("states=" + states.joinToString(",") { it.name.lowercase() })
                    if (!includeProxied) add("include=none")
                }.joinToString("&")
            val url = "${profile.baseUrl}/api/federation/sessions" + if (params.isEmpty()) "" else "?$params"
            val dto: FederationResponseDto =
                client.get(url) {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            com.dmzs.datawatchclient.transport.FederationView(
                primary = dto.primary.map { it.toDomain(profile.id) },
                proxied =
                    dto.proxied.mapValues { entry ->
                        entry.value.map { it.toDomain("${profile.id}:${entry.key}") }
                    },
                errors = dto.errors,
            )
        }

    // ---- v0.11 session power-user parity ----

    override suspend fun renameSession(
        sessionId: String,
        name: String,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/sessions/rename") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(RenameSessionDto(id = sessionId, name = name))
            }
        }

    override suspend fun restartSession(sessionId: String): Result<Session> =
        request {
            val dto: SessionDto =
                client.post("${profile.baseUrl}/api/sessions/restart") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(RestartSessionDto(id = sessionId))
                }.body()
            dto.toDomain(profile.id)
        }

    override suspend fun deleteSession(sessionId: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/sessions/delete") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(DeleteSessionDto(id = sessionId))
            }
        }

    override suspend fun deleteSessions(sessionIds: List<String>): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/sessions/delete") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(DeleteSessionDto(ids = sessionIds))
            }
        }

    override suspend fun fetchCert(): Result<ByteArray> =
        request {
            client.get("${profile.baseUrl}/api/cert") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.readBytes()
        }

    override suspend fun setActiveBackend(name: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/backends/active") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(SetActiveBackendDto(name = name))
            }
        }

    override suspend fun listAlerts(): Result<AlertsView> =
        request {
            val dto: AlertsListResponseDto =
                client.get("${profile.baseUrl}/api/alerts") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            AlertsView(
                alerts = dto.alerts.map { it.toDomain(profile.id) },
                unreadCount = dto.unreadCount,
            )
        }

    override suspend fun markAlertRead(
        alertId: String?,
        all: Boolean,
    ): Result<Unit> =
        request {
            require(alertId != null || all) {
                "markAlertRead requires either alertId or all=true"
            }
            client.post("${profile.baseUrl}/api/alerts") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(MarkAlertReadDto(id = alertId, all = if (all) true else null))
            }
        }

    override suspend fun fetchInfo(): Result<ServerInfo> =
        request {
            val dto: ServerInfoDto =
                client.get("${profile.baseUrl}/api/info") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            dto.toDomain()
        }

    override suspend fun fetchOutput(
        sessionId: String,
        lines: Int,
    ): Result<String> =
        request {
            client.get("${profile.baseUrl}/api/output") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                parameter("id", sessionId)
                parameter("n", lines)
            }.bodyAsText()
        }

    override suspend fun fetchTimeline(sessionId: String): Result<List<String>> =
        request {
            // PWA-observed shape: `{"lines": ["<ts> | <event> | <detail>", ...]}`.
            val raw: kotlinx.serialization.json.JsonObject =
                client.get("${profile.baseUrl}/api/sessions/timeline") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    parameter("id", sessionId)
                }.body()
            val linesEl = raw["lines"] as? kotlinx.serialization.json.JsonArray
            linesEl?.mapNotNull { el ->
                (el as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
            } ?: emptyList()
        }

    override suspend fun listModels(backend: String): Result<List<String>> =
        request {
            val path =
                when (backend.lowercase()) {
                    "ollama" -> "/api/ollama/models"
                    "openwebui" -> "/api/openwebui/models"
                    else -> throw TransportError.NotFound("unknown backend: $backend")
                }
            // PWA-observed shape: a flat JSON array of model-name strings.
            val arr: kotlinx.serialization.json.JsonArray =
                client.get("${profile.baseUrl}$path") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            arr.mapNotNull { el ->
                (el as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
            }
        }

    override suspend fun listProfiles(): Result<Map<String, kotlinx.serialization.json.JsonObject>> =
        request {
            val raw: kotlinx.serialization.json.JsonObject =
                client.get("${profile.baseUrl}/api/profiles") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            raw.mapNotNull { (name, el) ->
                (el as? kotlinx.serialization.json.JsonObject)?.let { name to it }
            }.toMap()
        }

    override suspend fun writeConfig(raw: kotlinx.serialization.json.JsonObject): Result<Unit> =
        request {
            client.put("${profile.baseUrl}/api/config") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(raw)
            }
        }

    override suspend fun fetchLogs(
        lines: Int,
        offset: Int,
        level: String?,
    ): Result<LogsView> =
        request {
            val raw: kotlinx.serialization.json.JsonObject =
                client.get("${profile.baseUrl}/api/logs") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    parameter("lines", lines)
                    parameter("offset", offset)
                    level?.let { parameter("level", it) }
                }.body()
            val linesArr =
                (raw["lines"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content
                } ?: emptyList()
            val total =
                (raw["total"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content?.toIntOrNull() ?: linesArr.size
            LogsView(lines = linesArr, total = total)
        }

    override suspend fun restartDaemon(): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/restart") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
        }

    override suspend fun checkUpdate(): Result<kotlinx.serialization.json.JsonObject> =
        request {
            val response = client.get("${profile.baseUrl}/api/update/check") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            if (response.status == HttpStatusCode.NotFound) {
                // Older daemon — fall back to the POST check-and-install endpoint.
                client.post("${profile.baseUrl}/api/update") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            } else {
                response.body()
            }
        }

    override suspend fun updateDaemon(): Result<kotlinx.serialization.json.JsonObject> =
        request {
            client.post("${profile.baseUrl}/api/update") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun reloadSubsystem(subsystem: String): Result<kotlinx.serialization.json.JsonObject> =
        request {
            client.post("${profile.baseUrl}/api/reload") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                parameter("subsystem", subsystem)
            }.body()
        }

    override suspend fun listInterfaces(): Result<List<kotlinx.serialization.json.JsonObject>> =
        request {
            val arr: kotlinx.serialization.json.JsonArray =
                client.get("${profile.baseUrl}/api/interfaces") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        }

    override suspend fun memoryStats(): Result<kotlinx.serialization.json.JsonObject> =
        request {
            client.get("${profile.baseUrl}/api/memory/stats") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun memoryList(
        limit: Int,
        role: String?,
        sinceIso: String?,
    ): Result<List<kotlinx.serialization.json.JsonObject>> =
        request {
            val arr: kotlinx.serialization.json.JsonArray =
                client.get("${profile.baseUrl}/api/memory/list") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    parameter("n", limit)
                    role?.let { parameter("role", it) }
                    sinceIso?.let { parameter("since", it) }
                }.body()
            arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        }

    override suspend fun memorySearch(query: String): Result<List<kotlinx.serialization.json.JsonObject>> =
        request {
            val arr: kotlinx.serialization.json.JsonArray =
                client.get("${profile.baseUrl}/api/memory/search") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    parameter("q", query)
                }.body()
            arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        }

    override suspend fun memoryDelete(id: Long): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/memory/delete") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    kotlinx.serialization.json.buildJsonObject {
                        put(
                            "id",
                            kotlinx.serialization.json.JsonPrimitive(id),
                        )
                    },
                )
            }
        }

    override suspend fun memoryExport(): Result<ByteArray> =
        request {
            client.get("${profile.baseUrl}/api/memory/export") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<ByteArray>()
        }

    override suspend fun memoryPin(
        id: Long,
        pinned: Boolean,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/memory/pin") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    com.dmzs.datawatchclient.transport.dto.MemoryPinDto(
                        id = id,
                        pinned = pinned,
                    ),
                )
            }.body<Unit>()
        }

    override suspend fun memorySweepStale(
        olderThanDays: Int,
        dryRun: Boolean,
    ): Result<Int> =
        request {
            val res: com.dmzs.datawatchclient.transport.dto.MemorySweepStaleResponseDto =
                client.post("${profile.baseUrl}/api/memory/sweep_stale") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(
                        com.dmzs.datawatchclient.transport.dto.MemorySweepStaleRequestDto(
                            olderThanDays = olderThanDays,
                            dryRun = dryRun,
                        ),
                    )
                }.body()
            res.count
        }

    override suspend fun memorySpellcheck(
        text: String,
        extraWords: List<String>,
    ): Result<List<com.dmzs.datawatchclient.transport.dto.SpellcheckSuggestionDto>> =
        request {
            val res: com.dmzs.datawatchclient.transport.dto.MemorySpellcheckResponseDto =
                client.post("${profile.baseUrl}/api/memory/spellcheck") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(
                        com.dmzs.datawatchclient.transport.dto.MemorySpellcheckRequestDto(
                            text = text,
                            extraWords = extraWords,
                        ),
                    )
                }.body()
            res.suggestions
        }

    override suspend fun memoryExtractFacts(
        text: String,
    ): Result<List<com.dmzs.datawatchclient.transport.dto.SvoTripleDto>> =
        request {
            val res: com.dmzs.datawatchclient.transport.dto.MemoryExtractFactsResponseDto =
                client.post("${profile.baseUrl}/api/memory/extract_facts") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(
                        com.dmzs.datawatchclient.transport.dto.MemoryExtractFactsRequestDto(
                            text = text,
                        ),
                    )
                }.body()
            res.triples
        }

    override suspend fun memoryWakeup(
        projectDir: String?,
        agentId: String?,
        parentAgentId: String?,
        parentName: String?,
    ): Result<String> =
        request {
            client.get("${profile.baseUrl}/api/memory/wakeup") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                projectDir?.let { parameter("project_dir", it) }
                agentId?.let { parameter("agent_id", it) }
                parentAgentId?.let { parameter("parent_agent_id", it) }
                parentName?.let { parameter("parent_name", it) }
            }.body<String>()
        }

    override suspend fun listPrds(): Result<com.dmzs.datawatchclient.transport.dto.PrdListDto> =
        request {
            client.get("${profile.baseUrl}/api/autonomous/prds") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun createPrd(request: com.dmzs.datawatchclient.transport.dto.NewPrdRequestDto): Result<String> {
        val req = request
        return request {
            val res: com.dmzs.datawatchclient.transport.dto.NewPrdResponseDto =
                client.post("${profile.baseUrl}/api/autonomous/prds") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(req)
                }.body()
            res.id
        }
    }

    override suspend fun prdAction(
        prdId: String,
        action: String,
        body: kotlinx.serialization.json.JsonObject?,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/autonomous/prds/$prdId/$action") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }.body<Unit>()
        }

    override suspend fun editStory(
        prdId: String,
        storyId: String,
        newTitle: String?,
        newDescription: String?,
        actor: String?,
    ): Result<Unit> =
        request {
            val body =
                kotlinx.serialization.json.buildJsonObject {
                    put("story_id", kotlinx.serialization.json.JsonPrimitive(storyId))
                    newTitle?.let {
                        put("new_title", kotlinx.serialization.json.JsonPrimitive(it))
                    }
                    newDescription?.let {
                        put("new_description", kotlinx.serialization.json.JsonPrimitive(it))
                    }
                    actor?.let {
                        put("actor", kotlinx.serialization.json.JsonPrimitive(it))
                    }
                }
            client.post("${profile.baseUrl}/api/autonomous/prds/$prdId/edit_story") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body<Unit>()
        }

    override suspend fun orchestratorGraph(
        id: String,
    ): Result<com.dmzs.datawatchclient.transport.dto.OrchestratorGraphDto> =
        request {
            client.get("${profile.baseUrl}/api/orchestrator/graphs/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun startAgent(
        request: com.dmzs.datawatchclient.transport.dto.StartAgentRequestDto,
    ): Result<String> {
        val req = request
        return request {
            val res: com.dmzs.datawatchclient.transport.dto.StartAgentResponseDto =
                client.post("${profile.baseUrl}/api/agents") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(req)
                }.body()
            res.sessionId ?: res.id ?: error("server returned no session id")
        }
    }

    override suspend fun editFiles(
        prdId: String,
        storyId: String?,
        taskId: String?,
        files: List<String>,
        actor: String?,
    ): Result<Unit> =
        request {
            val body =
                kotlinx.serialization.json.buildJsonObject {
                    storyId?.let {
                        put("story_id", kotlinx.serialization.json.JsonPrimitive(it))
                    }
                    taskId?.let {
                        put("task_id", kotlinx.serialization.json.JsonPrimitive(it))
                    }
                    put(
                        "files",
                        kotlinx.serialization.json.buildJsonArray {
                            files.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        },
                    )
                    actor?.let {
                        put("actor", kotlinx.serialization.json.JsonPrimitive(it))
                    }
                }
            client.post("${profile.baseUrl}/api/autonomous/prds/$prdId/edit_files") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body<Unit>()
        }

    override suspend fun patchPrd(
        prdId: String,
        title: String?,
        spec: String?,
    ): Result<Unit> =
        request {
            val body =
                kotlinx.serialization.json.buildJsonObject {
                    title?.let { put("title", kotlinx.serialization.json.JsonPrimitive(it)) }
                    spec?.let { put("spec", kotlinx.serialization.json.JsonPrimitive(it)) }
                }
            client.patch("${profile.baseUrl}/api/autonomous/prds/$prdId") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body<Unit>()
        }

    override suspend fun deletePrd(
        prdId: String,
        hard: Boolean,
    ): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/autonomous/prds/$prdId") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                if (hard) parameter("hard", "true")
            }.body<Unit>()
        }

    override suspend fun listChannels(): Result<List<kotlinx.serialization.json.JsonObject>> =
        request {
            // PWA ships `{channels: [{id, type, enabled, ...}, ...]}`;
            // some older builds returned a bare array. Accept both.
            val root: kotlinx.serialization.json.JsonElement =
                client.get("${profile.baseUrl}/api/channels") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            val arr =
                when (root) {
                    is kotlinx.serialization.json.JsonArray -> root
                    is kotlinx.serialization.json.JsonObject ->
                        (root["channels"] as? kotlinx.serialization.json.JsonArray)
                            ?: kotlinx.serialization.json.JsonArray(emptyList())
                    else -> kotlinx.serialization.json.JsonArray(emptyList())
                }
            arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        }

    override suspend fun createChannel(
        type: String,
        id: String,
        enabled: Boolean,
        config: kotlinx.serialization.json.JsonObject?,
    ): Result<kotlinx.serialization.json.JsonObject> =
        request {
            val body =
                kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive(type))
                    put("id", kotlinx.serialization.json.JsonPrimitive(id))
                    put("enabled", kotlinx.serialization.json.JsonPrimitive(enabled))
                    config?.let { put("config", it) }
                }
            client.post("${profile.baseUrl}/api/channels") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(body)
            }.body<kotlinx.serialization.json.JsonObject>()
        }

    override suspend fun deleteChannel(channelId: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/channels/${channelId.replace(" ", "%20")}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun setChannelEnabled(
        channelId: String,
        enabled: Boolean,
    ): Result<Unit> =
        request {
            // Channel ids are server-emitted tokens (e.g. "signal",
            // "telegram") — per parent openapi they're safe URL path
            // segments, so no encoding is strictly needed; we still
            // swap literal spaces defensively.
            client.patch("${profile.baseUrl}/api/channels/${channelId.replace(" ", "%20")}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    kotlinx.serialization.json.buildJsonObject {
                        put(
                            "enabled",
                            kotlinx.serialization.json.JsonPrimitive(enabled),
                        )
                    },
                )
            }
        }

    override suspend fun sendChannelTest(
        channelId: String,
        text: String,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/channel/send") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    kotlinx.serialization.json.buildJsonObject {
                        put("channel", kotlinx.serialization.json.JsonPrimitive(channelId))
                        put("text", kotlinx.serialization.json.JsonPrimitive(text))
                    },
                )
            }
        }

    override suspend fun listRemoteServers(): Result<List<kotlinx.serialization.json.JsonObject>> =
        request {
            val arr: kotlinx.serialization.json.JsonArray =
                client.get("${profile.baseUrl}/api/servers") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        }

    override suspend fun listRemoteServerHealth(): Result<List<kotlinx.serialization.json.JsonObject>> =
        request {
            val arr: kotlinx.serialization.json.JsonArray =
                client.get("${profile.baseUrl}/api/servers/health") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        }

    override suspend fun killOrphans(): Result<kotlinx.serialization.json.JsonObject> =
        request {
            client.post("${profile.baseUrl}/api/stats/kill-orphans") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun memoryTest(): Result<kotlinx.serialization.json.JsonObject> =
        request {
            client.get("${profile.baseUrl}/api/memory/test") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun listFilters(): Result<List<kotlinx.serialization.json.JsonObject>> =
        request {
            val arr: kotlinx.serialization.json.JsonArray =
                client.get("${profile.baseUrl}/api/filters") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            arr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        }

    override suspend fun createFilter(
        pattern: String,
        action: String,
        value: String?,
        enabled: Boolean,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/filters") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    kotlinx.serialization.json.buildJsonObject {
                        put("pattern", kotlinx.serialization.json.JsonPrimitive(pattern))
                        put("action", kotlinx.serialization.json.JsonPrimitive(action))
                        value?.let { put("value", kotlinx.serialization.json.JsonPrimitive(it)) }
                        put("enabled", kotlinx.serialization.json.JsonPrimitive(enabled))
                    },
                )
            }
        }

    override suspend fun updateFilter(
        id: String,
        pattern: String?,
        action: String?,
        value: String?,
        enabled: Boolean?,
    ): Result<Unit> =
        request {
            client.patch("${profile.baseUrl}/api/filters") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    kotlinx.serialization.json.buildJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive(id))
                        pattern?.let { put("pattern", kotlinx.serialization.json.JsonPrimitive(it)) }
                        action?.let { put("action", kotlinx.serialization.json.JsonPrimitive(it)) }
                        value?.let { put("value", kotlinx.serialization.json.JsonPrimitive(it)) }
                        enabled?.let { put("enabled", kotlinx.serialization.json.JsonPrimitive(it)) }
                    },
                )
            }
        }

    override suspend fun deleteFilter(id: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/filters") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                parameter("id", id)
            }
        }

    override suspend fun fetchMcpDocs(): Result<kotlinx.serialization.json.JsonElement> =
        request {
            client.get("${profile.baseUrl}/api/mcp/docs") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun listKindProfiles(kind: String): Result<List<kotlinx.serialization.json.JsonObject>> =
        request {
            val raw: kotlinx.serialization.json.JsonObject =
                client.get("${profile.baseUrl}/api/profiles/${kind}s") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            (raw["profiles"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                ?: emptyList()
        }

    override suspend fun deleteKindProfile(
        kind: String,
        name: String,
    ): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/profiles/${kind}s/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
        }

    override suspend fun smokeKindProfile(
        kind: String,
        name: String,
    ): Result<kotlinx.serialization.json.JsonObject> =
        request {
            client.post("${profile.baseUrl}/api/profiles/${kind}s/$name/smoke") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun putKindProfile(
        kind: String,
        name: String,
        body: kotlinx.serialization.json.JsonObject,
    ): Result<Unit> =
        request {
            client.put("${profile.baseUrl}/api/profiles/${kind}s/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    // ---- v0.12 schedules + files + saved commands + config (read) ----

    override suspend fun listSchedules(
        sessionId: String?,
        state: String?,
    ): Result<List<Schedule>> =
        request {
            val dto: List<ScheduleDto> =
                client.get("${profile.baseUrl}/api/schedules") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    sessionId?.let { parameter("session_id", it) }
                    state?.let { parameter("state", it) }
                }.body()
            dto.map { it.toDomain(profile.id) }
        }

    override suspend fun createSchedule(
        task: String,
        cron: String,
        enabled: Boolean,
        sessionId: String?,
    ): Result<Schedule> =
        request {
            val dto: ScheduleDto =
                client.post("${profile.baseUrl}/api/schedules") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(
                        CreateScheduleDto(
                            task = task,
                            cron = cron,
                            enabled = enabled,
                            sessionId = sessionId,
                        ),
                    )
                }.body()
            dto.toDomain(profile.id)
        }

    override suspend fun deleteSchedule(scheduleId: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/schedules") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                parameter("id", scheduleId)
            }
        }

    override suspend fun browseFiles(path: String?): Result<FileList> =
        request {
            val dto: FilesListResponseDto =
                client.get("${profile.baseUrl}/api/files") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    path?.let { parameter("path", it) }
                }.body()
            dto.toDomain()
        }

    override suspend fun mkdir(path: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/files") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    com.dmzs.datawatchclient.transport.dto.FilesMkdirDto(
                        path = path,
                        action = "mkdir",
                    ),
                )
            }.body<Unit>()
        }

    override suspend fun listCommands(): Result<List<SavedCommand>> =
        request {
            val dto: List<SavedCommandDto> =
                client.get("${profile.baseUrl}/api/commands") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            dto.map { it.toDomain() }
        }

    override suspend fun saveCommand(
        name: String,
        command: String,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/commands") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(SaveCommandDto(name = name, command = command))
            }
        }

    override suspend fun deleteCommand(name: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/commands") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                parameter("name", name)
            }
        }

    override suspend fun fetchConfig(): Result<ConfigView> =
        request {
            val raw: Map<String, JsonElement> =
                client.get("${profile.baseUrl}/api/config") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            ConfigView(raw = raw)
        }

    private suspend fun bearer(): String? = tokenProvider?.invoke()?.let { "Bearer $it" }

    private inline fun <T> request(block: () -> T): Result<T> =
        try {
            val v = block()
            _isReachable.value = true
            Result.success(v)
        } catch (e: ClientRequestException) {
            println("RestTransport: client error ${e.response.status} for ${profile.baseUrl}: ${e.message}")
            Result.failure(mapClientError(e))
        } catch (e: ServerResponseException) {
            println("RestTransport: server error ${e.response.status} for ${profile.baseUrl}: ${e.message}")
            Result.failure(TransportError.ServerError(e.response.status.value, e.message ?: ""))
        } catch (e: ResponseException) {
            println("RestTransport: response error ${e.response.status} for ${profile.baseUrl}: ${e.message}")
            Result.failure(TransportError.ServerError(e.response.status.value, e.message ?: ""))
        } catch (e: kotlinx.serialization.SerializationException) {
            // Parse error means the *server answered* — don't flip the
            // reachability flag or show "server unreachable", which misled
            // both user and debugger when backends/channels DTOs drifted
            // from the shipped PWA shape.
            println(
                "RestTransport: parse error for ${profile.baseUrl}: " +
                    "${e::class.simpleName}: ${e.message}",
            )
            Result.failure(
                TransportError.ServerError(
                    status = 0,
                    message = "Unexpected response shape — ${e.message ?: e::class.simpleName}",
                ),
            )
        } catch (e: Throwable) {
            // Log the actual cause so adb logcat can surface TLS / DNS / routing issues
            // instead of hiding them behind "Server unreachable" in the UI.
            println("RestTransport: unreachable for ${profile.baseUrl}: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            _isReachable.value = false
            Result.failure(TransportError.Unreachable(e))
        }

    private fun mapClientError(e: ClientRequestException): TransportError =
        when (e.response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                TransportError.Unauthorized()
            HttpStatusCode.NotFound ->
                TransportError.NotFound(e.message ?: "not found")
            HttpStatusCode.TooManyRequests ->
                TransportError.RateLimited()
            else ->
                TransportError.ServerError(e.response.status.value, e.message ?: "")
        }

    public companion object {
        /**
         * Default Json configuration that tolerates extra fields (forward-compat with
         * future datawatch server versions).
         */
        public val DefaultJson: Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
                explicitNulls = false
            }

        /**
         * Install ContentNegotiation + response validation on a caller-provided
         * HttpClient config block.
         */
        public fun configureDefaults(
            client: HttpClient,
            json: Json = DefaultJson,
        ): HttpClient =
            client.config {
                install(ContentNegotiation) { json(json) }
                defaultRequest { header(HttpHeaders.UserAgent, "datawatch-client/0.1.0-pre") }
                HttpResponseValidator {
                    validateResponse { response ->
                        val status = response.status.value
                        if (status in 500..599) {
                            throw TransportError.ServerError(status, "server error")
                        }
                    }
                }
            }
    }
}
