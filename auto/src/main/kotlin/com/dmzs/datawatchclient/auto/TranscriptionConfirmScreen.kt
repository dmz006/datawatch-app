package com.dmzs.datawatchclient.auto

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows the Whisper transcription result and lets the user confirm (send) or retry.
 */
public class TranscriptionConfirmScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
    private val transcript: String,
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val tts: TextToSpeech = TextToSpeech(carContext) { status ->
        if (status == TextToSpeech.SUCCESS) tts.language = java.util.Locale.getDefault()
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Auto-play TTS if preference enabled (default true)
                val prefs = carContext.getSharedPreferences("auto_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean(PREF_AUTO_PLAY_TRANSCRIPTION, true) && transcript.isNotBlank()) {
                    tts.speak(transcript, TextToSpeech.QUEUE_FLUSH, null, "dw-transcription")
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                tts.stop()
                tts.shutdown()
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val cancelAction = Action.Builder()
            .setTitle("Cancel")
            .setOnClickListener {
                // Pop both TranscriptionConfirmScreen and VoiceRecordingScreen
                screenManager.pop()
                screenManager.pop()
            }
            .build()

        val ttsIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_auto_info)
        ).build()

        val ttsAction = Action.Builder()
            .setIcon(ttsIcon)
            .setOnClickListener { tts.speak(transcript, TextToSpeech.QUEUE_FLUSH, null, "dw-transcription") }
            .build()

        return MessageTemplate.Builder(transcript.ifBlank { "No transcription" })
            .setTitle("$sessionTitle · Voice")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(cancelAction)
                    .addAction(ttsAction)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("✓ Send")
                    .setOnClickListener { onSend() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("🎤 Retry")
                    .setOnClickListener { screenManager.pop() /* back to VoiceRecordingScreen */ }
                    .build()
            )
            .build()
    }

    private fun onSend() {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: run {
                    CarToast.makeText(carContext, "No active server", CarToast.LENGTH_SHORT).show()
                    return@runCatching
                }
                AutoServiceLocator.transportFor(profile)
                    .replyToSession(sessionId, "$transcript\r")
                    .fold(
                        onSuccess = {
                            withContext(Dispatchers.Main) {
                                CarToast.makeText(carContext, "Sent", CarToast.LENGTH_SHORT).show()
                                // Pop back to session detail (past TranscriptionConfirmScreen + VoiceRecordingScreen)
                                screenManager.pop()
                                screenManager.pop()
                            }
                        },
                        onFailure = { err ->
                            withContext(Dispatchers.Main) {
                                CarToast.makeText(
                                    carContext,
                                    "Send failed: ${err.message?.take(ERROR_MSG_CHARS)}",
                                    CarToast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
            }
        }
    }

    private companion object {
        const val PREF_AUTO_PLAY_TRANSCRIPTION = "auto_play_transcription"
        const val ERROR_MSG_CHARS = 40
    }
}
