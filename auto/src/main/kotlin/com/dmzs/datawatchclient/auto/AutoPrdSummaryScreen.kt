@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.dmzs.datawatchclient.transport.dto.PrdDto

/**
 * PRD overview / spec screen — MessageTemplate pushed from [AutoPrdDetailScreen].
 *
 * Shows PRD-level summary: status, story counts, and spec text (truncated to fit).
 * Pushed from a ListTemplate (AutoPrdDetailScreen), so MessageTemplate is allowed here.
 * ActionStrip: speaker (TTS) + close (pop) — exactly 2 icon-only actions.
 */
public class AutoPrdSummaryScreen(
    carContext: CarContext,
    private val prd: PrdDto,
) : Screen(carContext) {

    override fun onGetTemplate(): Template = try {
        val stories = prd.stories
        val done = stories.count { it.status.lowercase() in DONE_STATUSES }
        val active = stories.count { it.status.lowercase() in setOf("in_progress", "running", "active") }
        val failed = stories.count { it.status.lowercase() == "failed" }

        val body = buildString {
            append("${prd.status}  ·  $done/${stories.size} stories done")
            if (active > 0) append("  ·  $active active")
            if (failed > 0) append("  ·  $failed failed")
            prd.spec?.takeIf { it.isNotBlank() }?.let {
                append("\n\n${it.take(MAX_SPEC_CHARS)}")
            }
            if (isEmpty()) append("No additional details.")
        }

        val speakerIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)
        ).build()
        val closeIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)
        ).build()

        MessageTemplate.Builder(body)
            .setTitle(prd.title?.takeIf { it.isNotBlank() }?.take(MAX_TITLE) ?: "Overview")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(speakerIcon)
                            .setOnClickListener { AutoTts.speak(carContext, body) }
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
    } catch (e: Throwable) {
        val closeIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_auto_close)
        ).build()
        MessageTemplate.Builder("Error: ${e.message ?: "Unknown"}")
            .setTitle("Overview")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setIcon(closeIcon).setOnClickListener { screenManager.pop() }.build())
                    .build(),
            )
            .build()
    }

    private companion object {
        const val MAX_TITLE = 30
        const val MAX_SPEC_CHARS = 300
        val DONE_STATUSES = setOf("complete", "completed", "done")
    }
}
