@file:Suppress("MagicNumber")
package com.dmzs.datawatchclient.auto

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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

/**
 * Voice input screen for Android Auto. Handles the full voice reply flow in a
 * single screen (no separate TranscriptionConfirmScreen push) to keep the
 * navigation stack within the Car App Library 5-screen limit.
 *
 * States:
 *   LISTENING  — ASR active, auto-starts on entry
 *   ERROR      — recognition failed, shows Retry / Cancel
 *   CONFIRMED  — transcript ready, TTS reads it aloud, user can Send or Retry
 *
 * Uses [CarContext.applicationContext] for [SpeechRecognizer] and [TextToSpeech]
 * binding — the [CarContext] itself is not suitable for service binding.
 */
public class VoiceRecordingScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
) : Screen(carContext) {

    private sealed class State {
        object Listening : State()
        data class Error(val msg: String) : State()
        data class Confirmed(val transcript: String) : State()
    }

    private var state: State = State.Listening
    private var recognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val tts: TextToSpeech = TextToSpeech(carContext.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) tts.language = java.util.Locale.getDefault()
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Only auto-start listening on first entry, not when returning from a CONFIRMED state.
                if (state is State.Listening) startListening()
            }

            override fun onStop(owner: LifecycleOwner) {
                recognizer?.cancel()
                tts.stop()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                recognizer?.destroy()
                recognizer = null
                tts.stop()
                tts.shutdown()
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template = when (val s = state) {
        is State.Listening -> buildListeningTemplate()
        is State.Error -> buildErrorTemplate(s.msg)
        is State.Confirmed -> buildConfirmTemplate(s.transcript)
    }

    private fun buildListeningTemplate(): Template =
        MessageTemplate.Builder("Listening…\nSpeak your reply")
            .setTitle(sessionTitle)
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("Cancel")
                    .setOnClickListener {
                        recognizer?.cancel()
                        screenManager.pop()
                    }
                    .build()
            )
            .build()

    private fun buildErrorTemplate(msg: String): Template =
        MessageTemplate.Builder(msg.ifEmpty { "Could not hear — tap Retry" })
            .setTitle(sessionTitle)
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("Retry")
                    .setOnClickListener { startListening() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Cancel")
                    .setOnClickListener { screenManager.pop() }
                    .build()
            )
            .build()

    private fun buildConfirmTemplate(transcript: String): Template {
        val voiceIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)
        ).build()
        return MessageTemplate.Builder(transcript.ifBlank { "No transcription" })
            .setTitle("$sessionTitle · Voice")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Listen")
                            .setIcon(voiceIcon)
                            .setOnClickListener {
                                tts.speak(transcript, TextToSpeech.QUEUE_FLUSH, null, "dw-voice")
                            }
                            .build()
                    )
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Send")
                    .setOnClickListener { onSend(transcript) }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Retry")
                    .setOnClickListener { startListening() }
                    .build()
            )
            .build()
    }

    private fun startListening() {
        state = State.Listening
        recognizer?.destroy()
        recognizer = null
        invalidate()

        val appCtx = carContext.applicationContext

        if (!SpeechRecognizer.isRecognitionAvailable(appCtx)) {
            state = State.Error("Speech recognition not available — check that Google app is installed")
            invalidate()
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(appCtx).also { rec ->
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        state = State.Confirmed(text)
                        invalidate()
                        // Auto-play TTS so the user can confirm what was heard.
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dw-voice")
                    } else {
                        state = State.Error("Nothing heard — tap Retry")
                        invalidate()
                    }
                }

                override fun onError(error: Int) {
                    state = State.Error(speechErrorString(error))
                    invalidate()
                }

                override fun onReadyForSpeech(params: Bundle) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle) {}
                override fun onEvent(eventType: Int, params: Bundle) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            rec.startListening(intent)
        }
    }

    private fun onSend(transcript: String) {
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
                            CarToast.makeText(carContext, "Sent", CarToast.LENGTH_SHORT).show()
                            screenManager.pop()
                        },
                        onFailure = { err ->
                            CarToast.makeText(
                                carContext,
                                "Send failed: ${err.message?.take(ERROR_MSG_CHARS) ?: "unknown"}",
                                CarToast.LENGTH_LONG,
                            ).show()
                        },
                    )
            }
        }
    }

    private companion object {
        const val ERROR_MSG_CHARS = 40

        fun speechErrorString(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone error — check audio"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required — grant in phone settings"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error — try again"
            SpeechRecognizer.ERROR_NO_MATCH -> "Nothing matched — speak clearly and retry"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy — retry"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected — tap Retry"
            else -> "Recognition error ($error) — tap Retry"
        }
    }
}
