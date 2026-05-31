@file:Suppress("MagicNumber")
package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.media.CarAudioRecord
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Voice recording screen for Android Auto. Captures audio via [CarAudioRecord],
 * then calls the Whisper transcription endpoint.
 * On success → pushes [TranscriptionConfirmScreen].
 */
public class VoiceRecordingScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String,
) : Screen(carContext) {

    private enum class RecordState { RECORDING, TRANSCRIBING }

    private var state: RecordState = RecordState.RECORDING
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordJob: Job? = null
    private var recorder: CarAudioRecord? = null
    private val audioBuffer = ByteArrayOutputStream()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                startRecording()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                stopRecordingQuiet()
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        return when (state) {
            RecordState.RECORDING ->
                MessageTemplate.Builder("Listening…\nSpeak then tap Done")
                    .setTitle(sessionTitle)
                    .setHeaderAction(Action.BACK)
                    .addAction(
                        Action.Builder()
                            .setTitle("Done")
                            .setOnClickListener { onDone() }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle("Cancel")
                            .setOnClickListener { onCancel() }
                            .build()
                    )
                    .build()

            RecordState.TRANSCRIBING ->
                MessageTemplate.Builder("Transcribing…\nPlease wait")
                    .setTitle(sessionTitle)
                    .setHeaderAction(Action.BACK)
                    .addAction(
                        Action.Builder()
                            .setTitle("Cancel")
                            .setOnClickListener { onCancel() }
                            .build()
                    )
                    .build()
        }
    }

    private fun startRecording() {
        audioBuffer.reset()
        recordJob = scope.launch(Dispatchers.IO) {
            try {
                val rec = CarAudioRecord.create(carContext)
                recorder = rec
                rec.startRecording()
                val buf = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)
                while (isActive) {
                    val read = rec.read(buf, 0, buf.size)
                    if (read > 0) audioBuffer.write(buf, 0, read)
                    else if (read < 0) break
                }
            } catch (e: SecurityException) {
                // Host denied recording (e.g. UX_RESTRICTIONS_NO_RECORDING while driving).
                // Surface the denial so the user knows why recording didn't capture anything.
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    CarToast.makeText(
                        carContext,
                        "Microphone not available — try from the notification reply button",
                        CarToast.LENGTH_LONG,
                    ).show()
                    screenManager.pop()
                }
            } catch (_: Throwable) {
                // Other failures surface as empty audio at Done tap.
            }
        }
    }

    private fun onDone() {
        recordJob?.cancel()
        recorder?.stopRecording()
        recorder = null
        val audio = audioBuffer.toByteArray()
        if (audio.isEmpty()) {
            CarToast.makeText(carContext, "No audio recorded", CarToast.LENGTH_SHORT).show()
            screenManager.pop()
            return
        }
        state = RecordState.TRANSCRIBING
        invalidate()
        scope.launch { transcribe(audio) }
    }

    private fun onCancel() {
        recordJob?.cancel()
        stopRecordingQuiet()
        screenManager.pop()
    }

    private fun stopRecordingQuiet() {
        try {
            recorder?.stopRecording()
        } catch (_: Throwable) {}
        recorder = null
    }

    private suspend fun transcribe(audio: ByteArray) {
        val profile = resolveActiveProfile()
        if (profile == null) {
            withContext(Dispatchers.Main) {
                CarToast.makeText(carContext, "No active server", CarToast.LENGTH_SHORT).show()
                screenManager.pop()
            }
            return
        }
        AutoServiceLocator.transportFor(profile)
            .transcribeAudio(audio, CarAudioRecord.AUDIO_CONTENT_MIME, sessionId = sessionId, autoExec = false)
            .fold(
                onSuccess = { result ->
                    withContext(Dispatchers.Main) {
                        screenManager.push(
                            TranscriptionConfirmScreen(carContext, sessionId, sessionTitle, result.transcript)
                        )
                    }
                },
                onFailure = { err ->
                    withContext(Dispatchers.Main) {
                        CarToast.makeText(
                            carContext,
                            "Transcription failed: ${err.message?.take(ERROR_MSG_CHARS)}",
                            CarToast.LENGTH_LONG
                        ).show()
                        screenManager.pop()
                    }
                }
            )
    }

    private companion object {
        const val ERROR_MSG_CHARS = 40
    }
}
