package com.dmzs.datawatchclient.diagnostics

/**
 * Strips secrets and infrastructure details from report text. Mandatory for
 * every crash / bug report (AGENT.md Security Rules: no tokens, server URLs or
 * IP addresses in crash reports). Errs on the side of over-redaction.
 *
 * Rules, applied in order:
 * 1. `Authorization:` / `Proxy-Authorization:` header values → `<redacted>`.
 * 2. `Bearer <token>` → `Bearer <redacted>`.
 * 3. JSON Web Tokens (`eyJ….….…`) → `<token>`.
 * 4. Secret-named query parameters and key/value pairs (`token=`, `"password":`) → `<redacted>`.
 * 5. URLs: `scheme://[user@]host[:port]` → `<server>` (the path is kept).
 * 6. Host names: `host "x.y"`, `hostname=x.y`, `connect to x.y`, and `*.ts.net` / `.local` / `.lan` /
 *    `.internal` / `.home.arpa` names (with an optional `:port`) → `<server>`. A name followed
 *    by `.word` is a package (`kotlinx.coroutines.internal.X`), not a host, and is kept.
 * 7. IPv4 and IPv6 addresses → `<ip>`.
 * 8. Token-like runs: 32+ base64url/hex characters (`/` excluded so file paths
 *    survive) containing both a letter and a digit → `<token>`. Canonical UUIDs are kept.
 *
 * The output contains none of the patterns above, so redacting twice is a no-op.
 */
public object Redactor {
    public const val REDACTED: String = "<redacted>"
    public const val SERVER: String = "<server>"
    public const val IP: String = "<ip>"
    public const val TOKEN: String = "<token>"

    private val authHeader =
        Regex("""(?i)\b((?:proxy-)?authorization)(\s*["']?\s*[:=]\s*)[^\r\n]*""")
    private val bearer = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""")
    private val jwt = Regex("""\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*""")
    private val secretParam =
        Regex(
            """(?i)(?<![A-Za-z0-9])((?:access_|refresh_|api_|auth_|id_)?""" +
                """(?:token|key|secret|password|passwd|pwd|apikey|auth))""" +
                """(["']?\s*[:=]\s*["']?)[^\s"'&,;}]+""",
        )
    private val url =
        Regex("""(?i)\b[a-z][a-z0-9+.-]*://(?:[^\s/?#@]*@)?(?:\[[0-9a-f:.]+\]|[^\s/?#:\[\]"'<>]+)(?::\d+)?""")
    private val namedHost =
        Regex(
            """(?i)\b((?:host(?:name)?|connect(?:ed|ing)?\s+to)\s*[:=]?\s*["']?/?)""" +
                """([a-z0-9-]+(?:\.[a-z0-9-]+)+)(?::\d+)?""",
        )
    private val privateTldHost =
        Regex("""(?i)\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:ts\.net|local|lan|internal|home\.arpa)\b(?!\.\w)(?::\d+)?""")
    private val ipv4 = Regex("""(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.]*\d)""")
    private val ipv6 =
        Regex(
            """(?<![\w:.])(?:(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}""" +
                """|(?:[0-9a-fA-F]{1,4}:){1,6}:(?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?""" +
                """|::(?:[0-9a-fA-F]{1,4}:){0,5}[0-9a-fA-F]{1,4})(?![\w:])""",
        )
    private val tokenRun =
        Regex(
            """(?<![A-Za-z0-9+_-])""" +
                // Canonical UUIDs are identifiers (MetricKit binary UUIDs), not secrets.
                """(?![0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""" +
                """(?![A-Za-z0-9+_-]))""" +
                """(?=[A-Za-z0-9+_-]*\d)(?=[A-Za-z0-9+_-]*[A-Za-z])[A-Za-z0-9+_-]{32,}={0,2}""",
        )

    /** Returns [text] with every rule applied. */
    public fun redact(text: String): String {
        var out = text
        out = authHeader.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + REDACTED }
        out = bearer.replace(out) { "Bearer $REDACTED" }
        out = jwt.replace(out, TOKEN)
        out = secretParam.replace(out) { m -> m.groupValues[1] + m.groupValues[2] + REDACTED }
        out = url.replace(out, SERVER)
        out = namedHost.replace(out) { m -> m.groupValues[1] + SERVER }
        out = privateTldHost.replace(out, SERVER)
        out = ipv4.replace(out, IP)
        out = ipv6.replace(out, IP)
        out = tokenRun.replace(out, TOKEN)
        return out
    }
}
