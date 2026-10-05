package com.dmzs.datawatchclient.transport.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One persisted debate round inside `GET /api/council/runs/{id}` —
 * `{index, responses: {persona → text}}` (server `council.Round`).
 */
@Serializable
public data class CouncilRoundDto(
    val index: Int = 0,
    val responses: Map<String, String> = emptyMap(),
)

/**
 * One event from `GET /api/council/runs/{id}/events` (server
 * `internal/council` EventFn → SSEHub topic `council:<run_id>`).
 *
 * Event types and payloads (every payload also carries `run_id`):
 *  - `hello` / `close` — `{}` (connection opened / topic closed)
 *  - `run_started` — `{mode, personas[], max_parallel, rounds_total}`
 *  - `round_started` / `round_completed` — `{round}`
 *  - `persona_responding` — `{round, persona}`
 *  - `persona_response` — `{round, persona, text, session_id}`
 *  - `persona_error` — `{round, persona, error, session_id}`
 *  - `synthesis_started` — `{}`
 *  - `run_completed` — `{consensus, dissent, finished_at}`
 *  - `run_cancelled` — `{finished_at}`
 *
 * Flat (no sealed hierarchy) so Swift sees one class with plain fields.
 */
public data class CouncilRunEvent(
    val type: String,
    val runId: String = "",
    val round: Int = 0,
    val persona: String = "",
    val text: String = "",
    val error: String = "",
    val mode: String = "",
    val personas: List<String> = emptyList(),
    val roundsTotal: Int = 0,
    val maxParallel: Int = 0,
    val consensus: String = "",
    val dissent: String = "",
    val finishedAt: String = "",
    val sessionId: String = "",
) {
    /** True for the events after which the server publishes nothing more for the run. */
    val isTerminal: Boolean
        get() = type == TYPE_RUN_COMPLETED || type == TYPE_RUN_CANCELLED || type == TYPE_CLOSE

    public companion object {
        public const val TYPE_HELLO: String = "hello"
        public const val TYPE_RUN_STARTED: String = "run_started"
        public const val TYPE_ROUND_STARTED: String = "round_started"
        public const val TYPE_PERSONA_RESPONDING: String = "persona_responding"
        public const val TYPE_PERSONA_RESPONSE: String = "persona_response"
        public const val TYPE_PERSONA_ERROR: String = "persona_error"
        public const val TYPE_ROUND_COMPLETED: String = "round_completed"
        public const val TYPE_SYNTHESIS_STARTED: String = "synthesis_started"
        public const val TYPE_RUN_COMPLETED: String = "run_completed"
        public const val TYPE_RUN_CANCELLED: String = "run_cancelled"
        public const val TYPE_CLOSE: String = "close"
    }
}

/** Decodes one SSE frame (`event:` + `data:` JSON) into a [CouncilRunEvent]. */
public object CouncilRunEventParser {
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Never throws: malformed JSON yields an event with only [type] set. */
    public fun parse(
        event: String,
        data: String,
    ): CouncilRunEvent {
        val obj: JsonObject =
            runCatching { json.parseToJsonElement(data.ifBlank { "{}" }) as? JsonObject }
                .getOrNull() ?: JsonObject(emptyMap())
        return CouncilRunEvent(
            type = event,
            runId = str(obj["run_id"]),
            round = int(obj["round"]),
            persona = str(obj["persona"]),
            text = str(obj["text"]),
            error = str(obj["error"]),
            mode = str(obj["mode"]),
            personas = strList(obj["personas"]),
            roundsTotal = int(obj["rounds_total"]),
            maxParallel = int(obj["max_parallel"]),
            consensus = str(obj["consensus"]),
            dissent = str(obj["dissent"]),
            finishedAt = str(obj["finished_at"]),
            sessionId = str(obj["session_id"]),
        )
    }

    private fun str(el: JsonElement?): String {
        val p: JsonPrimitive = el as? JsonPrimitive ?: return ""
        if (p is kotlinx.serialization.json.JsonNull) return ""
        return p.content
    }

    private fun int(el: JsonElement?): Int {
        val p: JsonPrimitive = el as? JsonPrimitive ?: return 0
        return p.content.toIntOrNull() ?: p.content.toDoubleOrNull()?.toInt() ?: 0
    }

    private fun strList(el: JsonElement?): List<String> {
        val arr: JsonArray = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.content }
    }
}
