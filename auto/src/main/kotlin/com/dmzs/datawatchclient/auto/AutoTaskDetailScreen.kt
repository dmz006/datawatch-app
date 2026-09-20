@file:Suppress("MagicNumber")

package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
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
 * Full task detail for Android Auto — depth 5 (Car App Library max).
 *
 * Shows task spec, status, error detail, verification summary, and retry count.
 * Actions: Requeue (requeuePrdTask — force-requeues regardless of status) and
 * Cancel Task (cancelPrdTask). No sub-navigation — this is the leaf node.
 *
 * Reached from AutoPrdStoriesScreen story-detail view (depth 4).
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

    override fun onGetTemplate(): Template {
        val builder = MessageTemplate.Builder(buildTaskBody(task))
            .setTitle(task.task.take(MAX_TITLE).ifBlank { "Task" })
            .setHeaderAction(Action.BACK)

        // MessageTemplate ActionStrip limit = 2 icon-only actions on MESSAGING path.
        // For failed state: Requeue + Cancel both go in addAction (2 allowed); strip stays at 2.
        // All strip actions must be icon-only — titled strip actions crash the MESSAGING session.
        when (task.status) {
            "failed" -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Requeue")
                        .setBackgroundColor(CarColor.YELLOW)
                        .setOnClickListener { fireRequeue() }
                        .build(),
                )
                builder.addAction(
                    Action.Builder()
                        .setTitle("Cancel")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { fireCancelTask() }
                        .build(),
                )
            }
            "pending", "in_progress" -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Cancel Task")
                        .setBackgroundColor(CarColor.RED)
                        .setOnClickListener { fireCancelTask() }
                        .build(),
                )
            }
        }

        val voiceIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_voice)).build()
        val speakerIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_auto_speaker)).build()
        val taskTitle = task.task.take(MAX_TITLE).ifBlank { "Task" }

        // Strip: speaker + voice — exactly 2 actions (MessageTemplate MESSAGING-path limit).
        builder.setActionStrip(
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setIcon(speakerIcon)
                        .setOnClickListener {
                            AutoTts.speak(carContext, task.spec.takeIf { it.isNotBlank() } ?: task.task)
                        }
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setIcon(voiceIcon)
                        .setOnClickListener {
                            screenManager.push(
                                VoiceRecordingScreen(
                                    carContext,
                                    sessionId = "",
                                    sessionTitle = taskTitle,
                                    prdId = prdId,
                                    taskId = task.id,
                                ),
                            )
                        }
                        .build(),
                )
                .build(),
        )

        return builder.build()
    }

    internal companion object {
        const val MAX_TITLE = 52
        const val MAX_ERROR = 200
        const val MAX_VERIFICATION_SUMMARY = 150
        const val MAX_ISSUE = 80
        const val MAX_ISSUES_SHOWN = 3

        fun buildTaskBody(task: PrdTaskDto): String =
            buildString {
                // Conversation format: [You] = task instruction, [datawatch] = result/status
                appendLine("[You]: ${task.task.take(MAX_TITLE)}")
                appendLine()

                val dwResponse = buildString {
                    append("Status: ${task.status.ifBlank { "unknown" }}")
                    if (task.retryCount > 0) append("  ·  Retries: ${task.retryCount}")
                }
                appendLine("[datawatch]: $dwResponse")

                task.error?.takeIf { it.isNotBlank() && task.status == "failed" }?.let { err ->
                    appendLine()
                    appendLine("Error:")
                    append(err.take(MAX_ERROR))
                    if (err.length > MAX_ERROR) append("…")
                    appendLine()
                }

                task.verification?.let { v ->
                    appendLine()
                    appendLine("Verification:")
                    v.summary?.takeIf { it.isNotBlank() }?.let {
                        appendLine(it.take(MAX_VERIFICATION_SUMMARY))
                    }
                    v.severity?.takeIf { it.isNotBlank() }?.let { appendLine("Severity: $it") }
                    if (v.issues.isNotEmpty()) {
                        v.issues.take(MAX_ISSUES_SHOWN).forEach { issue ->
                            appendLine("• ${issue.take(MAX_ISSUE)}")
                        }
                        val more = v.issues.size - MAX_ISSUES_SHOWN
                        if (more > 0) appendLine("… $more more")
                    }
                }
            }.trimEnd()
    }
}
