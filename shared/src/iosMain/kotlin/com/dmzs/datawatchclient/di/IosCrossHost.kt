package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One caller line of a cross-host envelope; [cross] = `peer:kind:id` attribution (🔗 cross). */
public data class IosCrossHostCaller(
    val caller: String,
    val detail: String,
    val cross: Boolean,
)

/** One envelope row (PWA showCrossHostView): id · kind · label, listen + outbound summaries, callers. */
public data class IosCrossHostEnvelope(
    val id: String,
    val kind: String,
    val label: String,
    val listen: String,
    val outbound: String,
    val callers: List<IosCrossHostCaller>,
)

public data class IosCrossHostPeer(
    val peer: String,
    val envelopes: List<IosCrossHostEnvelope>,
)

/**
 * Observer "↔ Cross-host view" (PWA `showCrossHostView`): GET
 * /api/observer/envelopes/all-peers grouped by peer. Callbacks fire off the
 * main thread.
 */
public object IosCrossHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun load(
        profile: ServerProfile,
        onSuccess: (List<IosCrossHostPeer>) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            IosServiceLocator.transportFor(profile).fetchCrossHostEnvelopesJson().fold(
                onSuccess = { obj -> onSuccess(parse(obj)) },
                onFailure = { onError(it.message ?: "load failed") },
            )
        }
    }

    /** PWA: a caller is cross-host when it has at least three `:`-separated parts. */
    public fun isCross(caller: String): Boolean = caller.contains(':') && caller.split(':').size >= 3

    private fun parse(obj: JsonObject): List<IosCrossHostPeer> {
        val byPeer: JsonObject = obj["by_peer"] as? JsonObject ?: return emptyList()
        return byPeer.map { (peer, envs) ->
            val list: List<IosCrossHostEnvelope> =
                (envs as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { e -> envelope(e) }
            IosCrossHostPeer(peer = peer, envelopes = list)
        }
    }

    private fun envelope(e: JsonObject): IosCrossHostEnvelope {
        val callers: List<IosCrossHostCaller> =
            (e["callers"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { c ->
                val caller: String = c.s("caller").ifEmpty { "?" }
                IosCrossHostCaller(
                    caller = caller,
                    detail = "${c.s("caller_kind")} · ${c.s("conns").ifEmpty { "0" }} conns",
                    cross = isCross(caller),
                )
            }
        return IosCrossHostEnvelope(
            id = e.s("id").ifEmpty { "?" },
            kind = e.s("kind"),
            label = e.s("label"),
            listen = addrs(e, "listen_addrs", "ip", "port"),
            outbound = addrs(e, "outbound_edges", "target_ip", "target_port"),
            callers = callers,
        )
    }

    private fun addrs(
        e: JsonObject,
        key: String,
        ip: String,
        port: String,
    ): String =
        (e[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.joinToString(", ") { "${it.s(ip)}:${it.s(port)}" }

    private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()
}
