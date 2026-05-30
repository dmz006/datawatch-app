package com.dmzs.datawatchclient.auto.voice

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.auto.AutoServiceLocator
import com.dmzs.datawatchclient.auto.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android Auto screen that displays a voice-ready status summary.
 * The Car App runtime reads this text aloud via TTS when the driver
 * uses Google Assistant / "Hey Google, ask DataWatch for status".
 */
public class VoiceStatusScreen(
    carContext: CarContext,
    private val command: VoiceCommand = VoiceCommand.STATUS,
) : Screen(carContext) {

    private var statusText: String = carContext.getString(R.string.auto_voice_loading)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
        scope.launch {
            statusText = when (command) {
                VoiceCommand.STATUS, VoiceCommand.UNKNOWN -> {
                    val summary = buildStatusSummary()
                    when {
                        summary.isError -> carContext.getString(R.string.auto_voice_error)
                        summary.noServer -> carContext.getString(R.string.auto_voice_no_server)
                        else -> buildStatusString(summary)
                    }
                }
                VoiceCommand.REFRESH -> {
                    runCatching {
                        val activeId = AutoServiceLocator.activeServerStore.get()
                        val profiles = AutoServiceLocator.profileRepository.observeAll().first()
                        val profile = profiles.firstOrNull { it.id == activeId && it.enabled }
                        if (profile != null) {
                            AutoServiceLocator.transportFor(profile).stats()
                        }
                    }
                    carContext.getString(R.string.auto_voice_refresh_done)
                }
                VoiceCommand.CANCEL -> carContext.getString(R.string.auto_voice_cancel_hint)
                VoiceCommand.REPORT -> {
                    val summary = buildStatusSummary()
                    if (summary.isError || summary.noServer) {
                        carContext.getString(R.string.auto_voice_no_report)
                    } else {
                        buildStatusString(summary)
                    }
                }
                VoiceCommand.WHAT_FAILED -> buildWhatFailedReport()
                VoiceCommand.SERVER_STATUS -> {
                    val summary = buildStatusSummary()
                    if (summary.isError || summary.noServer) carContext.getString(R.string.auto_voice_no_server)
                    else buildStatusString(summary)
                }
                VoiceCommand.COST_REPORT -> "Cost reporting is coming soon."
                VoiceCommand.MEMORY_RECALL -> "Memory recall is coming soon."
                VoiceCommand.CREATE_SESSION,
                VoiceCommand.APPROVE_GATE,
                VoiceCommand.LIST_AUTOMATA,
                VoiceCommand.PAUSE_SESSION,
                VoiceCommand.KILL_SESSION,
                VoiceCommand.SWITCH_SERVER -> "Use the datawatch screen to complete this action."
            }
            invalidate()
        }
    }

    private fun buildStatusString(s: StatusSummary): String {
        val parts = mutableListOf<String>()
        parts += carContext.getString(R.string.auto_voice_status_server, s.serverName)
        parts += carContext.getString(R.string.auto_voice_status_sessions, s.running, s.waiting)
        if (s.errors > 0) parts += carContext.getString(R.string.auto_voice_status_errors, s.errors)
        s.cpuPct?.let { parts += carContext.getString(R.string.auto_voice_status_cpu, it) }
        s.memPct?.let { parts += carContext.getString(R.string.auto_voice_status_mem, it) }
        return parts.joinToString(". ")
    }

    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(statusText)
            .setTitle(carContext.getString(R.string.auto_voice_title))
            .setHeaderAction(Action.BACK)
            .build()
    }
}
