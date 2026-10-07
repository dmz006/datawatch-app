package com.dmzs.datawatchclient.transport

/**
 * Short, user-facing text for a failed request: the server's own
 * `{"error":"…"}` message when the transport error carries one (e.g.
 * "tailscale not configured"), else the error message, else [fallback].
 * Keeps raw HTTP dumps ("Client request(GET https://…) invalid: 404 …") off
 * the screen.
 */
public object ErrorText {
    private val serverError = Regex("\"error\"\\s*:\\s*\"([^\"]*)\"")
    private val httpStatus = Regex(":\\s*(\\d{3})\\b")

    public fun of(
        e: Throwable?,
        fallback: String,
    ): String {
        val m = e?.message.orEmpty()
        serverError.find(m)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }
        if (m.contains("Client request(") || m.contains("Server error(")) {
            val code = httpStatus.find(m)?.groupValues?.get(1)
            return if (code == "404") "Not available on this server version." else "$fallback (HTTP ${code ?: "?"})"
        }
        return m.ifBlank { fallback }
    }
}
