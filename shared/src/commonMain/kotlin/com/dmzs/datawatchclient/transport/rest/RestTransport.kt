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
import com.dmzs.datawatchclient.transport.dto.ClonePrdToTemplateRequestDto
import com.dmzs.datawatchclient.transport.dto.CreateTemplateRequestDto
import com.dmzs.datawatchclient.transport.dto.InstantiateTemplateRequestDto
import com.dmzs.datawatchclient.transport.dto.PrdDto
import com.dmzs.datawatchclient.transport.dto.TemplateDto
import com.dmzs.datawatchclient.transport.dto.TemplateListDto
import com.dmzs.datawatchclient.transport.dto.UpdateTemplateRequestDto
import com.dmzs.datawatchclient.transport.dto.SkillRegistryDto
import com.dmzs.datawatchclient.transport.dto.SkillRegistryRequestDto
import com.dmzs.datawatchclient.transport.dto.SkillRegistryUpdateDto
import com.dmzs.datawatchclient.transport.dto.SkillDto
import com.dmzs.datawatchclient.transport.dto.AvailableSkillDto
import com.dmzs.datawatchclient.transport.dto.SyncSkillsRequestDto
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
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.append
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.core.writeFully
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import com.dmzs.datawatchclient.transport.dto.LinkQrFrameDto
import com.dmzs.datawatchclient.transport.dto.MatrixStatusDto
import com.dmzs.datawatchclient.transport.dto.SignalLinkStatusDto
import com.dmzs.datawatchclient.transport.QuickCommandItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        permissionMode: String?,
        model: String?,
        claudeEffort: String?,
        llm: String?,
        computeNode: String?,
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
                            permissionMode = permissionMode,
                            model = model,
                            claudeEffort = claudeEffort,
                            llm = llm,
                            computeNodeOverride = computeNode,
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
                    // Whisper / OpenAI transcription can take 30-60 s on slow
                    // hardware; override the 15 s global request timeout.
                    timeout { requestTimeoutMillis = 120_000 }
                    setBody(
                        io.ktor.client.request.forms.MultiPartFormDataContent(
                            parts =
                                io.ktor.client.request.forms.formData {
                                    // Use filename overload — generates one Content-Disposition
                                    // with name+filename so Go's multipart parser sees the ext.
                                    append(
                                        key = "audio",
                                        filename = "voice.${audioMime.substringAfter('/')}",
                                        contentType = ContentType.parse(audioMime),
                                        size = audio.size.toLong(),
                                    ) {
                                        writeFully(audio)
                                    }
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

    override suspend fun listClaudeModels(): Result<List<String>> =
        listClaudeEndpoint("models")

    override suspend fun listClaudeEfforts(): Result<List<String>> =
        listClaudeEndpoint("efforts")

    override suspend fun listClaudePermissionModes(): Result<List<String>> =
        listClaudeEndpoint("permission_modes")

    private suspend fun listClaudeEndpoint(sub: String): Result<List<String>> =
        request {
            val root: kotlinx.serialization.json.JsonElement =
                client.get("${profile.baseUrl}/api/llm/claude/$sub") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            val arr = when (root) {
                is kotlinx.serialization.json.JsonArray -> root
                is kotlinx.serialization.json.JsonObject -> {
                    // API returns {aliases: [...], full_names: [...]} for models,
                    // {levels: [...], source: ...} for efforts, {modes: [...], source: ...} for permission_modes.
                    // Extract the first array field found.
                    root.values.filterIsInstance<kotlinx.serialization.json.JsonArray>().firstOrNull()
                        ?: kotlinx.serialization.json.JsonArray(emptyList())
                }
                else -> kotlinx.serialization.json.JsonArray(emptyList())
            }
            arr.mapNotNull { el ->
                when (el) {
                    is kotlinx.serialization.json.JsonPrimitive ->
                        el.takeIf { it.isString }?.content
                    is kotlinx.serialization.json.JsonObject ->
                        (el["value"] as? kotlinx.serialization.json.JsonPrimitive)
                            ?.takeIf { it.isString }?.content
                    else -> null
                }
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

    override suspend fun memoryRemember(
        text: String,
        role: String,
        tags: List<String>,
    ): Result<kotlinx.serialization.json.JsonObject> =
        request {
            client.post("${profile.baseUrl}/api/memory/remember") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    kotlinx.serialization.json.buildJsonObject {
                        put("text", kotlinx.serialization.json.JsonPrimitive(text))
                        put("role", kotlinx.serialization.json.JsonPrimitive(role))
                        if (tags.isNotEmpty()) {
                            put("tags", kotlinx.serialization.json.buildJsonArray {
                                tags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                            })
                        }
                    }
                )
            }.body()
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
        permissionMode: String?,
    ): Result<Unit> =
        request {
            val body =
                kotlinx.serialization.json.buildJsonObject {
                    title?.let { put("title", kotlinx.serialization.json.JsonPrimitive(it)) }
                    spec?.let { put("spec", kotlinx.serialization.json.JsonPrimitive(it)) }
                    permissionMode?.let { put("permission_mode", kotlinx.serialization.json.JsonPrimitive(it)) }
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

    // ---- v0.63.0 Type registry ----

    override suspend fun listAutomataTypes(): Result<List<com.dmzs.datawatchclient.transport.dto.AutomataTypeDto>> =
        request {
            client.get("${profile.baseUrl}/api/autonomous/types") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun registerAutomataType(req: com.dmzs.datawatchclient.transport.dto.AutomataTypeRequestDto): Result<com.dmzs.datawatchclient.transport.dto.AutomataTypeDto> =
        request {
            client.post("${profile.baseUrl}/api/autonomous/types") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }.body()
        }

    override suspend fun deleteAutomataType(id: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/autonomous/types/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<Unit>()
        }

    // ---- v0.62.0 Security scan ----

    override suspend fun triggerScan(prdId: String): Result<com.dmzs.datawatchclient.transport.dto.ScanResultDto> =
        request {
            client.post("${profile.baseUrl}/api/autonomous/prds/$prdId/scan") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.body()
        }

    override suspend fun getScanResult(prdId: String): Result<com.dmzs.datawatchclient.transport.dto.ScanResultDto> =
        request {
            client.get("${profile.baseUrl}/api/autonomous/prds/$prdId/scan") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun createFixPrd(prdId: String): Result<com.dmzs.datawatchclient.transport.dto.PrdDto> =
        request {
            client.post("${profile.baseUrl}/api/autonomous/prds/$prdId/fix_prd") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.body()
        }

    override suspend fun proposeRules(prdId: String): Result<com.dmzs.datawatchclient.transport.dto.RuleProposalDto> =
        request {
            client.post("${profile.baseUrl}/api/autonomous/prds/$prdId/propose_rules") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.body()
        }

    override suspend fun getScanConfig(): Result<com.dmzs.datawatchclient.transport.dto.ScanConfigDto> =
        request {
            client.get("${profile.baseUrl}/api/autonomous/scan/config") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun updateScanConfig(config: com.dmzs.datawatchclient.transport.dto.ScanConfigDto): Result<Unit> =
        request {
            client.put("${profile.baseUrl}/api/autonomous/scan/config") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(config)
            }.body<Unit>()
        }

    // ---- v0.61.0 Template Store ----

    override suspend fun listTemplates(): Result<TemplateListDto> =
        request {
            client.get("${profile.baseUrl}/api/autonomous/templates") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun createTemplate(req: CreateTemplateRequestDto): Result<TemplateDto> =
        request {
            client.post("${profile.baseUrl}/api/autonomous/templates") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }.body()
        }

    override suspend fun getTemplate(id: String): Result<TemplateDto> =
        request {
            client.get("${profile.baseUrl}/api/autonomous/templates/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun updateTemplate(id: String, req: UpdateTemplateRequestDto): Result<TemplateDto> =
        request {
            client.put("${profile.baseUrl}/api/autonomous/templates/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }.body()
        }

    override suspend fun deleteTemplate(id: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/autonomous/templates/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<Unit>()
        }

    override suspend fun instantiateTemplate(
        id: String,
        req: InstantiateTemplateRequestDto,
    ): Result<PrdDto> =
        request {
            client.post("${profile.baseUrl}/api/autonomous/templates/$id/instantiate") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }.body()
        }

    override suspend fun clonePrdToTemplate(
        prdId: String,
        req: ClonePrdToTemplateRequestDto,
    ): Result<TemplateDto> =
        request {
            client.post("${profile.baseUrl}/api/autonomous/prds/$prdId/clone_to_template") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }.body()
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

    override suspend fun fetchChannelInfo(): Result<kotlinx.serialization.json.JsonElement> =
        request {
            client.get("${profile.baseUrl}/api/channel/info") {
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

    override suspend fun fetchSystemQuickCommands(): Result<List<QuickCommandItem>> =
        request {
            val raw: Map<String, JsonElement> =
                client.get("${profile.baseUrl}/api/config") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                }.body()
            raw["quick_commands"]
                ?.jsonArray
                ?.mapNotNull { el ->
                    runCatching {
                        val obj = el.jsonObject
                        QuickCommandItem(
                            label = obj["label"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            value = obj["value"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                        )
                    }.getOrNull()
                }
                ?: emptyList()
        }

    // ------ BL21: Signal device-linking (datawatch#31) ------

    override fun startSignalLinking(): Flow<LinkQrFrameDto> =
        callbackFlow {
            val response =
                client.get("${profile.baseUrl}/api/link/qr") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    header(HttpHeaders.Accept, "text/event-stream")
                    header(HttpHeaders.CacheControl, "no-cache")
                }
            val channel = response.bodyAsChannel()
            var data = StringBuilder()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.isBlank() -> {
                        val payload = data.toString().trim()
                        if (payload.isNotEmpty()) {
                            runCatching { DefaultJson.decodeFromString<LinkQrFrameDto>(payload) }
                                .onSuccess { trySend(it) }
                        }
                        data = StringBuilder()
                    }
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").trimStart())
                    }
                }
            }
            awaitClose { }
        }

    override suspend fun getSignalLinkStatus(): Result<SignalLinkStatusDto> =
        request {
            client.get("${profile.baseUrl}/api/link/status") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun cancelSignalLink(): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/link/cancel") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun unlinkSignalDevice(deviceId: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/link/$deviceId") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    // ------ BL255: Skill Registries (datawatch v6.7.0) ------

    override suspend fun listSkillRegistries(): Result<List<SkillRegistryDto>> =
        request {
            client.get("${profile.baseUrl}/api/skills/registries") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<com.dmzs.datawatchclient.transport.dto.SkillRegistriesResponseDto>().registries
        }

    override suspend fun createSkillRegistry(req: SkillRegistryRequestDto): Result<SkillRegistryDto> =
        request {
            client.post("${profile.baseUrl}/api/skills/registries") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }.body()
        }

    override suspend fun updateSkillRegistry(name: String, req: SkillRegistryUpdateDto): Result<SkillRegistryDto> =
        request {
            client.put("${profile.baseUrl}/api/skills/registries/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }.body()
        }

    override suspend fun deleteSkillRegistry(name: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/skills/registries/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun addDefaultSkillRegistry(): Result<SkillRegistryDto> =
        request {
            client.post("${profile.baseUrl}/api/skills/registries/add-default") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun connectSkillRegistry(name: String): Result<SkillRegistryDto> =
        request {
            client.post("${profile.baseUrl}/api/skills/registries/$name/connect") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun listAvailableSkills(name: String): Result<List<AvailableSkillDto>> =
        request {
            client.get("${profile.baseUrl}/api/skills/registries/$name/available") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun syncSkills(name: String, req: SyncSkillsRequestDto): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/skills/registries/$name/sync") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            Unit
        }

    override suspend fun unsyncSkills(name: String, req: SyncSkillsRequestDto): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/skills/registries/$name/unsync") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            Unit
        }

    override suspend fun listSyncedSkills(): Result<List<SkillDto>> =
        request {
            client.get("${profile.baseUrl}/api/skills") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<com.dmzs.datawatchclient.transport.dto.SkillsResponseDto>().skills ?: emptyList()
        }

    // ---- v0.74.0 Compute Nodes (S5-1) ----

    override suspend fun listComputeNodes(): Result<List<com.dmzs.datawatchclient.transport.dto.ComputeNodeDto>> =
        request {
            client.get("${profile.baseUrl}/api/compute/nodes") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<com.dmzs.datawatchclient.transport.dto.ComputeNodesResponseDto>().nodes
        }

    override suspend fun createComputeNode(
        dto: com.dmzs.datawatchclient.transport.dto.ComputeNodeDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.ComputeNodeDto> =
        request {
            client.post("${profile.baseUrl}/api/compute/nodes") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.body()
        }

    override suspend fun updateComputeNode(
        name: String,
        dto: com.dmzs.datawatchclient.transport.dto.ComputeNodeDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.ComputeNodeDto> =
        request {
            client.put("${profile.baseUrl}/api/compute/nodes/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.body()
        }

    override suspend fun deleteComputeNode(name: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/compute/nodes/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun getComputeNodeModels(name: String, kind: String): Result<List<String>> =
        request {
            client.get("${profile.baseUrl}/api/compute/nodes/$name/models") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                parameter("kind", kind)
            }.body()
        }

    // ---- v0.74.0 LLM Registry (S5-2) ----

    override suspend fun listLlms(): Result<List<com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto>> =
        request {
            client.get("${profile.baseUrl}/api/llms") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<com.dmzs.datawatchclient.transport.dto.LlmsResponseDto>().llms
        }

    override suspend fun createLlm(
        dto: com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto> =
        request {
            client.post("${profile.baseUrl}/api/llms") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.body()
        }

    override suspend fun updateLlm(
        name: String,
        dto: com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.LlmRegistryEntryDto> =
        request {
            client.put("${profile.baseUrl}/api/llms/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.body()
        }

    override suspend fun deleteLlm(name: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/llms/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun enableLlm(name: String, enabled: Boolean, pretest: Boolean): Result<Unit> =
        request {
            client.patch("${profile.baseUrl}/api/llms/$name/enabled") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.LlmToggleRequest(enabled = enabled, pretest = pretest))
            }
            Unit
        }

    // ---- v0.74.0 Migration (S5-3) ----

    override suspend fun getMigrationStatus(): Result<com.dmzs.datawatchclient.transport.dto.MigrationStatusDto> =
        request {
            client.get("${profile.baseUrl}/api/migration/status") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun dismissMigration(): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/migration/status") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    // ---- v0.84.0 Sprint 15 — migration + observer binding ----

    override suspend fun getMigrationComputeKinds(): Result<com.dmzs.datawatchclient.transport.dto.MigrationComputeKindsDto> =
        request {
            client.get("${profile.baseUrl}/api/migration/compute-kinds") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun migrateComputeNodeKind(name: String, kind: String): Result<Unit> =
        request {
            client.put("${profile.baseUrl}/api/migration/compute-kinds/${name.replace(" ", "%20")}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.MigrateKindRequestDto(kind = kind))
            }
            Unit
        }

    override suspend fun toggleComputeNodeEnabled(name: String, enabled: Boolean): Result<Unit> =
        request {
            client.patch("${profile.baseUrl}/api/compute/nodes/${name.replace(" ", "%20")}/enabled") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(mapOf("enabled" to enabled))
            }
            Unit
        }

    override suspend fun getFreePeers(): Result<List<com.dmzs.datawatchclient.transport.dto.FreeObserverPeerDto>> =
        request {
            client.get("${profile.baseUrl}/api/observer/peers/free") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun attachObserverPeer(nodeName: String, peer: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/compute/nodes/${nodeName.replace(" ", "%20")}/observer-peer") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.AttachObserverRequestDto(peer = peer))
            }
            Unit
        }

    override suspend fun detachObserverPeer(nodeName: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/compute/nodes/${nodeName.replace(" ", "%20")}/observer-peer") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    // ---- v0.75.0 Vault/Secrets + Docs Search (S6-3, S6-4 BL274) ----

    override suspend fun getSecretsStatus(): Result<com.dmzs.datawatchclient.transport.dto.SecretsStatusDto> =
        request {
            client.get("${profile.baseUrl}/api/secrets/status") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun docsSearch(
        q: String,
        limit: Int,
    ): Result<List<com.dmzs.datawatchclient.transport.dto.DocsSearchResultDto>> =
        request {
            client.get("${profile.baseUrl}/api/docs/search") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                parameter("q", q)
                parameter("limit", limit)
            }.body()
        }

    override suspend fun docsPendingList(): Result<List<com.dmzs.datawatchclient.transport.dto.DocsPendingSourceDto>> =
        request {
            client.get("${profile.baseUrl}/api/docs/trust/pending") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun docsTrustAccept(paths: List<String>): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/docs/trust/accept") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.DocsTrustBulkRequest(paths))
            }
            Unit
        }

    override suspend fun docsTrustDismiss(paths: List<String>): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/docs/trust/dismiss") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.DocsTrustBulkRequest(paths))
            }
            Unit
        }

    override suspend fun docsTrustedList(): Result<List<com.dmzs.datawatchclient.transport.dto.DocsTrustedSourceDto>> =
        request {
            client.get("${profile.baseUrl}/api/docs/trust") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun docsTrustRemove(path: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/docs/trust/${path.replace("/", "%2F")}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun docsListHowtos(): Result<List<com.dmzs.datawatchclient.transport.dto.DocsHowtoDto>> =
        request {
            client.get("${profile.baseUrl}/api/docs/howtos") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<com.dmzs.datawatchclient.transport.dto.DocsHowtosResponse>().howtos
        }

    override suspend fun docsTrustAdd(source: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/docs/trust/add") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.DocsTrustAddRequest(source))
            }
            Unit
        }

    // ---- v0.73.0 Sprint 4: Identity, Algorithm Mode, Evals ----

    override suspend fun getIdentity(): Result<com.dmzs.datawatchclient.transport.dto.IdentityDto> =
        request {
            client.get("${profile.baseUrl}/api/identity") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun setIdentity(dto: com.dmzs.datawatchclient.transport.dto.IdentityDto): Result<com.dmzs.datawatchclient.transport.dto.IdentityDto> =
        request {
            client.put("${profile.baseUrl}/api/identity") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(dto)
            }.body()
        }

    override suspend fun algorithmList(): Result<List<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto>> =
        request {
            client.get("${profile.baseUrl}/api/algorithm") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<com.dmzs.datawatchclient.transport.dto.AlgorithmListResponseDto>().sessions
        }

    override suspend fun algorithmAdvance(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto> =
        request {
            client.post("${profile.baseUrl}/api/algorithm/$sessionId/advance") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun algorithmAbort(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto> =
        request {
            client.post("${profile.baseUrl}/api/algorithm/$sessionId/abort") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun algorithmStart(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto> =
        request {
            client.post("${profile.baseUrl}/api/algorithm/$sessionId/start") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody("""{}""")
            }.body()
        }

    override suspend fun algorithmGet(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto> =
        request {
            client.get("${profile.baseUrl}/api/algorithm/$sessionId") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun algorithmReset(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto> =
        request {
            client.delete("${profile.baseUrl}/api/algorithm/$sessionId") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun algorithmEdit(sessionId: String, output: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto> =
        request {
            client.post("${profile.baseUrl}/api/algorithm/$sessionId/edit") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(kotlinx.serialization.json.buildJsonObject {
                    put("output", kotlinx.serialization.json.JsonPrimitive(output))
                }.toString())
            }.body()
        }

    override suspend fun algorithmMeasure(sessionId: String, suite: String): Result<com.dmzs.datawatchclient.transport.dto.AlgorithmStateDto> =
        request {
            client.post("${profile.baseUrl}/api/algorithm/$sessionId/measure") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                url { parameters.append("suite", suite) }
            }.body()
        }

    override suspend fun evalsList(): Result<List<com.dmzs.datawatchclient.transport.dto.EvalSuiteDto>> =
        request {
            client.get("${profile.baseUrl}/api/evals/suites") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<com.dmzs.datawatchclient.transport.dto.EvalSuitesResponseDto>().suites
        }

    override suspend fun evalsRun(suiteId: String): Result<com.dmzs.datawatchclient.transport.dto.EvalRunResultDto> =
        request {
            client.post("${profile.baseUrl}/api/evals/$suiteId/run") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    // ---- v0.77.0 Council persona wizard (S8-1/2/3, #92) ----

    override suspend fun councilListPersonas(): Result<List<com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto>> =
        request {
            client.get("${profile.baseUrl}/api/council/personas") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<com.dmzs.datawatchclient.transport.dto.CouncilPersonasResponseDto>().personas
        }

    override suspend fun councilListRuns(): Result<List<com.dmzs.datawatchclient.transport.dto.CouncilRunDto>> =
        request {
            client.get("${profile.baseUrl}/api/council/runs") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<com.dmzs.datawatchclient.transport.dto.CouncilRunsResponseDto>().runs
        }

    override suspend fun councilGetConfig(): Result<com.dmzs.datawatchclient.transport.dto.CouncilConfigDto> =
        request {
            client.get("${profile.baseUrl}/api/council/config") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun councilUpdateConfig(
        config: com.dmzs.datawatchclient.transport.dto.CouncilConfigDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.CouncilConfigDto> =
        request {
            client.put("${profile.baseUrl}/api/council/config") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(config)
            }.body()
        }

    override suspend fun councilStartRun(
        request: com.dmzs.datawatchclient.transport.dto.StartCouncilRunRequest,
    ): Result<com.dmzs.datawatchclient.transport.dto.CouncilRunDto> {
        val req = request
        return this.request {
            client.post("${profile.baseUrl}/api/council/run") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(req)
            }.body()
        }
    }

    override suspend fun councilStopRun(id: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/council/runs/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun createCouncilPersona(
        dto: com.dmzs.datawatchclient.transport.dto.CouncilPersonaCreateDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto> =
        request {
            client.post("${profile.baseUrl}/api/council/personas") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.body()
        }

    override suspend fun updateCouncilPersona(
        name: String,
        dto: com.dmzs.datawatchclient.transport.dto.CouncilPersonaCreateDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto> =
        request {
            client.put("${profile.baseUrl}/api/council/personas/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.body()
        }

    // Sprint 31 — alpha.39/40 Council persona built-in support + delete
    override suspend fun getCouncilPersona(
        name: String,
    ): Result<com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto> =
        request {
            client.get("${profile.baseUrl}/api/council/personas/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun setCouncilPersona(
        persona: com.dmzs.datawatchclient.transport.dto.CouncilPersonaDto,
    ): Result<Unit> =
        request {
            client.put("${profile.baseUrl}/api/council/personas/${persona.name}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(persona)
            }
            Unit
        }

    override suspend fun deleteCouncilPersona(name: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/council/personas/$name") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    // ---- v0.80.0 Sprint 11: Cost Rates, Routing Rules, Tailscale Mesh ----

    override suspend fun getCostRates(): Result<com.dmzs.datawatchclient.transport.dto.CostRatesDto> =
        request {
            client.get("${profile.baseUrl}/api/cost/rates") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun saveCostRates(
        rates: Map<String, com.dmzs.datawatchclient.transport.dto.CostRateDto>,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/cost/rates") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    kotlinx.serialization.json.buildJsonObject {
                        put(
                            "rates",
                            kotlinx.serialization.json.Json.encodeToJsonElement(
                                kotlinx.serialization.serializer<Map<String, com.dmzs.datawatchclient.transport.dto.CostRateDto>>(),
                                rates,
                            ),
                        )
                    },
                )
            }
            Unit
        }

    override suspend fun getRoutingRules(): Result<com.dmzs.datawatchclient.transport.dto.RoutingRulesDto> =
        request {
            client.get("${profile.baseUrl}/api/routing-rules") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun setRoutingRules(
        rules: List<com.dmzs.datawatchclient.transport.dto.RoutingRuleDto>,
    ): Result<com.dmzs.datawatchclient.transport.dto.RoutingRulesDto> =
        request {
            client.post("${profile.baseUrl}/api/routing-rules") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.RoutingRulesDto(rules))
            }.body()
        }

    override suspend fun testRouting(task: String): Result<com.dmzs.datawatchclient.transport.dto.RoutingTestResultDto> =
        request {
            client.post("${profile.baseUrl}/api/routing-rules/test") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.RoutingTestRequestDto(task))
            }.body()
        }

    override suspend fun getTailscaleStatus(): Result<com.dmzs.datawatchclient.transport.dto.TailscaleStatusDto> =
        request {
            client.get("${profile.baseUrl}/api/tailscale/status") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    // ---- v0.81.0 Sprint 12: Pipelines + OrchestratorGraphs list ----

    override suspend fun getPipelines(): Result<List<com.dmzs.datawatchclient.transport.dto.PipelineListItemDto>> =
        request {
            client.get("${profile.baseUrl}/api/pipelines") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun getOrchestratorGraphsList(): Result<com.dmzs.datawatchclient.transport.dto.OrchestratorGraphsListDto> =
        request {
            client.get("${profile.baseUrl}/api/orchestrator/graphs") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun createOrchestratorGraph(
        title: String,
        directory: String,
        prdIds: List<String>,
    ): Result<com.dmzs.datawatchclient.transport.dto.OrchestratorGraphListItemDto> =
        request {
            client.post("${profile.baseUrl}/api/orchestrator/graphs") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    com.dmzs.datawatchclient.transport.dto.CreateOrchestratorGraphRequestDto(
                        title = title,
                        directory = directory,
                        prdIds = prdIds,
                    ),
                )
            }.body()
        }

    override suspend fun runOrchestratorGraph(id: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/orchestrator/graphs/$id/run") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun deleteOrchestratorGraph(id: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/orchestrator/graphs/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    // ---- v0.82.0 Sprint 13: General tab gaps ----

    override suspend fun getSessionTemplates(): Result<List<com.dmzs.datawatchclient.transport.dto.SessionTemplateDto>> =
        request {
            client.get("${profile.baseUrl}/api/templates") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun createSessionTemplate(
        template: com.dmzs.datawatchclient.transport.dto.SessionTemplateDto,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/templates") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(template)
            }
            Unit
        }

    override suspend fun deleteSessionTemplate(name: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/templates/${name.replace(" ", "%20")}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun getDeviceAliases(): Result<List<com.dmzs.datawatchclient.transport.dto.DeviceAliasDto>> =
        request {
            client.get("${profile.baseUrl}/api/device-aliases") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun createDeviceAlias(alias: String, server: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/device-aliases") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.DeviceAliasDto(alias = alias, server = server))
            }
            Unit
        }

    override suspend fun deleteDeviceAlias(alias: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/device-aliases/${alias.replace(" ", "%20")}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun getToolingStatus(): Result<com.dmzs.datawatchclient.transport.dto.ToolingStatusDto> =
        request {
            client.get("${profile.baseUrl}/api/tooling/status") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun toolingGitignore(backend: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/tooling/gitignore") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(mapOf("backend" to backend))
            }
            Unit
        }

    override suspend fun toolingCleanup(backend: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/tooling/cleanup") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(mapOf("backend" to backend))
            }
            Unit
        }

    override suspend fun getSecrets(): Result<com.dmzs.datawatchclient.transport.dto.SecretsListDto> =
        request {
            client.get("${profile.baseUrl}/api/secrets") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun addSecret(
        secret: com.dmzs.datawatchclient.transport.dto.AddSecretDto,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/secrets") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(secret)
            }
            Unit
        }

    override suspend fun deleteSecret(name: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/secrets/${name.replace(" ", "%20")}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    // ---- v0.88.0 Sprint 19: Observer by-node + federation meta-peers ----

    override suspend fun getObserverPeersByNode(): Result<com.dmzs.datawatchclient.transport.dto.ObserverPeersByNodeDto> =
        request {
            client.get("${profile.baseUrl}/api/observer/peers/by-node") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun getFederationMetaPeers(): Result<com.dmzs.datawatchclient.transport.dto.MetaPeersDto> =
        request {
            client.get("${profile.baseUrl}/api/federation/meta-peers") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun patchProjectAgentSettings(
        name: String,
        settings: com.dmzs.datawatchclient.transport.dto.AgentSettingsDto,
    ): Result<Unit> =
        request {
            client.patch("${profile.baseUrl}/api/profiles/projects/${name.replace(" ", "%20")}/agent-settings") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(settings)
            }
        }

    override suspend fun getSessionStatus(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.SessionStatusBoardDto> =
        request {
            client.get("${profile.baseUrl}/api/sessions/${sessionId}/status") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    // Sprint 27 — alpha.33 Ollama marketplace
    override suspend fun getInstalledOllamaModels(nodeId: String): Result<com.dmzs.datawatchclient.transport.dto.OllamaInstalledModelsDto> =
        request {
            client.get("${profile.baseUrl}/api/compute/nodes/${nodeId}/models") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun getOllamaCatalog(): Result<com.dmzs.datawatchclient.transport.dto.OllamaCatalogDto> =
        request {
            client.get("${profile.baseUrl}/api/marketplace/ollama/catalog") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun pullOllamaModel(nodeId: String, model: String): Result<com.dmzs.datawatchclient.transport.dto.OllamaPullTaskDto> =
        request {
            client.post("${profile.baseUrl}/api/compute/nodes/${nodeId}/models/pull") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody("""{"model":"$model"}""")
            }.body()
        }

    override suspend fun getPullTask(taskId: String): Result<com.dmzs.datawatchclient.transport.dto.OllamaPullTaskDto> =
        request {
            client.get("${profile.baseUrl}/api/marketplace/ollama/tasks/${taskId}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun deleteOllamaModel(nodeId: String, model: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/compute/nodes/${nodeId}/models/${model}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    // Sprint 28 — alpha.35 UnifiedPush SSE
    override suspend fun registerPush(
        registration: com.dmzs.datawatchclient.transport.dto.PushRegistrationDto,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/push/register") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(registration)
            }
            Unit
        }

    override fun subscribePushAlerts(): Flow<com.dmzs.datawatchclient.transport.dto.PushEventDto> = flow {
        val url = "${profile.baseUrl}/api/push/alerts"
        var backoff = 1_000L
        while (true) {
            try {
                client.prepareGet(url) {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    timeout {
                        // SSE stream is unbounded — no request timeout; keep global connect/socket timeouts
                        requestTimeoutMillis = Long.MAX_VALUE
                        socketTimeoutMillis = 30_000L
                        connectTimeoutMillis = 5_000L
                    }
                }.execute { res ->
                    val channel = res.bodyAsChannel()
                    var data = ""
                    backoff = 1_000L
                    while (true) {
                        val line = channel.readUTF8Line() ?: break
                        when {
                            line.startsWith("data:") -> data = line.removePrefix("data:").trim()
                            line.isBlank() && data.isNotBlank() -> {
                                runCatching {
                                    DefaultJson.decodeFromString<com.dmzs.datawatchclient.transport.dto.PushEventDto>(data)
                                }.onSuccess { dto ->
                                    if (dto.event != "open" && dto.event != "keepalive") emit(dto)
                                }
                                data = ""
                            }
                            line.isBlank() -> data = ""
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    // Sprint 30 — LLM multi-node + session management
    override suspend fun getLlmSessions(
        name: String,
        page: Int,
        size: Int,
    ): Result<com.dmzs.datawatchclient.transport.dto.LlmSessionsDto> =
        request {
            client.get("${profile.baseUrl}/api/llms/${name}/sessions") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                parameter("page", page)
                parameter("size", size)
            }.body()
        }

    override suspend fun reassignLlmSessions(
        fromName: String,
        toName: String,
        force: Boolean,
    ): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/llms/${fromName}/reassign") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.LlmReassignDto(newLlm = toName, force = force))
            }
            Unit
        }

    // Sprint 35 — observer envelopes per-session (G8)
    override suspend fun getSessionEnvelopes(
        sessionId: String,
    ): Result<List<com.dmzs.datawatchclient.transport.dto.StatEnvelopeDto>> =
        request {
            client.get("${profile.baseUrl}/api/observer/envelopes") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                parameter("session_id", sessionId)
            }.body()
        }

    override suspend fun listDashboardCards(): Result<List<com.dmzs.datawatchclient.transport.dto.DashboardCardDto>> =
        request {
            client.get("${profile.baseUrl}/api/dashboard/cards") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun addDashboardCard(
        card: com.dmzs.datawatchclient.transport.dto.DashboardCardDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.DashboardCardDto> =
        request {
            client.post("${profile.baseUrl}/api/dashboard/cards") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(card)
            }.body()
        }

    override suspend fun updateDashboardCard(
        id: String,
        card: com.dmzs.datawatchclient.transport.dto.DashboardCardDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.DashboardCardDto> =
        request {
            client.put("${profile.baseUrl}/api/dashboard/cards/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(card)
            }.body()
        }

    override suspend fun deleteDashboardCard(id: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/dashboard/cards/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun getSessionTelemetry(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.SessionTelemetryDto> =
        request {
            client.get("${profile.baseUrl}/api/sessions/$sessionId/telemetry") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun listGuardrailLibrary(): Result<List<com.dmzs.datawatchclient.transport.dto.GuardrailLibraryItemDto>> =
        request {
            client.get("${profile.baseUrl}/api/autonomous/guardrails") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun listGuardrailProfiles(): Result<List<com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto>> =
        request {
            client.get("${profile.baseUrl}/api/autonomous/guardrail-profiles") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun createGuardrailProfile(
        profile: com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto> =
        request {
            val dto = profile
            client.post("${this.profile.baseUrl}/api/autonomous/guardrail-profiles") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.body()
        }

    override suspend fun updateGuardrailProfile(
        id: String,
        profile: com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto,
    ): Result<com.dmzs.datawatchclient.transport.dto.GuardrailProfileDto> =
        request {
            val dto = profile
            client.put("${this.profile.baseUrl}/api/autonomous/guardrail-profiles/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(dto)
            }.body()
        }

    override suspend fun deleteGuardrailProfile(id: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/autonomous/guardrail-profiles/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun runSessionGuardrail(sessionId: String): Result<com.dmzs.datawatchclient.transport.dto.GuardrailRunResultDto> =
        request {
            client.post("${profile.baseUrl}/api/sessions/$sessionId/guardrail") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun getSmokeProgress(): Result<com.dmzs.datawatchclient.transport.dto.SmokeProgressDto?> =
        request {
            val response = client.get("${profile.baseUrl}/api/smoke/progress") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            if (response.status == io.ktor.http.HttpStatusCode.NoContent) null
            else response.body<com.dmzs.datawatchclient.transport.dto.SmokeProgressDto>()
        }

    override suspend fun clearSmokeProgress(): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/smoke/progress") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun listEvalRuns(): Result<List<com.dmzs.datawatchclient.transport.dto.EvalRunHistoryDto>> =
        request {
            client.get("${profile.baseUrl}/api/evals") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body<com.dmzs.datawatchclient.transport.dto.EvalRunsResponseDto>().runs
        }

    // ---- T30: Channel Routing ----

    override suspend fun getChannelRouting(): Result<com.dmzs.datawatchclient.transport.dto.ChannelRoutingListDto> =
        request {
            client.get("${profile.baseUrl}/api/channel/routing") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun putChannelRouting(
        rules: List<com.dmzs.datawatchclient.transport.dto.ChannelRoutingRuleDto>,
    ): Result<com.dmzs.datawatchclient.transport.dto.ChannelRoutingListDto> =
        request {
            client.put("${profile.baseUrl}/api/channel/routing") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.ChannelRoutingListDto(rules = rules))
            }.body()
        }

    // ---- T30: File Service ----

    override suspend fun getFileServiceMeta(): Result<com.dmzs.datawatchclient.transport.dto.FileServiceMetaDto> =
        request {
            client.get("${profile.baseUrl}/api/files/meta") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    // ---- T30: Discussion Scopes ----

    override suspend fun listDiscussions(): Result<com.dmzs.datawatchclient.transport.dto.DiscussionListDto> =
        request {
            client.get("${profile.baseUrl}/api/memory/discussion") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun writeDiscussionMessage(
        id: String,
        content: String,
    ): Result<com.dmzs.datawatchclient.transport.dto.DiscussionWriteResponseDto> =
        request {
            client.post("${profile.baseUrl}/api/memory/discussion/$id") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(com.dmzs.datawatchclient.transport.dto.DiscussionWriteRequestDto(content = content))
            }.body()
        }

    // ---- T30: Encryption Status ----

    override suspend fun getEncryptionStatus(): Result<com.dmzs.datawatchclient.transport.dto.EncryptionStatusDto> =
        request {
            client.get("${profile.baseUrl}/api/security/encryption/status") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    // ---- S14b: Alert Rules ----

    override suspend fun listAlertRules(): Result<com.dmzs.datawatchclient.transport.dto.AlertRulesListDto> =
        request {
            client.get("${profile.baseUrl}/api/alert-rules") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun createAlertRule(rule: com.dmzs.datawatchclient.transport.dto.AlertRuleDto): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/alert-rules") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(rule)
            }
            Unit
        }

    override suspend fun deleteAlertRule(name: String): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/alert-rules/${name.replace(" ", "%20")}") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun enableAlertRule(name: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/alert-rules/${name.replace(" ", "%20")}/enable") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun disableAlertRule(name: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/alert-rules/${name.replace(" ", "%20")}/disable") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    // ---- Observer cards: Cooldown, Analytics, Audit ----

    override suspend fun getCooldownStatus(): Result<com.dmzs.datawatchclient.transport.dto.CooldownStatusDto> =
        request {
            client.get("${profile.baseUrl}/api/cooldown") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun setCooldown(untilUnixMs: Long, reason: String): Result<Unit> =
        request {
            client.post("${profile.baseUrl}/api/cooldown") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                contentType(ContentType.Application.Json)
                setBody(
                    kotlinx.serialization.json.buildJsonObject {
                        put("until_unix_ms", kotlinx.serialization.json.JsonPrimitive(untilUnixMs))
                        put("reason", kotlinx.serialization.json.JsonPrimitive(reason))
                    },
                )
            }
            Unit
        }

    override suspend fun clearCooldown(): Result<Unit> =
        request {
            client.delete("${profile.baseUrl}/api/cooldown") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }
            Unit
        }

    override suspend fun getAnalytics(rangeDays: Int): Result<com.dmzs.datawatchclient.transport.dto.AnalyticsDto> =
        request {
            client.get("${profile.baseUrl}/api/analytics?range=${rangeDays}d") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun getAuditLog(
        actor: String?,
        action: String?,
        limit: Int,
    ): Result<com.dmzs.datawatchclient.transport.dto.AuditListDto> =
        request {
            val params =
                buildList {
                    add("limit=$limit")
                    actor?.let { add("actor=${it.replace(" ", "%20")}") }
                    action?.let { add("action=${it.replace(" ", "%20")}") }
                }.joinToString("&")
            client.get("${profile.baseUrl}/api/audit?$params") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        }

    override suspend fun fetchMatrixStatus(): Result<MatrixStatusDto> =
        request {
            client.get("${profile.baseUrl}/api/matrix/status") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
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
            HttpStatusCode.Conflict ->
                TransportError.Conflict(e.message ?: "conflict")
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
