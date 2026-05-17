package com.dmzs.datawatchclient.wear

/**
 * BL303-W5 — pure Kotlin (no Android deps) voice query classification and
 * natural-language reply building for watch-initiated status queries.
 *
 * Three recognised intents:
 *  - STATUS      → "status", "how's it going", "update"
 *  - RUNNING     → "what's running", "list", "show", "running sessions"
 *  - BLOCKS      → "any blocks", "blocked", "guardrail"
 *
 * Server-name routing uses Levenshtein distance ≤ 2 so "prod" matches "production"
 * prefixes if within two edits.
 */
object VoiceQueryDispatcher {

    enum class QueryIntent { STATUS, RUNNING, BLOCKS, UNKNOWN }

    data class SessionSummary(val title: String, val state: String)

    fun classifyQuery(text: String): QueryIntent {
        val lower = text.lowercase().trim()
        return when {
            lower.contains("block") || lower.contains("guardrail") -> QueryIntent.BLOCKS
            lower.contains("run") || lower.contains("list") || lower.contains("show") ||
                lower.contains("what") -> QueryIntent.RUNNING
            lower.contains("status") || lower.contains("update") ||
                lower.contains("how") || lower.contains("summary") -> QueryIntent.STATUS
            else -> QueryIntent.UNKNOWN
        }
    }

    fun buildReply(
        intent: QueryIntent,
        running: Int,
        waiting: Int,
        error: Int,
        sessions: List<SessionSummary>,
        serverName: String,
    ): String {
        val server = if (serverName.isNotBlank()) " on $serverName" else ""
        return when (intent) {
            QueryIntent.STATUS -> {
                val parts = buildList {
                    if (running > 0) add("$running running")
                    if (waiting > 0) add("$waiting waiting")
                    if (error > 0) add("$error error")
                    if (isEmpty()) add("no active sessions")
                }
                "${parts.joinToString(", ")}$server"
            }
            QueryIntent.RUNNING -> {
                val runningSessions = sessions.filter {
                    it.state.lowercase() == "running"
                }
                if (runningSessions.isEmpty()) {
                    "no running sessions$server"
                } else {
                    val names = runningSessions.take(3).joinToString(", ") { it.title }
                    val suffix = if (runningSessions.size > 3) " and ${runningSessions.size - 3} more" else ""
                    "$names$suffix$server"
                }
            }
            QueryIntent.BLOCKS -> {
                val blocked = sessions.filter {
                    it.state.lowercase().contains("block") ||
                        it.state.lowercase() == "waiting"
                }
                if (blocked.isEmpty()) {
                    "no blocks$server"
                } else {
                    val names = blocked.take(2).joinToString(", ") { it.title }
                    val suffix = if (blocked.size > 2) " and ${blocked.size - 2} more blocked" else " blocked"
                    "$names$suffix$server"
                }
            }
            QueryIntent.UNKNOWN -> "say status, running, or blocks"
        }
    }

    /**
     * Returns true when [candidate] is within [maxDistance] Levenshtein edits of [target].
     * Used for fuzzy server-name matching in voice queries.
     */
    fun fuzzyMatchesServer(candidate: String, target: String, maxDistance: Int = 2): Boolean {
        val a = candidate.lowercase().trim()
        val b = target.lowercase().trim()
        if (a == b) return true
        if (a.isEmpty() || b.isEmpty()) return false
        return levenshtein(a, b) <= maxDistance
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[m][n]
    }
}
