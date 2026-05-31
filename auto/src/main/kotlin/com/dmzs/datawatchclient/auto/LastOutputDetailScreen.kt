package com.dmzs.datawatchclient.auto

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
    private val tts: TextToSpeech = TextToSpeech(carContext.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) tts.language = java.util.Locale.getDefault()
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (!shortText.isNullOrBlank()) speakText(shortText)
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

        // "Play Short" — speaks the summary (always available while driving)
        builder.addAction(
            Action.Builder()
                .setTitle(if (isSpeaking) "Stop" else "Play Short")
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

        // "Play Long" — speaks longText inline, no screen push (works while driving)
        if (!longText.isNullOrBlank()) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Play Long")
                    .setOnClickListener { speakText(longText) }
                    .build()
            )
        }

        return builder.build()
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dw-detail")
        isSpeaking = true
        invalidate()
    }
}
