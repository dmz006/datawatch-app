package com.dmzs.datawatchclient.auto

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Singleton TTS helper for Android Auto screens.
 * Routes audio through car speakers via USAGE_ASSISTANCE_NAVIGATION_GUIDANCE.
 * Requests AUDIOFOCUS_GAIN_TRANSIENT before playback and releases it on completion —
 * without audio focus, Android Auto silences TTS even if the engine is ready.
 */
internal object AutoTts {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var focusRequest: AudioFocusRequest? = null

    private val carAudioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun speak(context: Context, text: String) {
        val trimmed = text.take(MAX_CHARS)
        val appCtx = context.applicationContext
        val audioManager = appCtx.getSystemService(AudioManager::class.java)

        val existing = tts
        if (existing != null && ready) {
            requestFocus(audioManager)
            existing.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            return
        }
        tts?.shutdown()
        tts = TextToSpeech(appCtx) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
                tts?.setAudioAttributes(carAudioAttrs)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {}
                    override fun onDone(utteranceId: String) { abandonFocus(audioManager) }
                    @Deprecated("Replaced by onError(String, Int)")
                    override fun onError(utteranceId: String) { abandonFocus(audioManager) }
                    override fun onStop(utteranceId: String, interrupted: Boolean) { abandonFocus(audioManager) }
                })
                requestFocus(audioManager)
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

    private fun requestFocus(audioManager: AudioManager) {
        abandonFocus(audioManager)
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(carAudioAttrs)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        audioManager.requestAudioFocus(req)
    }

    private fun abandonFocus(audioManager: AudioManager) {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private const val UTTERANCE_ID = "auto_tts"
    private const val MAX_CHARS = 500
}
