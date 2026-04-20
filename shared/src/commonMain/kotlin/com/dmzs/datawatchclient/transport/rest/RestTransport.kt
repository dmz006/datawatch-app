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
import kotlinx.serialization.json.JsonElement
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.delete
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

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

    override suspend fun ping(): Result<Unit> = request {
        val res: HttpResponse = client.get("${profile.baseUrl}/api/health") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }
        // health often unauth; tolerate 401 as still-reachable if handshake completed.
        if (res.status != HttpStatusCode.OK && res.status != HttpStatusCode.Unauthorized) {
            throw TransportError.ServerError(res.status.value, "unexpected status")
        }
        // Try to parse body but don't fail if absent.
        runCatching { res.body<HealthDto>() }
    }

    override suspend fun listSessions(): Result<List<Session>> = request {
        // Datawatch returns /api/sessions as a bare JSON array, not wrapped in
        // {"sessions": [...]} — verified against a live server 2026-04-18.
        val dto: List<SessionDto> = client.get("${profile.baseUrl}/api/sessions") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }.body()
        dto.map { it.toDomain(profile.id) }
    }

    override suspend fun startSession(task: String, serverHint: String?): Result<String> =
        request {
            val res: StartSessionResponseDto =
                client.post("${profile.baseUrl}/api/sessions/start") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(StartSessionDto(task = task, serverHint = serverHint))
                }.body()
            res.sessionId
        }

    override suspend fun replyToSession(sessionId: String, text: String): Result<Unit> =
        request {
            val res: ReplyResponseDto =
                client.post("${profile.baseUrl}/api/sessions/reply") {
                    bearer()?.let { header(HttpHeaders.Authorization, it) }
                    contentType(ContentType.Application.Json)
                    setBody(ReplyDto(sessionId = sessionId, text = text))
                }.body()
            if (!res.ok) throw TransportError.ServerError(200, "server rejected reply")
        }

    override suspend fun killSession(sessionId: String): Result<Unit> = request {
        client.post("${profile.baseUrl}/api/sessions/kill") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            contentType(ContentType.Application.Json)
            setBody(mapOf("session_id" to sessionId))
        }
    }

    override suspend fun overrideSessionState(
        sessionId: String,
        state: com.dmzs.datawatchclient.domain.SessionState,
    ): Result<Unit> = request {
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

    override suspend fun stats(): Result<StatsDto> = request {
        client.get("${profile.baseUrl}/api/stats") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }.body()
    }

    override suspend fun listBackends(): Result<com.dmzs.datawatchclient.transport.BackendsView> = request {
        val dto: com.dmzs.datawatchclient.transport.dto.BackendsDto =
            client.get("${profile.baseUrl}/api/backends") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
            }.body()
        com.dmzs.datawatchclient.transport.BackendsView(
            llm = dto.llm,
            active = dto.active,
        )
    }

    override suspend fun transcribeAudio(
        audio: ByteArray,
        audioMime: String,
        sessionId: String?,
        autoExec: Boolean,
    ): Result<com.dmzs.datawatchclient.transport.VoiceTranscript> = request {
        val boundary = "dw-${kotlin.random.Random.nextLong()}"
        val dto: com.dmzs.datawatchclient.transport.dto.VoiceTranscribeResponseDto =
            client.post("${profile.baseUrl}/api/voice/transcribe") {
                bearer()?.let { header(HttpHeaders.Authorization, it) }
                setBody(
                    io.ktor.client.request.forms.MultiPartFormDataContent(
                        parts = io.ktor.client.request.forms.formData {
                            append(
                                key = "audio",
                                value = audio,
                                headers = io.ktor.http.Headers.build {
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
    ): Result<String> = request {
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

    override suspend fun unregisterDevice(deviceId: String): Result<Unit> = request {
        client.delete("${profile.baseUrl}/api/devices/$deviceId") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }
    }

    override suspend fun federationSessions(
        sinceEpochMs: Long?,
        states: List<com.dmzs.datawatchclient.domain.SessionState>,
        includeProxied: Boolean,
    ): Result<com.dmzs.datawatchclient.transport.FederationView> = request {
        val params = buildList {
            sinceEpochMs?.let { add("since=$it") }
            if (states.isNotEmpty()) add("states=" + states.joinToString(",") { it.name.lowercase() })
            if (!includeProxied) add("include=none")
        }.joinToString("&")
        val url = "${profile.baseUrl}/api/federation/sessions" + if (params.isEmpty()) "" else "?$params"
        val dto: FederationResponseDto = client.get(url) {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }.body()
        com.dmzs.datawatchclient.transport.FederationView(
            primary = dto.primary.map { it.toDomain(profile.id) },
            proxied = dto.proxied.mapValues { entry ->
                entry.value.map { it.toDomain("${profile.id}:${entry.key}") }
            },
            errors = dto.errors,
        )
    }

    // ---- v0.11 session power-user parity ----

    override suspend fun renameSession(sessionId: String, name: String): Result<Unit> = request {
        client.post("${profile.baseUrl}/api/sessions/rename") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            contentType(ContentType.Application.Json)
            setBody(RenameSessionDto(id = sessionId, name = name))
        }
    }

    override suspend fun restartSession(sessionId: String): Result<Session> = request {
        val dto: SessionDto = client.post("${profile.baseUrl}/api/sessions/restart") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            contentType(ContentType.Application.Json)
            setBody(RestartSessionDto(id = sessionId))
        }.body()
        dto.toDomain(profile.id)
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = request {
        client.post("${profile.baseUrl}/api/sessions/delete") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            contentType(ContentType.Application.Json)
            setBody(DeleteSessionDto(id = sessionId))
        }
    }

    override suspend fun deleteSessions(sessionIds: List<String>): Result<Unit> = request {
        client.post("${profile.baseUrl}/api/sessions/delete") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            contentType(ContentType.Application.Json)
            setBody(DeleteSessionDto(ids = sessionIds))
        }
    }

    override suspend fun fetchCert(): Result<ByteArray> = request {
        client.get("${profile.baseUrl}/api/cert") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }.readBytes()
    }

    override suspend fun setActiveBackend(name: String): Result<Unit> = request {
        client.post("${profile.baseUrl}/api/backends/active") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            contentType(ContentType.Application.Json)
            setBody(SetActiveBackendDto(name = name))
        }
    }

    override suspend fun listAlerts(): Result<AlertsView> = request {
        val dto: AlertsListResponseDto = client.get("${profile.baseUrl}/api/alerts") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }.body()
        AlertsView(
            alerts = dto.alerts.map { it.toDomain(profile.id) },
            unreadCount = dto.unreadCount,
        )
    }

    override suspend fun markAlertRead(alertId: String?, all: Boolean): Result<Unit> = request {
        require(alertId != null || all) {
            "markAlertRead requires either alertId or all=true"
        }
        client.post("${profile.baseUrl}/api/alerts") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            contentType(ContentType.Application.Json)
            setBody(MarkAlertReadDto(id = alertId, all = if (all) true else null))
        }
    }

    override suspend fun fetchInfo(): Result<ServerInfo> = request {
        val dto: ServerInfoDto = client.get("${profile.baseUrl}/api/info") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }.body()
        dto.toDomain()
    }

    override suspend fun fetchOutput(sessionId: String, lines: Int): Result<String> = request {
        client.get("${profile.baseUrl}/api/output") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            parameter("id", sessionId)
            parameter("n", lines)
        }.bodyAsText()
    }

    // ---- v0.12 schedules + files + saved commands + config (read) ----

    override suspend fun listSchedules(): Result<List<Schedule>> = request {
        val dto: List<ScheduleDto> = client.get("${profile.baseUrl}/api/schedule") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }.body()
        dto.map { it.toDomain(profile.id) }
    }

    override suspend fun createSchedule(
        task: String,
        cron: String,
        enabled: Boolean,
    ): Result<Schedule> = request {
        val dto: ScheduleDto = client.post("${profile.baseUrl}/api/schedule") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleDto(task = task, cron = cron, enabled = enabled))
        }.body()
        dto.toDomain(profile.id)
    }

    override suspend fun deleteSchedule(scheduleId: String): Result<Unit> = request {
        client.delete("${profile.baseUrl}/api/schedule") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            parameter("id", scheduleId)
        }
    }

    override suspend fun browseFiles(path: String?): Result<FileList> = request {
        val dto: FilesListResponseDto = client.get("${profile.baseUrl}/api/files") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            path?.let { parameter("path", it) }
        }.body()
        dto.toDomain()
    }

    override suspend fun listCommands(): Result<List<SavedCommand>> = request {
        val dto: List<SavedCommandDto> = client.get("${profile.baseUrl}/api/commands") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }.body()
        dto.map { it.toDomain() }
    }

    override suspend fun saveCommand(name: String, command: String): Result<Unit> = request {
        client.post("${profile.baseUrl}/api/commands") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            contentType(ContentType.Application.Json)
            setBody(SaveCommandDto(name = name, command = command))
        }
    }

    override suspend fun deleteCommand(name: String): Result<Unit> = request {
        client.delete("${profile.baseUrl}/api/commands") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
            parameter("name", name)
        }
    }

    override suspend fun fetchConfig(): Result<ConfigView> = request {
        val raw: Map<String, JsonElement> = client.get("${profile.baseUrl}/api/config") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }.body()
        ConfigView(raw = raw)
    }

    private suspend fun bearer(): String? =
        tokenProvider?.invoke()?.let { "Bearer $it" }

    private inline fun <T> request(block: () -> T): Result<T> = try {
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
        public val DefaultJson: Json = Json {
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
        ): HttpClient = client.config {
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
