package com.dmzs.datawatchclient.auto

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Thin singleton wrapper around Android's TextToSpeech for Android Auto screens.
 * Initializes lazily on first [speak] call and reuses the engine until [shutdown].
 * Audio is routed through car speakers via USAGE_ASSISTANCE_NAVIGATION_GUIDANCE.
 */
internal object AutoTts {

    private var tts: TextToSpeech? = null
    private var ready = false

    private val carAudioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun speak(context: Context, text: String) {
        val trimmed = text.take(MAX_CHARS)
        val existing = tts
        if (existing != null && ready) {
            existing.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            return
        }
        tts?.shutdown()
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
                tts?.setAudioAttributes(carAudioAttrs)
                tts?.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }

    private const val UTTERANCE_ID = "auto_tts"
    private const val MAX_CHARS = 500
}
