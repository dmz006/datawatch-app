package com.dmzs.datawatchclient.transport

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** One rendered line of the MCP channel bridge card; [tone] = success / warning / muted / text. */
public data class ChannelBridgeLine(
    val text: String,
    val tone: String,
)

/**
 * `GET /api/channel/info` → the PWA `loadChannelBridge` lines, verbatim order:
 * Bridge kind + ready mark, path, not-ready hint, JS node path, `MCP: stdio + SSE`,
 * then the stale `.mcp.json` list with the cleanup command. Shared by the
 * Observer and Settings › About cards on Android and iOS.
 */
public object ChannelBridgeFormat {
    public fun lines(info: JsonObject): List<ChannelBridgeLine> {
        fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun bool(k: String): Boolean = (info[k] as? JsonPrimitive)?.booleanOrNull == true
        val out = mutableListOf<ChannelBridgeLine>()
        val go = str(info, "kind") == "go"
        val ready = bool("ready")
        out += ChannelBridgeLine("Bridge: ${if (go) "Go" else "JS (fallback)"} ${if (ready) "✓" else "⚠"}", if (go) "success" else "warning")
        str(info, "path")?.takeIf { it.isNotBlank() }?.let { out += ChannelBridgeLine(it, "muted") }
        if (!ready) str(info, "hint")?.takeIf { it.isNotBlank() }?.let { out += ChannelBridgeLine(it, "warning") }
        if (str(info, "kind") == "js") {
            str(info, "node_path")?.takeIf { it.isNotBlank() }?.let { out += ChannelBridgeLine("node: $it", "muted") }
        }
        val modes = listOfNotNull(if (bool("stdio_enabled")) "stdio" else null, if (bool("sse_enabled")) "SSE" else null)
        if (modes.isNotEmpty()) out += ChannelBridgeLine("MCP: " + modes.joinToString(" + "), "success")
        val stale = (info["stale_mcp_json"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        if (stale.isNotEmpty()) {
            out += ChannelBridgeLine("Stale .mcp.json files (point at missing channel.js):", "warning")
            stale.forEach { e ->
                out += ChannelBridgeLine("• ${str(e, "path").orEmpty()} → ${str(e, "missing_channel_js").orEmpty()}", "text")
            }
            out += ChannelBridgeLine("Run datawatch channel cleanup-stale-mcp-json to remove.", "muted")
        }
        return out
    }
}
