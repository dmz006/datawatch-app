package com.dmzs.datawatchclient.auto

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Shows a session's last output (status, prompt, or response) with TTS playback.
 * Pushed from Session Detail ActionStrip or Home Screen last-output row.
 */
public class LastOutputDetailScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionName: String,
    private val shortText: String?,
    private val longText: String?,
) : Screen(carContext) {

    private var isSpeaking: Boolean = false
    private val tts: TextToSpeech = TextToSpeech(carContext) { status ->
        if (status == TextToSpeech.SUCCESS) tts.language = java.util.Locale.getDefault()
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Auto-play if preference enabled
                val prefs = carContext.getSharedPreferences("auto_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean(PREF_AUTO_PLAY_LAST_RESPONSE, false) && !shortText.isNullOrBlank()) {
                    speakText(shortText)
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
        val body = shortText?.takeIf { it.isNotBlank() } ?: "No content available"

        val cancelAction = Action.Builder()
            .setTitle("Cancel")
            .setOnClickListener { screenManager.pop() }
            .build()

        val ttsIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_auto_info)
        ).build()

        val ttsAction = Action.Builder()
            .setIcon(ttsIcon)
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

        val builder = MessageTemplate.Builder(body)
            .setTitle(sessionName)
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(cancelAction)
                    .addAction(ttsAction)
                    .build()
            )

        // Reply button — pops back to session detail (user taps Reply there)
        builder.addAction(
            Action.Builder()
                .setTitle("Reply")
                .setOnClickListener { screenManager.pop() }
                .build()
        )

        // Long Version button — only when longText is available, parked-only
        if (!longText.isNullOrBlank()) {
            builder.addAction(
                Action.Builder()
                    .setTitle("Long Version")
                    .setOnClickListener(
                        ParkedOnlyOnClickListener.create {
                            screenManager.push(LongOutputScreen(carContext, sessionName, longText))
                        }
                    )
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

    private companion object {
        const val PREF_AUTO_PLAY_LAST_RESPONSE = "auto_play_last_response"
    }
}

/** Pushes the full long-text view with TTS. Parked-only navigation target. */
internal class LongOutputScreen(
    carContext: CarContext,
    private val sessionName: String,
    private val longText: String,
) : Screen(carContext) {

    private val tts: TextToSpeech = TextToSpeech(carContext) { status ->
        if (status == TextToSpeech.SUCCESS) tts.language = java.util.Locale.getDefault()
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                tts.stop()
                tts.shutdown()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val ttsIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_auto_info)
        ).build()

        val ttsAction = Action.Builder()
            .setIcon(ttsIcon)
            .setOnClickListener { tts.speak(longText, TextToSpeech.QUEUE_FLUSH, null, "dw-long") }
            .build()

        return MessageTemplate.Builder(longText)
            .setTitle("$sessionName · Detail")
            .setHeaderAction(Action.BACK)
            .setActionStrip(ActionStrip.Builder().addAction(ttsAction).build())
            .addAction(
                Action.Builder()
                    .setTitle("Reply")
                    .setOnClickListener {
                        screenManager.pop()
                        screenManager.pop()
                    }
                    .build()
            )
            .build()
    }
}
