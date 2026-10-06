package com.dmzs.datawatchclient.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelBridgeFormatTest {
    private fun lines(json: String) = ChannelBridgeFormat.lines(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun goReadyWithModes() {
        val l = lines("""{"kind":"go","path":"/usr/bin/datawatch-channel","ready":true,"hint":"ok","stdio_enabled":true,"sse_enabled":true}""")
        assertEquals(
            listOf(
                ChannelBridgeLine("Bridge: Go ✓", "success"),
                ChannelBridgeLine("/usr/bin/datawatch-channel", "muted"),
                ChannelBridgeLine("MCP: stdio + SSE", "success"),
            ),
            l,
        )
    }

    @Test
    fun jsNotReadyShowsHintNodeAndStaleFiles() {
        val l =
            lines(
                """{"kind":"js","path":"","ready":false,"hint":"run setup","node_path":"/usr/bin/node",
                   "stale_mcp_json":[{"path":"/h/.mcp.json","missing_channel_js":"/x/channel.js"}]}""",
            )
        assertEquals(ChannelBridgeLine("Bridge: JS (fallback) ⚠", "warning"), l[0])
        assertEquals(ChannelBridgeLine("run setup", "warning"), l[1])
        assertEquals(ChannelBridgeLine("node: /usr/bin/node", "muted"), l[2])
        assertEquals("Stale .mcp.json files (point at missing channel.js):", l[3].text)
        assertEquals("• /h/.mcp.json → /x/channel.js", l[4].text)
        assertEquals(6, l.size)
    }
}
