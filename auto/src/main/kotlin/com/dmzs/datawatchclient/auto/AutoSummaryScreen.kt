package com.dmzs.datawatchclient.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Driver-safe session summary for Android Auto. Reads live session
 * counts from the active [AutoServiceLocator] profile every 15 s.
 * Tapping "Waiting input" opens [WaitingSessionsScreen]. The action
 * strip exposes server picker, monitor and about screens — drivers
 * get a consistent datawatch entry point without leaving the car UI.
 *
 * ADR-0031 Play-compliance: static ListTemplate only, no free-form
 * text input from the driver surface.
 */
public class AutoSummaryScreen(carContext: CarContext) : Screen(carContext) {
    private var running: Int = 0
    private var waiting: Int = 0
    private var total: Int = 0
    private var activeProfile: ServerProfile? = null
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

    private companion object {
        const val POLL_MS: Long = 15_000L
    }

    private suspend fun refresh() {
        try {
            val profile =
                resolveActiveProfile() ?: run {
                    error = "No enabled server (configure on phone)"
                    activeProfile = null
                    running = 0
                    waiting = 0
                    total = 0
                    return
                }
            activeProfile = profile
            AutoServiceLocator.transportFor(profile).listSessions().fold(
                onSuccess = { list ->
                    error = null
                    running = list.count { it.state == SessionState.Running }
                    waiting = list.count { it.state == SessionState.Waiting }
                    total = list.size
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
        // Active server header — first row so the driver knows which
        // fleet they're looking at before seeing the counts.
        val profile = activeProfile
        if (profile != null) {
            builder.addItem(
                Row.Builder()
                    .setTitle(colored("● ${profile.displayName}", CarColor.GREEN))
                    .addText(profile.baseUrl)
                    .build(),
            )
        }
        val runningText =
            CarText.Builder("$running sessions")
                .addVariant("$running")
                .build()
        builder.addItem(
            Row.Builder()
                .setTitle(colored("Running", CarColor.GREEN))
                .addText(runningText)
                .build(),
        )
        builder.addItem(
            Row.Builder()
                .setTitle(colored("Waiting input", CarColor.YELLOW))
                .addText("$waiting sessions")
                .setOnClickListener {
                    screenManager.push(WaitingSessionsScreen(carContext))
                }
                .build(),
        )
        builder.addItem(
            Row.Builder()
                .setTitle("Total")
                .addText("$total sessions")
                .build(),
        )
        builder.addItem(
            Row.Builder()
                .setTitle(colored("Autonomous", CarColor.YELLOW))
                .addText("Review, approve, or stop plans")
                .setOnClickListener {
                    screenManager.push(WaitingPrdsScreen(carContext))
                }
                .build(),
        )
        val title = "datawatch ${Version.VERSION}"
        val actionStrip =
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle("Server")
                        .setOnClickListener {
                            screenManager.push(AutoServerPickerScreen(carContext))
                        }
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setTitle("Monitor")
                        .setOnClickListener {
                            screenManager.push(AutoMonitorScreen(carContext))
                        }
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setTitle("About")
                        .setOnClickListener {
                            screenManager.push(AutoAboutScreen(carContext))
                        }
                        .build(),
                )
                .build()
        return ListTemplate.Builder()
            .setTitle(error?.let { "$title · $it" } ?: title)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .setSingleList(builder.build())
            .build()
    }
}

/**
 * Resolve the profile the user most recently picked via the car (or
 * phone) picker. Falls back to the first enabled profile so a
 * fresh install still shows something useful.
 */
internal suspend fun resolveActiveProfile(): ServerProfile? {
    val profiles = AutoServiceLocator.profileRepository.observeAll().first()
    val activeId = AutoServiceLocator.activeServerStore.get()
    val picked = profiles.firstOrNull { it.id == activeId && it.enabled }
    return picked ?: profiles.firstOrNull { it.enabled }
}

/**
 * Apply a datawatch accent colour to a car row's title. The Car App
 * Library allows a narrow subset of [CarColor] spans; GREEN / YELLOW
 * mirror the PWA's "running / waiting" palette exactly.
 */
internal fun colored(
    text: String,
    color: CarColor,
): CarText {
    val spannable = android.text.SpannableString(text)
    spannable.setSpan(
        ForegroundCarColorSpan.create(color),
        0,
        text.length,
        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    return CarText.create(spannable)
}

/** Reusable datawatch-brand icon for template headers. */
internal fun brandIcon(carContext: CarContext): CarIcon? =
    runCatching {
        val resId =
            carContext.resources.getIdentifier(
                "ic_launcher",
                "mipmap",
                carContext.packageName,
            )
        if (resId != 0) {
            CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
        } else {
            null
        }
    }.getOrNull()
