package com.dmzs.datawatchclient.ui.debug

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import com.dmzs.datawatchclient.BuildConfig
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.ui.DeepLinks
import com.dmzs.datawatchclient.ui.shell.Destinations
import com.dmzs.datawatchclient.ui.shell.LastViewStore
import com.dmzs.datawatchclient.ui.theme.ThemeMode
import com.dmzs.datawatchclient.ui.theme.ThemePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * DEBUG-build-only launch hooks for emulator screenshot passes against a demo
 * server (scripts/screenshots/capture-android.sh). Android twin of iOS
 * `DebugLaunchHooks.swift`. Inert unless the build is debuggable
 * ([BuildConfig.DEBUG] and FLAG_DEBUGGABLE), so release builds ignore every extra.
 *
 *   --es dwSeedURL https://<host>:<port>  add a trust-all profile once (by URL)
 *   --es dwSeedToken <token>              bearer token, stored via TokenVault
 *   --es dwSeedName <name>                display name (default "sandbox")
 *   --es dwTab sessions|automata|alerts|observer|dashboard|settings
 *   --es dwTheme dark|light|system
 *   --es dwOpenSession <id>               open that session (≥ 600 dp: in the detail
 *                                         pane beside the dwTab tab)
 *   --es dwOpenAutomaton <id>             open that Automaton's detail
 *   --ez dwSkipNotifPrompt true           accepted for iOS parity; the app never
 *                                         prompts at launch (grant via `pm grant`)
 *
 * Tab / theme extras are read in Activity.onCreate before the first
 * composition, so launch cold (`am start -S`). Values come from the adb
 * command line only; nothing is stored in the repo.
 */
public object DebugLaunchHooks {
    private const val TAG = "DebugLaunchHooks"

    /** Automaton id for AutonomousScreen to open once its list has loaded it. */
    public val pendingAutomaton: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Session id for the two-pane (≥ 600 dp) Home shell's detail pane. */
    public val pendingWideSession: MutableStateFlow<String?> = MutableStateFlow(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tabs: Map<String, String> =
        mapOf(
            "sessions" to Destinations.Tabs.Sessions,
            "automata" to Destinations.Tabs.Autonomous,
            "alerts" to Destinations.Tabs.Alerts,
            "observer" to Destinations.Tabs.Observer,
            "dashboard" to Destinations.Tabs.Dashboard,
            "settings" to Destinations.Tabs.Settings,
        )

    private fun enabled(context: Context): Boolean =
        BuildConfig.DEBUG && (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    public fun apply(
        context: Context,
        intent: Intent?,
    ) {
        if (intent == null || !enabled(context)) return
        intent.getStringExtra("dwTheme")?.let { t ->
            ThemeMode.entries.firstOrNull { it.name.equals(t, ignoreCase = true) }
                ?.let { ThemePrefs.save(context, it) }
        }
        val session = intent.getStringExtra("dwOpenSession")
        val tab = intent.getStringExtra("dwTab")?.let { tabs[it.lowercase()] }
        if (tab != null || session != null) {
            LastViewStore.setLastTab(context, tab ?: Destinations.Tabs.Sessions)
            // A restored open session would cover the requested tab.
            LastViewStore.setLastSession(context, null)
        }
        if (session != null) {
            if (context.resources.configuration.screenWidthDp >= 600) {
                // Two-pane: the detail pane shows it next to whichever tab dwTab picked.
                pendingWideSession.value = session
            } else {
                DeepLinks.pendingSessionTarget.tryEmit(session)
            }
        }
        intent.getStringExtra("dwOpenAutomaton")?.let { pendingAutomaton.value = it }
        intent.getStringExtra("dwSeedURL")?.let { url ->
            seed(url.trim().trimEnd('/'), intent.getStringExtra("dwSeedToken"), intent.getStringExtra("dwSeedName"))
        }
    }

    /** Returns and clears the pending Automaton id once [loadedIds] contains it. */
    public fun takeAutomaton(loadedIds: List<String>): String? {
        val id = pendingAutomaton.value ?: return null
        if (id !in loadedIds) return null
        pendingAutomaton.value = null
        return id
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun seed(
        url: String,
        token: String?,
        name: String?,
    ) {
        scope.launch {
            runCatching {
                val repo = ServiceLocator.profileRepository
                if (repo.observeAll().first().any { it.baseUrl == url }) return@launch
                val id = "srv-${Uuid.random().toString().take(8)}"
                val alias = if (token.isNullOrBlank()) "" else ServiceLocator.tokenVault.put(id, token)
                repo.upsert(
                    ServerProfile(
                        id = id,
                        displayName = name?.takeIf { it.isNotBlank() } ?: "sandbox",
                        baseUrl = url,
                        bearerTokenRef = alias,
                        trustAnchorSha256 = ServiceLocator.TRUST_ALL_SENTINEL,
                        reachabilityProfileId = "lan-default",
                        enabled = true,
                        createdTs = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
                Log.i(TAG, "seeded profile ${name ?: "sandbox"}")
            }.onFailure { Log.w(TAG, "seed failed: ${it.message}") }
        }
    }
}
