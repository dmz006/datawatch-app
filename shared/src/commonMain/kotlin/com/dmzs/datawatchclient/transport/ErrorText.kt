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
    private val bodyText = Regex("Text: \"(.*)\"\\s*$", RegexOption.DOT_MATCHES_ALL)

    public fun of(
        e: Throwable?,
        fallback: String,
    ): String {
        val m = e?.message.orEmpty()
        serverError.find(m)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let { return it }
        if (m.contains("Client request(") || m.contains("Server error(")) {
            // Plain-text server body (Go http.Error), e.g. `registry "community" not connected …`.
            bodyText.find(m)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() && !it.startsWith("{") && !it.contains("page not found") }
                ?.let { return it }
            val code = httpStatus.find(m)?.groupValues?.get(1)
            return if (code == "404") "Not available on this server version." else "$fallback (HTTP ${code ?: "?"})"
        }
        return m.ifBlank { fallback }
    }
}
