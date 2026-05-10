package com.dmzs.datawatchclient.auto.voice

import com.dmzs.datawatchclient.auto.AutoServiceLocator
import kotlinx.coroutines.flow.first

/** Recognized voice commands. */
public enum class VoiceCommand { STATUS, REPORT, CANCEL, REFRESH, UNKNOWN }

/** Parses raw voice input text to a [VoiceCommand]. */
public fun parseVoiceCommand(input: String): VoiceCommand {
    val lower = input.lowercase().trim()
    return when {
        lower.contains("status") -> VoiceCommand.STATUS
        lower.contains("report") || lower.contains("summary") -> VoiceCommand.REPORT
        lower.contains("cancel") || lower.contains("abort") -> VoiceCommand.CANCEL
        lower.contains("refresh") || lower.contains("sync") -> VoiceCommand.REFRESH
        else -> VoiceCommand.UNKNOWN
    }
}

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

private const val PCT_MULTIPLIER: Int = 100

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
