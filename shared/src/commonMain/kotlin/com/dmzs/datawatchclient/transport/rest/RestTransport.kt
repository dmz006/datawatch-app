package com.dmzs.datawatchclient.transport.rest

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.TransportError
import com.dmzs.datawatchclient.transport.dto.HealthDto
import com.dmzs.datawatchclient.transport.dto.ReplyDto
import com.dmzs.datawatchclient.transport.dto.ReplyResponseDto
import com.dmzs.datawatchclient.transport.dto.SessionDto
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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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

    override suspend fun stats(): Result<StatsDto> = request {
        client.get("${profile.baseUrl}/api/stats") {
            bearer()?.let { header(HttpHeaders.Authorization, it) }
        }.body()
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
