package com.dmzs.datawatchclient.auto

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.transport.dto.GuardrailVerdictDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shows all active guardrail block verdicts for a session with Approve and Kill actions.
 */
public class BlockDetailsScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionName: String,
    private val verdicts: List<GuardrailVerdictDto>,
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var focusRequest: AudioFocusRequest? = null
    private val audioManager = carContext.applicationContext.getSystemService(AudioManager::class.java)

    private val tts: TextToSpeech = TextToSpeech(carContext.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = java.util.Locale.getDefault()
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) { abandonAudioFocus() }
                @Deprecated("replaced by onStop") override fun onError(utteranceId: String) { abandonAudioFocus() }
                override fun onStop(utteranceId: String, interrupted: Boolean) { abandonAudioFocus() }
            })
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                tts.stop()
                tts.shutdown()
                abandonAudioFocus()
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val body = buildVerdictBody()

        val voiceIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)
        ).build()

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Listen")
                    .setIcon(voiceIcon)
                    .setOnClickListener { speakWithFocus(body) }
                    .build()
            )
            .build()

        return MessageTemplate.Builder(body)
            .setTitle("$sessionName · Blocked")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .addAction(
                Action.Builder()
                    .setTitle("Approve Gate")
                    .setBackgroundColor(CarColor.GREEN)
                    .setOnClickListener { onApproveGate() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Kill Session")
                    .setOnClickListener {
                        CarToast.makeText(carContext, "Kill — use session detail", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
                    }
                    .build()
            )
            .build()
    }

    private fun buildVerdictBody(): String {
        if (verdicts.isEmpty()) return "No active blocks"
        return verdicts
            .filter { it.outcome == "block" }
            .ifEmpty { verdicts }
            .joinToString("\n\n") { verdict ->
                "⚠ ${verdict.guardrail}\n${verdict.summary.take(SUMMARY_CHARS)}"
            }
            .take(BODY_CHAR_LIMIT)
    }

    private fun speakWithFocus(text: String) {
        abandonAudioFocus()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        audioManager.requestAudioFocus(req)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dw-block")
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun onApproveGate() {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: return@runCatching
                AutoServiceLocator.transportFor(profile).runSessionGuardrail(sessionId).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Gate approval submitted", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
                    },
                    onFailure = { err ->
                        CarToast.makeText(carContext, "Approve failed: ${err.message?.take(ERROR_MSG_CHARS)}", CarToast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private companion object {
        const val BODY_CHAR_LIMIT = 500
        const val SUMMARY_CHARS = 120
        const val ERROR_MSG_CHARS = 40
    }
}
