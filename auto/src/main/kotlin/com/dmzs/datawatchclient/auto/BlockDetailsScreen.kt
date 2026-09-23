package com.dmzs.datawatchclient.auto

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
import com.dmzs.datawatchclient.transport.dto.GuardrailVerdictDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * BL33 — per-guardrail approval on the Block Details screen.
 *
 * ListTemplate with one row per blocked guardrail plus an "Approve All" first row.
 * Row taps are always driving-safe. ActionStrip carries a single speaker icon for TTS.
 *
 * Pushed from AutoSessionDetailScreen when the session has guardrail blocks.
 * Endpoint: POST /api/sessions/{id}/guardrail/{name}/approve (datawatch#153).
 */
public class BlockDetailsScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionName: String,
    private val verdicts: List<GuardrailVerdictDto>,
) : Screen(carContext) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    val blocks = verdicts.filter { it.outcome == "block" }.ifEmpty { verdicts }
                    val ttsText = GuardrailTtsBuilder.buildAllVerdicts(blocks)
                    AutoTts.speak(carContext, ttsText)
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    scope.cancel()
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        val blocks = verdicts.filter { it.outcome == "block" }.ifEmpty { verdicts }
        val ttsText = GuardrailTtsBuilder.buildAllVerdicts(blocks)

        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val dotGreenIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_dot_green)).build()
        val dotRedIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_dot_red)).build()

        val blockWord = if (blocks.size == 1) "block" else "blocks"
        val itemList =
            ItemList.Builder().apply {
                // "Approve All" as a tappable row — driving-safe via row click listener.
                addItem(
                    Row.Builder()
                        .setTitle("Approve all $blockWord")
                        .setImage(dotGreenIcon)
                        .addText("Continue past all ${blocks.size} guardrail $blockWord")
                        .setOnClickListener { onApproveAll() }
                        .build(),
                )
                blocks.take(MAX_LIST_ROWS - 1).forEach { verdict ->
                    val name = GuardrailTtsBuilder.friendlyName(verdict.guardrail)
                    addItem(
                        Row.Builder()
                            .setTitle("⚠ $name")
                            .setImage(dotRedIcon)
                            .addText(verdict.summary.take(SUMMARY_CHARS))
                            .setOnClickListener { onApproveGuardrail(verdict.guardrail) }
                            .build(),
                    )
                }
            }.build()

        return ListTemplate.Builder()
            .setTitle("$sessionName · ${blocks.size} $blockWord")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(speakerIcon)
                            .setOnClickListener { AutoTts.speak(carContext, ttsText) }
                            .build(),
                    )
                    .build(),
            )
            .setSingleList(itemList)
            .build()
    }

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

    private companion object {
        const val SUMMARY_CHARS = 120
        const val ERROR_MSG_CHARS = 40
        // Max ItemList rows: "Approve All" row takes 1 slot; remaining go to individual blocks.
        const val MAX_LIST_ROWS = 6
    }
}
