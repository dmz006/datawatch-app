package com.dmzs.datawatchclient.auto

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.transport.dto.GuardrailVerdictDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * BL33 — per-guardrail approval on the Block Details screen.
 *
 * Single block  → MessageTemplate: "Approve [name]" button per blocked verdict.
 * Multiple blocks → ListTemplate: one tappable row per block (tap = approve that guardrail);
 *                   ActionStrip carries "Listen" + "Approve All" for bulk override.
 *
 * Endpoint: POST /api/sessions/{id}/guardrail/{name}/approve (datawatch#153).
 */
public class BlockDetailsScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionName: String,
    private val verdicts: List<GuardrailVerdictDto>,
) : Screen(carContext) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var focusRequest: AudioFocusRequest? = null
    private val audioManager = carContext.applicationContext.getSystemService(AudioManager::class.java)

    private val tts: TextToSpeech =
        TextToSpeech(carContext.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = java.util.Locale.getDefault()
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
                            abandonAudioFocus()
                        }

                        @Deprecated("replaced by onStop")
                        override fun onError(utteranceId: String) {
                            abandonAudioFocus()
                        }

                        override fun onStop(
                            utteranceId: String,
                            interrupted: Boolean,
                        ) {
                            abandonAudioFocus()
                        }
                    },
                )
            }
        }

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    tts.stop()
                    tts.shutdown()
                    abandonAudioFocus()
                    scope.cancel()
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        val blocks = verdicts.filter { it.outcome == "block" }.ifEmpty { verdicts }
        return if (blocks.size == 1) {
            buildSingleBlockTemplate(blocks.first())
        } else {
            buildMultiBlockTemplate(blocks)
        }
    }

    // MessageTemplate: single block — one targeted "Approve [name]" button + "Kill Session"
    private fun buildSingleBlockTemplate(verdict: GuardrailVerdictDto): Template {
        val name = GuardrailTtsBuilder.friendlyName(verdict.guardrail)
        val body = "⚠ $name\n${verdict.summary.take(SUMMARY_CHARS)}"
        // MessageTemplate allows only 1 custom-title action; Kill Session shares the ActionStrip
        // with the Listen action so Approve can be the single full-width button.
        val actionStrip =
            ActionStrip.Builder()
                .addAction(listenAction(body))
                .addAction(
                    Action.Builder()
                        .setTitle("Kill Session")
                        .setOnClickListener { showKillToast() }
                        .build(),
                )
                .build()
        return MessageTemplate.Builder(body)
            .setTitle("$sessionName · Blocked")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .addAction(
                Action.Builder()
                    .setTitle("Approve $name")
                    .setBackgroundColor(CarColor.GREEN)
                    .setOnClickListener { onApproveGuardrail(verdict.guardrail) }
                    .build(),
            )
            .build()
    }

    // ListTemplate: multiple blocks — tap any row to approve that specific guardrail;
    // ActionStrip carries "Listen" + "Approve All" for bulk override.
    private fun buildMultiBlockTemplate(blocks: List<GuardrailVerdictDto>): Template {
        val ttsText = GuardrailTtsBuilder.buildAllVerdicts(blocks)
        val itemList =
            ItemList.Builder()
                .apply {
                    blocks.take(MAX_LIST_ROWS).forEach { verdict ->
                        val name = GuardrailTtsBuilder.friendlyName(verdict.guardrail)
                        addItem(
                            Row.Builder()
                                .setTitle("⚠ $name")
                                .addText(verdict.summary.take(SUMMARY_CHARS))
                                .setOnClickListener { onApproveGuardrail(verdict.guardrail) }
                                .build(),
                        )
                    }
                }
                .build()
        val actionStrip =
            ActionStrip.Builder()
                .addAction(listenAction(ttsText))
                .addAction(
                    Action.Builder()
                        .setTitle("Approve All")
                        .setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener { onApproveAll() }
                        .build(),
                )
                .build()
        return ListTemplate.Builder()
            .setTitle("$sessionName · ${blocks.size} Blocks — tap to approve")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setSingleList(itemList)
            .build()
    }

    private fun listenAction(text: String): Action =
        Action.Builder()
            .setTitle("Listen")
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice),
                ).build(),
            )
            .setOnClickListener { speakWithFocus(text) }
            .build()

    private fun onApproveGuardrail(guardrailName: String) {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: return@runCatching
                AutoServiceLocator.transportFor(profile).approveGuardrailBlock(sessionId, guardrailName).fold(
                    onSuccess = {
                        CarToast.makeText(
                            carContext,
                            "Approved: ${GuardrailTtsBuilder.friendlyName(guardrailName)}",
                            CarToast.LENGTH_SHORT,
                        ).show()
                        screenManager.pop()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Approve failed: ${err.message?.take(ERROR_MSG_CHARS)}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }

    private fun onApproveAll() {
        scope.launch {
            runCatching {
                val profile = resolveActiveProfile() ?: return@runCatching
                AutoServiceLocator.transportFor(profile).runSessionGuardrail(sessionId).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "All gates approved", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Approve All failed: ${err.message?.take(ERROR_MSG_CHARS)}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }

    private fun showKillToast() {
        CarToast.makeText(carContext, "Kill — use session detail", CarToast.LENGTH_SHORT).show()
        screenManager.pop()
    }

    private fun speakWithFocus(text: String) {
        abandonAudioFocus()
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
        focusRequest = req
        audioManager.requestAudioFocus(req)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dw-block")
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        const val BODY_CHAR_LIMIT = 500
        const val SUMMARY_CHARS = 120
        const val ERROR_MSG_CHARS = 40
        const val MAX_LIST_ROWS = 6
    }
}
