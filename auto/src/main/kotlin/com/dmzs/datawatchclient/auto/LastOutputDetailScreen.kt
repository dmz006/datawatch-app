package com.dmzs.datawatchclient.auto

import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Shows a session's last output (status, prompt, or response) with TTS playback.
 * Auto-plays the short summary on entry. "Play Long" speaks the full narrative
 * inline without a screen push (works while driving).
 */
public class LastOutputDetailScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionName: String,
    private val shortText: String?,
    private val longText: String?,
) : Screen(carContext) {

    private var isSpeaking: Boolean = false
    private var ttsReady: Boolean = false
    private var pendingSpeak: String? = null
    private val tts: TextToSpeech = TextToSpeech(carContext.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts.language = java.util.Locale.getDefault()
            // Route TTS through car speakers. Without this Android Auto
            // routes output to the phone speaker instead of the head unit.
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            ttsReady = true
            // onStart() may have fired before binding completed — play now if so.
            pendingSpeak?.let { text -> pendingSpeak = null; speakText(text) }
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (!shortText.isNullOrBlank()) {
                    if (ttsReady) speakText(shortText) else pendingSpeak = shortText
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                tts.stop()
                isSpeaking = false
            }

            override fun onDestroy(owner: LifecycleOwner) {
                tts.stop()
                tts.shutdown()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val body = shortText?.takeIf { it.isNotBlank() } ?: "No content available"

        val voiceIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)
        ).build()

        val builder = MessageTemplate.Builder(body)
            .setTitle(sessionName)
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle(if (isSpeaking) "Stop" else "Listen")
                            .setIcon(voiceIcon)
                            .setOnClickListener {
                                if (isSpeaking) {
                                    tts.stop()
                                    isSpeaking = false
                                    invalidate()
                                } else {
                                    speakText(body)
                                }
                            }
                            .build()
                    )
                    .build()
            )

        // "Play Long" — full version; only shown when a longer form exists.
        // ActionStrip "Listen"/"Stop" handles replay of the short summary above.
        if (!longText.isNullOrBlank()) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Play Long")
                    .setOnClickListener { speakText(longText) }
                    .build()
            )
        }

        // MessageTemplate requires at least one primary action.
        builder.addAction(
            Action.Builder()
                .setTitle("Close")
                .setOnClickListener { screenManager.pop() }
                .build()
        )

        return builder.build()
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dw-detail")
        isSpeaking = true
        invalidate()
    }
}
