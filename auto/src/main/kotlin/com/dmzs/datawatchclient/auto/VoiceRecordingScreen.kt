@file:Suppress("MagicNumber")
package com.dmzs.datawatchclient.auto

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Voice input screen for Android Auto. Uses Android's built-in [SpeechRecognizer]
 * (Google ASR) rather than [androidx.car.app.media.CarAudioRecord] + Whisper, so it
 * works while driving without hitting the host's recording restriction.
 *
 * Recognition is automatic on start — user speaks, result goes to
 * [TranscriptionConfirmScreen] for a confirm-then-send step.
 */
public class VoiceRecordingScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
) : Screen(carContext) {

    private enum class State { LISTENING, ERROR }

    private var state = State.LISTENING
    private var errorMsg = ""
    private var recognizer: SpeechRecognizer? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                startListening()
            }

            override fun onStop(owner: LifecycleOwner) {
                // Cancel any in-flight recognition when this screen loses focus
                // (e.g. TranscriptionConfirmScreen is pushed on top).
                recognizer?.cancel()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                recognizer?.destroy()
                recognizer = null
            }
        })
    }

    private fun startListening() {
        state = State.LISTENING
        recognizer?.destroy()
        recognizer = null

        if (!SpeechRecognizer.isRecognitionAvailable(carContext)) {
            CarToast.makeText(
                carContext,
                "Speech recognition not available on this device",
                CarToast.LENGTH_LONG,
            ).show()
            screenManager.pop()
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(carContext).also { rec ->
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        screenManager.push(
                            TranscriptionConfirmScreen(carContext, sessionId, sessionTitle, text)
                        )
                    } else {
                        errorMsg = "Nothing heard — tap Retry"
                        state = State.ERROR
                        invalidate()
                    }
                }

                override fun onError(error: Int) {
                    errorMsg = speechErrorString(error)
                    state = State.ERROR
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
        invalidate()
    }

    override fun onGetTemplate(): Template =
        when (state) {
            State.LISTENING ->
                MessageTemplate.Builder("Listening…\nSpeak your command")
                    .setTitle(sessionTitle)
                    .setHeaderAction(Action.BACK)
                    .addAction(
                        Action.Builder()
                            .setTitle("Cancel")
                            .setOnClickListener {
                                recognizer?.cancel()
                                screenManager.pop()
                            }
                            .build(),
                    )
                    .build()

            State.ERROR ->
                MessageTemplate.Builder(errorMsg.ifEmpty { "Could not hear — tap Retry" })
                    .setTitle(sessionTitle)
                    .setHeaderAction(Action.BACK)
                    .addAction(
                        Action.Builder()
                            .setTitle("Retry")
                            .setOnClickListener { startListening() }
                            .build(),
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle("Cancel")
                            .setOnClickListener { screenManager.pop() }
                            .build(),
                    )
                    .build()
        }

    private companion object {
        fun speechErrorString(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone error — check audio"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error — try again"
            SpeechRecognizer.ERROR_NO_MATCH -> "Nothing matched — speak clearly and retry"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy — retry"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected — tap Retry"
            else -> "Recognition error ($error) — tap Retry"
        }
    }
}
