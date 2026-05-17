package com.dmzs.datawatchclient.auto.voice

import com.dmzs.datawatchclient.auto.AutoServiceLocator
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.flow.first

/** Recognized voice commands. */
public enum class VoiceCommand {
    // Original commands
    STATUS,
    REPORT,
    CANCEL,
    REFRESH,
    // BL303-A4: new commands
    CREATE_SESSION,
    APPROVE_GATE,
    WHAT_FAILED,
    LIST_AUTOMATA,
    PAUSE_SESSION,
    KILL_SESSION,
    SERVER_STATUS,
    SWITCH_SERVER,
    COST_REPORT,
    MEMORY_RECALL,
    UNKNOWN,
}

/**
 * A parsed voice command with optional extracted arguments.
 * [serverName] is set when the command is server-addressed (e.g. "Trent status").
 * [topic] is the subject for MEMORY_RECALL or the session name for PAUSE/KILL.
 */
public data class ParsedVoiceCommand(
    val command: VoiceCommand,
    val serverName: String? = null,
    val topic: String? = null,
)

/** Parses raw voice input text to a [ParsedVoiceCommand]. */
public fun parseVoiceCommand(input: String): VoiceCommand = parseVoiceCommandFull(input).command

/** Full parse returning server name and topic arguments. */
public fun parseVoiceCommandFull(input: String): ParsedVoiceCommand {
    val lower = input.lowercase().trim()
    // Server-name routing: "status of Trent", "Trent status"
    val serverName = extractServerName(lower)
    return when {
        lower.contains("create session") || lower.contains("new session") ||
            lower.contains("start session") ->
            ParsedVoiceCommand(VoiceCommand.CREATE_SESSION, serverName)

        lower.contains("approve gate") || lower.contains("approve guardrail") ||
            (lower.contains("approve") && !lower.contains("approval")) ->
            ParsedVoiceCommand(VoiceCommand.APPROVE_GATE, serverName)

        lower.contains("what failed") || lower.contains("what's broken") ||
            lower.contains("show errors") || lower.contains("what broke") ->
            ParsedVoiceCommand(VoiceCommand.WHAT_FAILED, serverName)

        lower.contains("list automata") || lower.contains("show automata") ||
            lower.contains("automata status") || lower.contains("automata overview") ->
            ParsedVoiceCommand(VoiceCommand.LIST_AUTOMATA, serverName)

        lower.contains("pause session") || lower.contains("pause ") ->
            ParsedVoiceCommand(VoiceCommand.PAUSE_SESSION, serverName, extractTopic(lower, "pause"))

        lower.contains("kill session") || lower.contains("stop session") ||
            lower.contains("abort session") ->
            ParsedVoiceCommand(VoiceCommand.KILL_SESSION, serverName, extractTopic(lower, "kill", "stop", "abort"))

        lower.contains("cost report") || lower.contains("how much") ||
            lower.contains("spending") || lower.contains("cost summary") ->
            ParsedVoiceCommand(VoiceCommand.COST_REPORT, serverName)

        lower.contains("remember what") || lower.contains("what do you know") ||
            lower.contains("recall ") || lower.contains("memory ") ->
            ParsedVoiceCommand(VoiceCommand.MEMORY_RECALL, serverName, extractTopic(lower, "recall", "about", "know about"))

        lower.contains("switch to ") || lower.contains("use server") ||
            (lower.contains("switch") && serverName != null) ->
            ParsedVoiceCommand(VoiceCommand.SWITCH_SERVER, serverName)

        (lower.contains("status of ") || lower.contains("status")) && serverName != null ->
            ParsedVoiceCommand(VoiceCommand.SERVER_STATUS, serverName)

        lower.contains("status") ->
            ParsedVoiceCommand(VoiceCommand.STATUS, serverName)

        lower.contains("report") || lower.contains("summary") ->
            ParsedVoiceCommand(VoiceCommand.REPORT, serverName)

        lower.contains("cancel") || lower.contains("abort") ->
            ParsedVoiceCommand(VoiceCommand.CANCEL, serverName)

        lower.contains("refresh") || lower.contains("sync") ->
            ParsedVoiceCommand(VoiceCommand.REFRESH, serverName)

        else -> ParsedVoiceCommand(VoiceCommand.UNKNOWN, serverName)
    }
}

/**
 * Fuzzy server-name extraction. Loads profile display names and finds one
 * within Levenshtein distance ≤ [MAX_EDIT_DIST] from any word in the input.
 * Returns the matched profile display name, or null if no match.
 *
 * This is a pure-string operation — the profiles must be passed in by the
 * caller to avoid coupling this function to the coroutine context.
 */
public fun resolveServerName(input: String, profileNames: List<String>): String? {
    val words = input.lowercase().split(Regex("\\s+"))
    for (name in profileNames) {
        val nameLower = name.lowercase()
        // Exact word match first (fast path)
        if (words.any { it == nameLower }) return name
        // Fuzzy match: any word within edit distance cap
        if (words.any { levenshtein(it, nameLower) <= MAX_EDIT_DIST }) return name
    }
    return null
}

private fun extractServerName(lower: String): String? = null // resolved async via resolveServerName

private fun extractTopic(lower: String, vararg prefixes: String): String? {
    for (prefix in prefixes) {
        val idx = lower.indexOf(prefix)
        if (idx >= 0) {
            val after = lower.substring(idx + prefix.length).trim()
            if (after.isNotBlank()) return after
        }
    }
    return null
}

/** Iterative Levenshtein distance capped at [MAX_EDIT_DIST] + 1 for efficiency. */
public fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val dp = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        var prev = dp[0]
        dp[0] = i + 1
        for (j in b.indices) {
            val temp = dp[j + 1]
            dp[j + 1] = if (a[i] == b[j]) prev
            else minOf(prev, dp[j], dp[j + 1]) + 1
            prev = temp
        }
    }
    return dp[b.length]
}

private const val MAX_EDIT_DIST: Int = 2

/**
 * Builds a short ≤15s spoken status summary from cached session + stats data.
 * Designed for TTS readout in Android Auto.
 */
public suspend fun buildStatusSummary(): StatusSummary {
    return runCatching {
        val activeId = AutoServiceLocator.activeServerStore.get()
            ?: return@runCatching StatusSummary.noServer()
        val profiles = AutoServiceLocator.profileRepository.observeAll().first()
        val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
            ?: return@runCatching StatusSummary.noServer()
        val stats = AutoServiceLocator.transportFor(profile).stats().getOrNull()
            ?: return@runCatching StatusSummary.noServer()
        val cpuPct = stats.cpuLoad1?.let { load ->
            stats.cpuCores?.let { cores ->
                if (cores > 0) (load / cores * PCT_MULTIPLIER).toInt() else null
            }
        }
        val memPct = stats.memTotal?.let { total ->
            if (total > 0) {
                stats.memUsed?.let { used -> (used.toDouble() / total * PCT_MULTIPLIER).toInt() }
            } else {
                null
            }
        }
        StatusSummary(
            serverName = profile.displayName,
            running = stats.sessionsRunning,
            waiting = stats.sessionsWaiting,
            errors = 0,
            cpuPct = cpuPct,
            memPct = memPct,
        )
    }.getOrElse { StatusSummary.error() }
}

/**
 * Builds a spoken report for WHAT_FAILED — reads the most-recently blocked
 * session's guardrail verdicts aloud.
 */
public suspend fun buildWhatFailedReport(): String {
    return runCatching {
        val profile = run {
            val activeId = AutoServiceLocator.activeServerStore.get()
            val profiles = AutoServiceLocator.profileRepository.observeAll().first()
            profiles.firstOrNull { it.id == activeId && it.enabled }
                ?: profiles.firstOrNull { it.enabled }
        } ?: return@runCatching "No enabled server configured."
        val transport = AutoServiceLocator.transportFor(profile)
        val sessions = transport.listSessions().getOrNull() ?: return@runCatching "Could not reach ${profile.displayName}."
        val blocked = sessions.filter { it.state == SessionState.Error || it.state == SessionState.Waiting }
            .firstOrNull() ?: return@runCatching "No failed or waiting sessions on ${profile.displayName}."
        val telem = transport.getSessionTelemetry(blocked.id).getOrNull()
        val blocks = telem?.guardrailVerdicts?.filter { it.outcome == "block" }
        return@runCatching when {
            blocks.isNullOrEmpty() ->
                "${blocked.name ?: blocked.id} is ${blocked.state.name.lowercase()} but no guardrail block found."
            else -> buildString {
                append("${blocked.name ?: "Session"} is blocked. ")
                blocks.take(2).forEach { v ->
                    append("${v.guardrail}: ${v.summary.take(SPOKEN_SUMMARY_CHARS)}. ")
                }
            }
        }
    }.getOrElse { "Error building report." }
}

private const val PCT_MULTIPLIER: Int = 100
private const val SPOKEN_SUMMARY_CHARS: Int = 80

public data class StatusSummary(
    val serverName: String,
    val running: Int,
    val waiting: Int,
    val errors: Int,
    val cpuPct: Int?,
    val memPct: Int?,
) {
    public companion object {
        public fun noServer(): StatusSummary = StatusSummary("", 0, 0, 0, null, null)
        public fun error(): StatusSummary = StatusSummary("", -1, -1, -1, null, null)
    }

    public val isError: Boolean get() = running == -1
    public val noServer: Boolean get() = serverName.isEmpty() && running == 0
}
