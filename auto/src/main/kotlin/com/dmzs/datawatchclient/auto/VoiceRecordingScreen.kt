@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
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
 *   LISTENING  — ASR active, auto-starts on entry; live RMS meter + partial results shown
 *   ERROR      — recognition failed, shows Retry / Back
 *   CONFIRMED  — transcript ready, TTS auto-plays it through car speakers; Send or Retry
 *
 * Audio focus is claimed with AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE before each
 * recognition session so navigation / music is silenced while the mic is active.
 * Focus is released on results, error, cancel, or screen stop.
 *
 * Uses [CarContext.applicationContext] for [SpeechRecognizer] and [TextToSpeech]
 * binding — the [CarContext] itself is not suitable for service binding.
 */
public class VoiceRecordingScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
    private val prdId: String? = null,
    /** When set, the transcript updates the story description via editStory. */
    private val storyId: String? = null,
    /** When set, the transcript updates the task spec via editPrdTask. */
    private val taskId: String? = null,
) : Screen(carContext) {
    private sealed class State {
        object Listening : State()

        data class Error(val msg: String) : State()

        data class Confirmed(val transcript: String) : State()
    }

    private var state: State = State.Listening

    // Live feedback during LISTENING — updated by recognizer callbacks.
    private var partialText: String = ""
    private var micReady = false // true after onReadyForSpeech fires

    private var ttsReady = false
    private var pendingSpeak: String? = null
    private var recognizer: SpeechRecognizer? = null
    private var focusRequest: AudioFocusRequest? = null // recording (EXCLUSIVE)
    private var ttsFocusRequest: AudioFocusRequest? = null // TTS playback (TRANSIENT)
    private val audioManager =
        carContext.applicationContext
            .getSystemService(AudioManager::class.java)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val tts: TextToSpeech =
        TextToSpeech(carContext.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = java.util.Locale.getDefault()
                // Route TTS through car speakers, not the phone speaker.
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                tts.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {}

                        override fun onDone(utteranceId: String) {
                            abandonTtsFocus()
                        }

                        @Deprecated("replaced by onStop")
                        override fun onError(utteranceId: String) {
                            abandonTtsFocus()
                        }

                        override fun onStop(
                            utteranceId: String,
                            interrupted: Boolean,
                        ) {
                            abandonTtsFocus()
                        }
                    },
                )
                ttsReady = true
                pendingSpeak?.let { text ->
                    pendingSpeak = null
                    speakWithFocus(text)
                }
            }
        }

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (state is State.Listening) startListening()
                }

                override fun onStop(owner: LifecycleOwner) {
                    recognizer?.cancel()
                    tts.stop()
                    abandonAudioFocus()
                    abandonTtsFocus()
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    recognizer?.destroy()
                    recognizer = null
                    tts.stop()
                    tts.shutdown()
                    abandonAudioFocus()
                    abandonTtsFocus()
                    scope.cancel()
                }
            },
        )
    }

    override fun onGetTemplate(): Template = try {
        when (val s = state) {
            is State.Listening -> buildListeningTemplate()
            is State.Error -> buildErrorTemplate(s.msg)
            is State.Confirmed -> buildConfirmTemplate(s.transcript)
        }
    } catch (e: Throwable) {
        val voiceIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)).build()
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        // Always ListTemplate so the template type never changes between states on invalidate().
        // A MessageTemplate→ListTemplate switch on invalidate() causes "cannot do while driving"
        // on strict head units (Samsung gearhead).
        ListTemplate.Builder()
            .setTitle(sessionTitle)
            .setHeaderAction(Action.BACK)
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("⚠ Error")
                            .addText(e.message ?: e::class.simpleName ?: "Unknown error")
                            .build(),
                    )
                    .build(),
            )
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(voiceIcon).setOnClickListener { startListening() }.build())
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener { screenManager.pop() }.build())
                    .build(),
            )
            .build()
    }

    private fun buildListeningTemplate(): Template {
        val statusText =
            when {
                !micReady -> "Starting microphone…"
                partialText.isNotBlank() -> partialText.take(PARTIAL_CHARS)
                else -> "Listening…"
            }
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        // ListTemplate (not MessageTemplate) so the template type stays constant across all states.
        // Changing from MessageTemplate→ListTemplate on invalidate() causes "cannot do while driving"
        // on Samsung gearhead — the host pre-validates on push and rejects a type change.
        return ListTemplate.Builder()
            .setTitle(sessionTitle)
            .setHeaderAction(Action.BACK)
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("🎤 Listening")
                            .addText(statusText)
                            .build(),
                    )
                    .build(),
            )
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(speakerIcon).setOnClickListener {
                        AutoTts.speak(carContext, partialText.ifBlank { "Listening" })
                    }.build())
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener {
                        recognizer?.cancel(); abandonAudioFocus(); screenManager.pop()
                    }.build())
                    .build(),
            )
            .build()
    }

    private fun buildErrorTemplate(msg: String): Template {
        val voiceIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)).build()
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        // ListTemplate keeps the template type constant (see buildListeningTemplate comment).
        return ListTemplate.Builder()
            .setTitle(sessionTitle)
            .setHeaderAction(Action.BACK)
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("⚠ ${msg.ifEmpty { "Could not hear" }}")
                            .addText("Tap mic icon to retry")
                            .build(),
                    )
                    .build(),
            )
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(voiceIcon).setOnClickListener { startListening() }.build())
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener { screenManager.pop() }.build())
                    .build(),
            )
            .build()
    }

    private fun buildConfirmTemplate(transcript: String): Template {
        val voiceIcon =
            CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice),
            ).build()
        val screenTitle = when {
            taskId != null -> "$sessionTitle · Update Task"
            storyId != null -> "$sessionTitle · Update Story"
            prdId != null -> "$sessionTitle · Update Spec"
            else -> "$sessionTitle · Voice"
        }
        val closeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)).build()
        // ListTemplate so "Send" and "Cancel" are row click listeners — driving-safe.
        // MessageTemplate.addAction(setTitle) is parked-only on MESSAGING path.
        val preview = transcript.take(MAX_TRANSCRIPT_PREVIEW).ifBlank { "No transcription" }
        val itemList = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("✓ Send")
                    .addText(preview)
                    .setOnClickListener { onSend(transcript) }
                    .build(),
            )
            .addItem(
                Row.Builder()
                    .setTitle("✗ Cancel")
                    .addText("Discard this recording")
                    .setOnClickListener { screenManager.pop() }
                    .build(),
            )
            .build()
        return ListTemplate.Builder()
            .setTitle(screenTitle)
            .setHeaderAction(Action.BACK)
            .setSingleList(itemList)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(voiceIcon)
                            .setOnClickListener { speakWithFocus(transcript) }
                            .build(),
                    )
                    .addAction(
                        Action.Builder()
                            .setIcon(closeIcon)
                            .setOnClickListener { screenManager.pop() }
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun requestAudioFocus(): Boolean {
        abandonAudioFocus()
        // USAGE_ASSISTANT silences navigation/media without triggering USAGE_VOICE_COMMUNICATION,
        // which in Android Auto over Bluetooth starts BT SCO (async). SCO setup takes 200-500ms;
        // SpeechRecognizer starts immediately and gets silence/noise → ERROR_NO_MATCH.
        val attrs =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        val req =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
        focusRequest = req
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun startListening() {
        // Stop TTS before grabbing the mic: if Retry is tapped while the confirmed-state
        // readback is still playing, the recognizer would capture its own TTS audio.
        tts.stop()
        pendingSpeak = null
        abandonTtsFocus()
        partialText = ""
        micReady = false
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

        requestAudioFocus()

        val rec = try {
            SpeechRecognizer.createSpeechRecognizer(appCtx)
        } catch (e: Throwable) {
            null
        }
        if (rec == null) {
            abandonAudioFocus()
            state = State.Error("Could not start recognizer — tap Retry")
            invalidate()
            return
        }

        recognizer = rec.also {
            it.setRecognitionListener(
                    object : RecognitionListener {
                        override fun onResults(results: Bundle) {
                            abandonAudioFocus()
                            val text =
                                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull()?.trim().orEmpty()
                            if (text.isNotEmpty()) {
                                state = State.Confirmed(text)
                                invalidate()
                                // Guard against the TTS race: if binding hasn't completed yet, queue the text.
                                if (ttsReady) {
                                    speakWithFocus(text)
                                } else {
                                    pendingSpeak = text
                                }
                            } else {
                                state = State.Error("Nothing heard — tap Retry")
                                invalidate()
                            }
                        }

                        override fun onError(error: Int) {
                            abandonAudioFocus()
                            state = State.Error(speechErrorString(error))
                            invalidate()
                        }

                        override fun onPartialResults(partialResults: Bundle) {
                            val text =
                                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    ?.firstOrNull()?.trim().orEmpty()
                            if (text != partialText) {
                                partialText = text
                                invalidate()
                            }
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            // Meter removed — no template update needed on volume change.
                        }

                        override fun onReadyForSpeech(params: Bundle) {
                            micReady = true
                            invalidate()
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onBufferReceived(buffer: ByteArray) {}

                        override fun onEndOfSpeech() {}

                        override fun onEvent(
                            eventType: Int,
                            params: Bundle,
                        ) {}
                    },
                )

                val intent =
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
                        // Car environments have road/A/C noise — give the user a wider silence window
                        // than the OS default (~1 s) so a breath between phrases isn't treated as end-of-speech.
                        // Keep totals well under the Google ASR ~10 s max to avoid ERROR_NO_MATCH on timeout.
                        putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 500)
                        putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 4000)
                        putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 2500)
                    }
                it.startListening(intent)
            }
    }

    private fun onSend(transcript: String) {
        scope.launch {
            runCatching {
                val profile =
                    resolveActiveProfile() ?: run {
                        CarToast.makeText(carContext, "No active server", CarToast.LENGTH_SHORT).show()
                        return@runCatching
                    }
                val transport = AutoServiceLocator.transportFor(profile)
                val result = when {
                    taskId != null && prdId != null ->
                        transport.editPrdTask(prdId, taskId, transcript)
                            .map { }
                    storyId != null && prdId != null ->
                        transport.editStory(prdId, storyId, newDescription = transcript)
                    prdId != null ->
                        transport.patchPrd(prdId, spec = transcript)
                    else ->
                        transport.replyToSession(sessionId, "$transcript\r")
                }
                result.fold(
                    onSuccess = {
                        val msg = when {
                            taskId != null -> "Task updated"
                            storyId != null -> "Story updated"
                            prdId != null -> "Spec updated"
                            else -> "Sent"
                        }
                        CarToast.makeText(carContext, msg, CarToast.LENGTH_SHORT).show()
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

    private fun speakWithFocus(text: String) {
        abandonTtsFocus()
        val req =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener { }
                .build()
        ttsFocusRequest = req
        audioManager.requestAudioFocus(req)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dw-voice")
    }

    private fun abandonTtsFocus() {
        ttsFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        ttsFocusRequest = null
    }

    private companion object {
        const val ERROR_MSG_CHARS = 40
        const val PARTIAL_CHARS = 200
        const val MAX_TRANSCRIPT_PREVIEW = 120

        fun speechErrorString(error: Int): String =
            when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Microphone error — check audio"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required — grant in phone settings"
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                -> "Network error — try again"
                SpeechRecognizer.ERROR_NO_MATCH -> "Nothing matched — speak clearly and retry"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy — retry"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected — tap Retry"
                else -> "Recognition error ($error) — tap Retry"
            }
    }
}
