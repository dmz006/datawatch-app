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
 * Auto — PRD review screen.
 *
 * v0.40.1 (autonomous-tab parity for the car surface). Lists the
 * active server's PRDs in `needs_review` / `revisions_asked` so a
 * driver can hands-free approve / reject between drives without
 * pulling out the phone. Tap a row → [PrdActionScreen] (confirm
 * dialog with two big buttons + a back action).
 *
 * Hides itself behind the existing AutoSummaryScreen entry-point
 * — a 0-count list still renders a friendly "All caught up" row
 * so the driver sees a deterministic outcome rather than an empty
 * pane.
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
            val profile = profiles.firstOrNull { it.enabled } ?: run {
                error = "No enabled server"
                prds = emptyList()
                return
            }
            AutoServiceLocator.transportFor(profile).listPrds().fold(
                onSuccess = { dto ->
                    error = null
                    prds = dto.prds.filter {
                        val s = it.status.lowercase()
                        s == "needs_review" || s == "revisions_asked"
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
                    .addText(error ?: "No PRDs waiting for review.")
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
                                PrdActionScreen(carContext, p.id, body),
                            )
                        }
                        .build(),
                )
            }
        }
        return ListTemplate.Builder()
            .setTitle("PRDs to review")
            .setHeaderAction(Action.BACK)
            .setSingleList(builder.build())
            .build()
    }

    private companion object {
        const val POLL_MS: Long = 15_000L
    }
}
