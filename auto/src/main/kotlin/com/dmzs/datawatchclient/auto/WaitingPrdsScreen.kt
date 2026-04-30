package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.transport.dto.PrdDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Auto — Autonomous plans screen (B69).
 *
 * v0.40.1 initial: needs_review + revisions_asked only.
 * v0.47.0: expanded to also show running plans so the driver can
 * cancel/stop them hands-free. Tap a row → [PrdActionScreen] which
 * renders Approve/Reject for review-state or Stop for running.
 */
public class WaitingPrdsScreen(carContext: CarContext) : Screen(carContext) {
    private var prds: List<PrdDto> = emptyList()
    private var error: String? = null
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    pollJob?.cancel()
                    pollJob = scope.launch { pollLoop() }
                }

                override fun onStop(owner: LifecycleOwner) {
                    pollJob?.cancel()
                    pollJob = null
                }
            },
        )
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            refresh()
            invalidate()
            delay(POLL_MS)
        }
    }

    private suspend fun refresh() {
        try {
            val profiles = AutoServiceLocator.profileRepository.observeAll().first()
            val profile =
                profiles.firstOrNull { it.enabled } ?: run {
                    error = "No enabled server"
                    prds = emptyList()
                    return
                }
            AutoServiceLocator.transportFor(profile).listPrds().fold(
                onSuccess = { dto ->
                    error = null
                    prds =
                        dto.prds.filter {
                            val s = it.status.lowercase()
                            s == "needs_review" || s == "revisions_asked" || s == "running"
                        }
                },
                onFailure = { err ->
                    error = "Unreachable: ${err.message ?: err::class.simpleName}"
                },
            )
        } catch (e: Throwable) {
            error = "Error: ${e.message ?: e::class.simpleName}"
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ItemList.Builder()
        if (prds.isEmpty()) {
            builder.addItem(
                Row.Builder()
                    .setTitle(if (error != null) "Error" else "All caught up")
                    .addText(error ?: "No autonomous plans active.")
                    .build(),
            )
        } else {
            prds.forEach { p ->
                val body =
                    p.title?.takeIf { it.isNotBlank() }
                        ?: p.name.takeIf { it.isNotBlank() }
                        ?: p.id
                val sub =
                    "${p.status.replace('_', ' ')} · " +
                        "${p.stories.size} stories"
                builder.addItem(
                    Row.Builder()
                        .setTitle(body)
                        .addText(sub)
                        .setOnClickListener {
                            screenManager.push(
                                PrdActionScreen(carContext, p.id, body, p.status),
                            )
                        }
                        .build(),
                )
            }
        }
        return ListTemplate.Builder()
            .setTitle("Autonomous plans")
            .setHeaderAction(Action.BACK)
            .setSingleList(builder.build())
            .build()
    }

    private companion object {
        const val POLL_MS: Long = 15_000L
    }
}
