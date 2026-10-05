package com.dmzs.datawatchclient.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * One poll of the PWA 📡 compute-node live-detail modal (`computeShowDetail`):
 * either the pretty-printed `/api/compute/nodes/{name}/detail` JSON or the
 * server's reason it is unavailable (e.g. "no monitoring_endpoint configured").
 */
public data class ComputeNodeLiveDetail(
    val json: String?,
    val error: String?,
)

public object ComputeNodeLiveDetailLoader {
    /** PWA polls every 1 s while the modal is open. */
    public const val POLL_INTERVAL_MS: Long = 1_000L

    private val pretty: Json = Json { prettyPrint = true }

    public fun format(obj: JsonElement): String = pretty.encodeToString(JsonElement.serializer(), obj)

    public suspend fun load(
        transport: TransportClient,
        name: String,
    ): ComputeNodeLiveDetail {
        val r: Result<JsonElement> = transport.getComputeNodeDetailJson(name)
        val obj: JsonElement? = r.getOrNull()
        return if (obj != null) {
            ComputeNodeLiveDetail(json = format(obj), error = null)
        } else {
            ComputeNodeLiveDetail(json = null, error = r.exceptionOrNull()?.message ?: "detail unavailable")
        }
    }
}
