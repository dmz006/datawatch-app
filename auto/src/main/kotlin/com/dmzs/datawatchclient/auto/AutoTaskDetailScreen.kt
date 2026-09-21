@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.transport.dto.PrdTaskDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Task detail for Android Auto — depth 5 (Car App Library max).
 *
 * Uses ListTemplate so Requeue/Cancel are row click listeners — driving-safe
 * on MESSAGING path. MessageTemplate.addAction() is parked-only, which means
 * the user couldn't requeue/cancel a task while driving with the old design.
 *
 * At depth 5 (max), VoiceRecordingScreen cannot be pushed (would exceed the
 * 5-screen limit). The mic ActionStrip icon instead speaks the full task body
 * via TTS so the user can listen to all details hands-free.
 *
 * Row layout:
 *   [if failed]       "⟳ Requeue task" (tappable)
 *   [if in_progress]  "✗ Cancel task" (tappable)
 *   Task detail row   (status + spec + error if any) — display only
 *   Verification row  (if present) — display only
 *   Files row         (filesTouched, if any) — display only
 *
 * ActionStrip (2 icon-only, MESSAGING limit; no screen push at depth 5):
 *   slot 1: speaker → TTS task name + status (quick summary)
 *   slot 2: mic     → TTS full task body including spec, error, verification
 */
public class AutoTaskDetailScreen(
    carContext: CarContext,
    private val prdId: String,
    private val task: PrdTaskDto,
) : Screen(carContext) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
            },
        )
    }

    private fun fireRequeue() {
        scope.launch {
            try {
                val profile = resolveActiveProfile() ?: return@launch
                AutoServiceLocator.transportFor(profile).requeuePrdTask(prdId, task.id).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Task requeued", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Requeue failed: ${err.message ?: err::class.simpleName}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            } catch (e: Throwable) {
                CarToast.makeText(carContext, "Error: ${e.message}", CarToast.LENGTH_LONG).show()
            }
        }
    }

    private fun fireCancelTask() {
        scope.launch {
            try {
                val profile = resolveActiveProfile() ?: return@launch
                AutoServiceLocator.transportFor(profile).cancelPrdTask(prdId, task.id).fold(
                    onSuccess = {
                        CarToast.makeText(carContext, "Task cancelled", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
                    },
                    onFailure = { err ->
                        CarToast.makeText(
                            carContext,
                            "Cancel failed: ${err.message ?: err::class.simpleName}",
                            CarToast.LENGTH_LONG,
                        ).show()
                    },
                )
            } catch (e: Throwable) {
                CarToast.makeText(carContext, "Error: ${e.message}", CarToast.LENGTH_LONG).show()
            }
        }
    }

    private fun listLimit(): Int = runCatching {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }.getOrElse { MAX_ROWS_FALLBACK }

    override fun onGetTemplate(): Template {
        val taskTitle = task.task.take(MAX_TITLE).ifBlank { "Task" }
        val limit = listLimit()
        val items = ItemList.Builder()
        var rowCount = 0

        // Lifecycle action rows (driving-safe — row click, not addAction)
        if (task.status == "failed" && rowCount < limit - 1) {
            items.addItem(
                Row.Builder()
                    .setTitle("⟳ Requeue task")
                    .addText("Tap to requeue this failed task")
                    .setOnClickListener { fireRequeue() }
                    .build(),
            )
            rowCount++
        }
        if (task.status in setOf("pending", "in_progress") && rowCount < limit - 1) {
            items.addItem(
                Row.Builder()
                    .setTitle("✗ Cancel task")
                    .addText("Tap to cancel this task")
                    .setOnClickListener { fireCancelTask() }
                    .build(),
            )
            rowCount++
        }

        // Task detail row (not tappable)
        if (rowCount < limit) {
            val statusLine = buildString {
                append("Status: ${task.status.replace('_', ' ')}")
                if (task.retryCount > 0) append("  ·  Retries: ${task.retryCount}")
            }
            val row = Row.Builder()
                .setTitle(CarText.create(statusLine))
            task.spec.takeIf { it.isNotBlank() }?.let { row.addText(it.take(MAX_SPEC_CHARS)) }
            task.error?.takeIf { it.isNotBlank() && task.status == "failed" }
                ?.let { row.addText("Error: ${it.take(MAX_ERROR_SHORT)}") }
            items.addItem(row.build())
            rowCount++
        }

        // Verification row (not tappable)
        task.verification?.let { v ->
            if (rowCount < limit) {
                val verTitle = buildString {
                    append("Verification")
                    v.severity?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                }
                val verText = v.summary?.take(MAX_VERIF_CHARS)?.takeIf { it.isNotBlank() }
                val issueText = v.issues.take(3).joinToString("  ·  ") { it.take(40) }
                val row = Row.Builder().setTitle(verTitle)
                if (verText != null) row.addText(verText)
                if (issueText.isNotBlank()) row.addText(issueText)
                items.addItem(row.build())
                rowCount++
            }
        }

        // Files touched row (not tappable)
        if (task.filesTouched.isNotEmpty() && rowCount < limit) {
            val fileTitle = "${task.filesTouched.size} file${if (task.filesTouched.size == 1) "" else "s"} touched"
            val fileList = task.filesTouched.take(3).joinToString("  ") { it.substringAfterLast('/') }
            val more = (task.filesTouched.size - 3).takeIf { it > 0 }
            items.addItem(
                Row.Builder()
                    .setTitle(fileTitle)
                    .addText(fileList + (more?.let { " … +$it" } ?: ""))
                    .build(),
            )
            rowCount++
        }

        // ActionStrip: 2 icon-only at depth 5 — no screen push (would exceed limit).
        // Speaker = quick TTS (name + status); mic = full TTS (spec + verification + files).
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val micIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)).build()
        val strip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(speakerIcon)
                    .setOnClickListener {
                        // Quick: task name + status
                        AutoTts.speak(carContext, "${task.task}. Status: ${task.status.replace('_', ' ')}.")
                    }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setIcon(micIcon)
                    .setOnClickListener {
                        // Full: spec + error + verification (long version for listening while parked)
                        AutoTts.speak(carContext, buildTaskBody(task))
                    }
                    .build(),
            )
            .build()

        return ListTemplate.Builder()
            .setTitle(taskTitle)
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .setActionStrip(strip)
            .build()
    }

    internal companion object {
        const val MAX_TITLE = 52
        const val MAX_SPEC_CHARS = 200
        const val MAX_ERROR_SHORT = 120
        const val MAX_VERIF_CHARS = 150
        const val MAX_ROWS_FALLBACK = 6

        fun buildTaskBody(task: PrdTaskDto): String =
            buildString {
                appendLine("[You]: ${task.task.take(MAX_TITLE)}")
                appendLine()
                val dwResponse = buildString {
                    append("Status: ${task.status.replace('_', ' ')}")
                    if (task.retryCount > 0) append("  ·  Retries: ${task.retryCount}")
                }
                appendLine("[datawatch]: $dwResponse")
                task.spec.takeIf { it.isNotBlank() }?.let {
                    appendLine()
                    appendLine("Spec: ${it.take(300)}")
                }
                task.error?.takeIf { it.isNotBlank() && task.status == "failed" }?.let { err ->
                    appendLine()
                    appendLine("Error:")
                    append(err.take(400))
                    if (err.length > 400) append("…")
                    appendLine()
                }
                task.verification?.let { v ->
                    appendLine()
                    appendLine("Verification:")
                    v.summary?.takeIf { it.isNotBlank() }?.let { appendLine(it.take(200)) }
                    v.severity?.takeIf { it.isNotBlank() }?.let { appendLine("Severity: $it") }
                    v.issues.take(5).forEach { appendLine("• ${it.take(80)}") }
                    val more = v.issues.size - 5
                    if (more > 0) appendLine("… $more more")
                }
                if (task.filesTouched.isNotEmpty()) {
                    appendLine()
                    appendLine("Files touched (${task.filesTouched.size}):")
                    task.filesTouched.take(6).forEach { appendLine("  $it") }
                    val more = task.filesTouched.size - 6
                    if (more > 0) appendLine("  … $more more")
                }
            }.trimEnd()
    }
}
