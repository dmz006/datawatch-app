package com.dmzs.datawatchclient.ui.settings

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.docs.DocsLinks
import com.dmzs.datawatchclient.domain.ServerInfo
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.push.AlertTier
import com.dmzs.datawatchclient.push.AlertTierDetector
import com.dmzs.datawatchclient.transport.TransportError
import com.dmzs.datawatchclient.ui.alerts.AlertsViewModel
import com.dmzs.datawatchclient.ui.common.AlertsBellAction
import com.dmzs.datawatchclient.ui.common.DocsLinkAction
import com.dmzs.datawatchclient.ui.common.DocsViewerSheet
import com.dmzs.datawatchclient.ui.common.ReachabilityDot
import com.dmzs.datawatchclient.ui.common.SingleServerPickerTitle
import com.dmzs.datawatchclient.ui.compute.ComputeNodesCard
import com.dmzs.datawatchclient.ui.compute.LlmRegistryCard
import com.dmzs.datawatchclient.ui.shell.AlertDockChannel
import com.dmzs.datawatchclient.ui.shell.DockLevel
import com.dmzs.datawatchclient.ui.splash.MatrixLogoAnimated
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Settings — mirrors the PWA's Settings structure (Servers / Comms / About)
 * at the card level. Animated logo sits in About as a live visual, not behind
 * a "replay" action — matches user feedback that the splash should always be
 * shown, not an opt-in.
 *
 * Feature-parity with PWA is tracked upstream in
 * [dmz006/datawatch#4](https://github.com/dmz006/datawatch/issues/4).
 */

/**
 * Sub-tabs — order matches PWA v7.0.0-alpha.12:
 * **Monitor · General · Plugins · Comms · Compute · Automata · About**.
 * PWA stashes the default active tab in `localStorage.cs_settings_tab = 'monitor'`
 * so users land on Monitor first. About is mobile-only (no PWA equivalent).
 */
private enum class SettingsTab(
    @StringRes val labelRes: Int,
    /** BL414 — header "?" opens the manual's section for this tab. */
    val docsKey: String,
) {
    General(R.string.settings_tab_general, "view_settings_general"),
    Plugins(R.string.settings_tab_plugins, "view_settings_plugins"),
    Comms(R.string.settings_tab_comms, "view_settings_comms"),
    Compute(R.string.settings_tab_compute, "view_settings_compute"),
    Automata(R.string.settings_tab_automata, "view_settings_automata"),
    About(R.string.settings_tab_about, "view_settings_about"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SettingsScreen(
    onAddServer: () -> Unit = {},
    onEditServer: (String) -> Unit = {},
    onNavigateToObserver: () -> Unit = {},
    alertsVm: AlertsViewModel = viewModel(),
) {
    val profiles by ServiceLocator.profileRepository.observeAll()
        .collectAsState(initial = emptyList())
    val activeId by ServiceLocator.activeServerStore.observe()
        .collectAsState(initial = null)
    // #234 — real profiles + proxied remotes for the active selection and
    // the title picker; `profiles` (real only) stays for the Servers card.
    val withProxied by ServiceLocator.profilesWithProxied()
        .collectAsState(initial = emptyList())
    val activeProfile: ServerProfile? =
        remember(withProxied, activeId) {
            val enabled = withProxied.filter { it.enabled }
            if (activeId == ActiveServerStore.SENTINEL_ALL_SERVERS) {
                enabled.firstOrNull()
            } else {
                enabled.firstOrNull { it.id == activeId } ?: enabled.firstOrNull()
            }
        }
    val alertsState by alertsVm.state.collectAsState()
    val transport = remember(activeProfile) { activeProfile?.let { ServiceLocator.transportFor(it) } }
    val reachable by remember(transport) {
        transport?.isReachable?.map { it as Boolean? } ?: flowOf<Boolean?>(null)
    }.collectAsState(initial = null)
    var lastProbeMs by remember { mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(
        reachable,
    ) { if (reachable == true) lastProbeMs = System.currentTimeMillis() }

    val prefs = LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val storedTab = prefs.getString("settings_active_tab", null)
    val migratedTab =
        when (storedTab) {
            "llm", "agents" -> SettingsTab.Compute
            "plugins" -> SettingsTab.Plugins
            "comms" -> SettingsTab.Comms
            "automata" -> SettingsTab.Automata
            "monitor" -> SettingsTab.General // Monitor removed; redirect to General
            "about" -> SettingsTab.About
            else -> SettingsTab.General
        }
    var activeTab by remember { mutableStateOf(migratedTab) }
    var pickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        // Tab screens sit inside the app shell Scaffold, which already
        // reserves the system bars + bottom nav; the default systemBars
        // insets here doubled the nav-bar gap above the bottom menu.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    SingleServerPickerTitle(
                        active = activeProfile,
                        open = pickerOpen,
                        onToggle = { pickerOpen = !pickerOpen },
                        onDismiss = { pickerOpen = false },
                        profiles = withProxied.filter { it.enabled },
                        onSelect = { id ->
                            ServiceLocator.activeServerStore.set(id)
                            pickerOpen = false
                        },
                    )
                },
                actions = {
                    DocsLinkAction(DocsLinks.forKey(activeTab.docsKey))
                    AlertsBellAction(alertsBadge = alertsState.watchedAlertCount)
                    if (activeProfile != null) {
                        ReachabilityDot(
                            reachable = reachable,
                            lastProbeEpochMs = lastProbeMs,
                            onRetry = {},
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxWidth(),
        ) {
            val dw = LocalDatawatchColors.current
            ScrollableTabRow(
                selectedTabIndex = activeTab.ordinal,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = dw.accent2,
                // Underline the selected tab in PWA accent2, matching
                // `.nav-btn.active` border-top-color.
                indicator = { tabPositions ->
                    if (activeTab.ordinal < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab.ordinal]),
                            color = dw.accent2,
                        )
                    }
                },
            ) {
                SettingsTab.entries.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = {
                            activeTab = tab
                            prefs.edit().putString("settings_active_tab", tab.name.lowercase()).apply()
                        },
                        selectedContentColor = dw.accent2,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            // Shrink Settings typography to match PWA's dense
            // 13px / 11px `.settings-row` rhythm. Default
            // MaterialTheme bodyLarge/titleMedium render at 16sp
            // which looks oversized vs the web UI (S3 report).
            // Isolated to Settings — doesn't shift typography in
            // Sessions / Chat / Terminal.
            val settingsTypography =
                pwaSettingsTypography(MaterialTheme.typography)
            androidx.compose.material3.MaterialTheme(
                colorScheme = MaterialTheme.colorScheme,
                typography = settingsTypography,
            ) {
                // LocalTextStyle drives OutlinedTextField / OutlinedButton
                // default text rendering — without overriding it those
                // widgets keep the outer 16sp bodyLarge even inside our
                // shrunken MaterialTheme. Providing a 13sp default here
                // lines up every input to PWA's `.form-input` density.
                CompositionLocalProvider(
                    LocalTextStyle provides TextStyle(fontSize = 13.sp),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .fillMaxWidth(),
                    ) {
                        // Restart-needed banner — visible on every Settings tab
                        // when `server.auto_restart_on_config` is false.
                        // ConfigFieldsPanel auto-saves on every change, and the
                        // server writes to config.yaml synchronously, but many
                        // fields (TLS, bind interface, backend configs, etc.)
                        // only take effect on daemon restart. With auto-restart
                        // off the user has no signal that their save hasn't yet
                        // activated — hence this always-visible affordance.
                        // User-flagged 2026-04-24: "make sure settings tab saves
                        // changes and restarts as needed and settings actually
                        // work."
                        RestartNeededBanner(activeProfile)

                        when (activeTab) {
                            SettingsTab.General -> {
                                // v0.59.0 — General mirrors PWA v6.5.1 GENERAL tab:
                                // Datawatch → Auto-Update → Session → Whisper →
                                // Project Profiles → Cluster Profiles → Notifications.
                                // Pipelines / Autonomous / Orchestrator / Agents →
                                // Automata tab. Plugins → Plugins tab.
                                SecurityCard()
                                RawConfigCard()
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Datawatch,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.AutoUpdate,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Session,
                                )
                                com.dmzs.datawatchclient.ui.general.SummarizerCard()
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Whisper,
                                )
                                com.dmzs.datawatchclient.ui.voice.TestWhisperCard()
                                com.dmzs.datawatchclient.ui.notifications.NotificationsCard()
                                // v0.75.0 S6-4 (#84, #85): Docs Search card.
                                DocsSearchCard()
                                // v0.82.0 Sprint 13 — General tab gaps
                                com.dmzs.datawatchclient.ui.general.SessionTemplatesCard()
                                com.dmzs.datawatchclient.ui.general.DeviceAliasesCard()
                                com.dmzs.datawatchclient.ui.general.ToolingCard()
                                // T30 — File Service + Discussion Scopes
                                FileServiceCard()
                                DiscussionScopesCard()
                            }
                            SettingsTab.Comms -> {
                                // Matches PWA `data-group="comms"` order:
                                // Authentication → Servers → cc_* → Proxy →
                                // Communication Configuration (channels).
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.CommsAuth,
                                )
                                ServersCard(
                                    profiles = profiles,
                                    onAddServer = onAddServer,
                                    onEditServer = onEditServer,
                                    onDelete = { profile ->
                                        GlobalScope.launch(Dispatchers.IO) {
                                            if (profile.bearerTokenRef.isNotBlank()) {
                                                ServiceLocator.tokenVault.remove(profile.bearerTokenRef)
                                            }
                                            ServiceLocator.profileRepository.delete(profile.id)
                                        }
                                    },
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.WebServer,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.McpServer,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Proxy,
                                )
                                // v0.80.0 Sprint 11 — RoutingRulesCard after Proxy (PWA parity)
                                com.dmzs.datawatchclient.ui.routing.RoutingRulesCard()
                                com.dmzs.datawatchclient.ui.channels.ChannelsCard()
                                // T30 — Channel Routing
                                com.dmzs.datawatchclient.ui.routing.ChannelRoutingCard()
                                com.dmzs.datawatchclient.ui.federation.FederationPeersCard()
                                PushNotificationsCard()
                                com.dmzs.datawatchclient.ui.cert.CertInstallCard()
                            }
                            SettingsTab.Compute -> {
                                // LLMs + ComputeNodes first — most-used section on Compute tab.
                                LlmRegistryCard()
                                var computeNodesRefreshTick by remember { mutableStateOf(0) }
                                ComputeNodesCard(
                                    onNodeDeleted = { computeNodesRefreshTick++ },
                                )
                                com.dmzs.datawatchclient.ui.compute.CostRatesCard()
                                com.dmzs.datawatchclient.ui.profiles.KindProfilesCard(
                                    kind = "cluster",
                                    title = stringResource(R.string.settings_cluster_profiles_title),
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Memory,
                                )
                                // PWA LLM config order: memory → goose → opencode → web_search → rtk → vision.
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Goose,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.OpenCode,
                                )
                                // PWA web_search section (registry-wide toggles) above the providers card.
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.WebSearch,
                                )
                                // BL391: replaced single-provider config panel with multi-provider registry card
                                com.dmzs.datawatchclient.ui.websearch.WebSearchRegistryCard()
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.LlmRtk,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Vision,
                                )
                                // Container Workers (cfg.agents)
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Agents,
                                )
                                com.dmzs.datawatchclient.ui.detection.DetectionFiltersCard()
                                // S14b — Alert Rules (PWA Settings > Compute parity)
                                com.dmzs.datawatchclient.ui.alertrules.AlertRulesCard()
                                com.dmzs.datawatchclient.ui.commands.SavedCommandsCard()
                                com.dmzs.datawatchclient.ui.filters.FiltersCard()
                                com.dmzs.datawatchclient.ui.tailscale.TailscaleSettingsCard()
                                com.dmzs.datawatchclient.ui.tailscale.TailscaleMeshCard()
                                // PWA Settings → Compute: BL356 Exit Hooks + BL357 Work Queue.
                                ExitHooksCard()
                                WorkQueueCard()
                                // v0.88.0 Sprint 19 (#111) — alpha.25 settings move
                                SecretsCard()
                                // PWA alpha.25 #230 — Observer quicklink moved from General → Compute
                                com.dmzs.datawatchclient.ui.general.ObserverQuicklinkCard(
                                    onNavigateToMonitor = onNavigateToObserver,
                                )
                            }
                            SettingsTab.Automata -> {
                                // Order mirrors PWA v8.6.0 Settings → Automata tab
                                com.dmzs.datawatchclient.ui.settings.IdentityCard()
                                com.dmzs.datawatchclient.ui.settings.AlgorithmModeCard()
                                com.dmzs.datawatchclient.ui.settings.EvalsCard()
                                com.dmzs.datawatchclient.ui.settings.CouncilCard()
                                com.dmzs.datawatchclient.ui.profiles.KindProfilesCard(
                                    kind = "project",
                                    title = stringResource(R.string.settings_project_profiles_title),
                                )
                                com.dmzs.datawatchclient.ui.automata.PipelineManagerCard()
                                com.dmzs.datawatchclient.ui.automata.OrchestratorGraphsCard()
                                // Guardrail Library (PWA: automata_scan + automata_guardrail_profiles)
                                ScanConfigCard()
                                AutonomousConfigCard()
                                GuardrailLibraryCard()
                                // Autonomous config + Skill Registries
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Autonomous,
                                )
                                SkillRegistriesCard()
                                // Below: extra config panels and registry types not in PWA main flow
                                AutomataTypesCard()
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Pipelines,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Orchestrator,
                                )
                            }
                            SettingsTab.Plugins -> {
                                // v0.59.0 — mirrors PWA v6.5.1 Plugins tab
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Plugins,
                                )
                                // PWA BL238 Plugin Manager: installed list + enable/disable + reload.
                                com.dmzs.datawatchclient.ui.plugins.InstalledPluginsCard()
                                // v8.1.0 issue #134 — community registry browse + install
                                com.dmzs.datawatchclient.ui.plugins.CommunityPluginsCard()
                            }
                            SettingsTab.About -> {
                                AboutCard(activeProfile = activeProfile)
                                LanguagePickerCard()
                                ThemePickerCard()
                                com.dmzs.datawatchclient.ui.about.ApiLinksCard()
                                com.dmzs.datawatchclient.ui.about.McpChannelCard()
                                com.dmzs.datawatchclient.ui.about.McpToolsCard()
                                com.dmzs.datawatchclient.ui.ops.UpdateDaemonCard()
                                com.dmzs.datawatchclient.ui.ops.SubsystemReloadCard()
                                com.dmzs.datawatchclient.ui.ops.RestartDaemonCard()
                                com.dmzs.datawatchclient.ui.ops.KillOrphansCard()
                                EncryptionStatusCard()
                            }
                        }
                    }
                } // end LocalTextStyle provider
            } // end settings-scale MaterialTheme
        }
    }
}

/**
 * Dense Settings typography — bodyLarge / bodyMedium / titleMedium
 * each drop ~2-3sp from Material3 defaults. Matches the PWA's
 * `.settings-row` 13px rhythm. Other scales (label / display /
 * headline) inherit unchanged so banners, chips, and app-bar text
 * still feel right. Kept inline to avoid leaking into non-Settings
 * surfaces (sessions / chat use the app-wide typography).
 */
private fun pwaSettingsTypography(base: Typography): Typography =
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        titleLarge = base.titleLarge.copy(fontSize = 18.sp, lineHeight = 22.sp),
        titleMedium = base.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
        titleSmall = base.titleSmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
        labelLarge = base.labelLarge.copy(fontSize = 13.sp, lineHeight = 17.sp),
    )

@Composable
private fun ServersCard(
    profiles: List<com.dmzs.datawatchclient.domain.ServerProfile>,
    onAddServer: () -> Unit,
    onEditServer: (String) -> Unit,
    onDelete: (com.dmzs.datawatchclient.domain.ServerProfile) -> Unit,
) {
    SectionWithAction(
        id = "servers",
        title = "Servers",
        actionIcon = Icons.Filled.Add,
        actionDescription = "Add server",
        onAction = onAddServer,
    ) {
        if (profiles.isEmpty()) {
            Text(
                "No servers yet — tap + above to add one.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            profiles.forEach { p ->
                ServerRow(
                    profile = p,
                    onEdit = { onEditServer(p.id) },
                    onDelete = { onDelete(p) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ServerRow(
    profile: ServerProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                profile.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val badges =
                buildList {
                    if (profile.bearerTokenRef.isBlank()) add("no auth")
                    if (profile.trustAnchorSha256 == ServiceLocator.TRUST_ALL_SENTINEL) {
                        add("trust-all TLS")
                    }
                }
            if (badges.isNotEmpty()) {
                Text(
                    badges.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // Parity D91a — certificate pin set (TOFU): a positive security badge.
            if (ServiceLocator.pinFor(profile) != null) {
                Text(
                    "🔒 " + stringResource(R.string.settings_profile_pinned_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.success,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Download CA cert") },
                    leadingIcon = {
                        Icon(Icons.Filled.Download, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        scope.launch {
                            downloadAndInstallCert(context, profile)
                        }
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "Delete server",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/**
 * Fetch the CA cert from the profile's server, save it to the public Downloads
 * folder under `datawatch-<displayName>-ca.pem`, and open Android's system
 * "Install a certificate" screen so the user can finish trust-anchor install
 * themselves. We cannot silently trust-anchor on unrooted Android — by design.
 *
 * NotFound → toast "Server doesn't support /api/cert". Other errors surface as
 * a short toast with the underlying message.
 */
private suspend fun downloadAndInstallCert(
    context: Context,
    profile: ServerProfile,
) {
    val transport = ServiceLocator.transportFor(profile)
    val result = transport.fetchCert()
    result.fold(
        onSuccess = { bytes ->
            val filename = "datawatch-${profile.displayName.sanitizeForFilename()}-ca.pem"
            val saved = savePemToDownloads(context, filename, bytes)
            if (saved) {
                AlertDockChannel.post(
                    "Saved to Downloads as $filename. Opening system trust-anchor screen…",
                    DockLevel.Success,
                )
                // Hand off to the OS flow. User picks the PEM from Downloads.
                val intent =
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        AlertDockChannel.post(
                            "Couldn't open security settings — install manually from Downloads.",
                            DockLevel.Error,
                        )
                    }
            } else {
                AlertDockChannel.post(
                    "Downloaded cert but couldn't save to Downloads.",
                    DockLevel.Error,
                )
            }
        },
        onFailure = { err ->
            val msg =
                when (err) {
                    is TransportError.NotFound ->
                        "Server doesn't expose /api/cert (parent-repo support pending)."
                    else -> "Cert download failed — ${err.message ?: err::class.simpleName}"
                }
            AlertDockChannel.post(
                msg,
                DockLevel.Error,
            )
        },
    )
}

private fun String.sanitizeForFilename(): String = replace(Regex("[^A-Za-z0-9._-]+"), "_").take(48).ifBlank { "server" }

/**
 * Save PEM bytes to the public Downloads folder under `Download/datawatch/`.
 * Android Q+ goes through MediaStore (scoped storage); pre-Q falls back to
 * direct write against [Environment.DIRECTORY_DOWNLOADS].
 */
private fun savePemToDownloads(
    context: Context,
    filename: String,
    bytes: ByteArray,
): Boolean {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "application/x-pem-file")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/datawatch")
                }
            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching false
            true
        } else {
            @Suppress("DEPRECATION")
            val downloads =
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS,
                )
            val dir = File(downloads, "datawatch").apply { mkdirs() }
            FileOutputStream(File(dir, filename)).use { it.write(bytes) }
            true
        }
    }.getOrDefault(false)
}

@Composable
private fun SecurityCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gate = remember { com.dmzs.datawatchclient.security.BiometricGate(context) }
    val keystore = remember { com.dmzs.datawatchclient.security.KeystoreManager(context) }
    var enabled by remember { mutableStateOf(gate.enabled()) }
    val canAuth = remember { gate.canAuthenticate(context) }
    var migrating by remember { mutableStateOf(false) }
    var migrationError by remember { mutableStateOf<String?>(null) }
    val migrationFailedFmt = stringResource(R.string.security_migration_failed)

    Section(id = "security", title = "Security") {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.security_biometric_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (canAuth) {
                        stringResource(R.string.security_biometric_desc)
                    } else {
                        stringResource(R.string.security_biometric_unavailable)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                migrationError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = { newValue ->
                    migrationError = null
                    val activity =
                        context as? androidx.fragment.app.FragmentActivity ?: return@Switch
                    migrating = true
                    gate.prompt(
                        activity = activity,
                        onSuccess = {
                            runCatching {
                                if (newValue) {
                                    val passphrase = keystore.deriveDatabasePassphrase()
                                    keystore.migratePassphraseToBiometricKey(passphrase)
                                    passphrase.fill(0)
                                } else {
                                    keystore.migratePassphraseFromBiometricKey()
                                }
                                gate.setEnabled(newValue)
                                enabled = newValue
                            }.onFailure { e ->
                                migrationError = migrationFailedFmt.format(e.message ?: "")
                            }
                            migrating = false
                        },
                        onFailure = { msg ->
                            migrationError = msg
                            migrating = false
                        },
                    )
                },
                enabled = canAuth && !migrating,
            )
        }
    }
}

@Composable
private fun CommsCard() {
    Section(id = "comms", title = "Comms") {
        Text(
            "Messaging channel configuration will land in Sprint 3 (see " +
                "docs/plans/README.md F3). This card will mirror the PWA's " +
                "Settings → Comms tab — Signal / Telegram / Slack / ntfy / Matrix etc.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutCard(activeProfile: ServerProfile?) {
    var serverInfo by remember(activeProfile?.id) { mutableStateOf<ServerInfo?>(null) }
    var serverInfoError by remember(activeProfile?.id) { mutableStateOf<String?>(null) }
    // v0.33.13 (B25) — compact sessions-details footer on About.
    // Shows total / running / waiting + uptime sourced from
    // `/api/stats`; single-shot fetch tied to the active profile.
    var stats by remember(activeProfile?.id) {
        mutableStateOf<com.dmzs.datawatchclient.transport.dto.StatsDto?>(null)
    }
    var docsUrl by remember { mutableStateOf<String?>(null) }

    // Refresh daemon info when the active profile changes. Failure is tolerated
    // — the card shows an em-dash fallback, not a banner.
    LaunchedEffect(activeProfile?.id) {
        val profile = activeProfile ?: return@LaunchedEffect
        val transport = ServiceLocator.transportFor(profile)
        transport.fetchInfo().fold(
            onSuccess = { info ->
                serverInfo = info
                serverInfoError = null
            },
            onFailure = { err ->
                serverInfo = null
                serverInfoError = err.message ?: err::class.simpleName
            },
        )
        transport.stats().onSuccess { stats = it }
    }

    // PWA About header is a plain (non-collapsible) title (app.js renderSettingsView).
    Section(id = "about", title = "About", collapsible = false) {
        // S6-6 (#87): normalized to 12dp horizontal / 8dp vertical per pwaCard standard.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Live animated logo (matrix rain + eye + arcs + tablet frame).
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    MatrixLogoAnimated(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("App version", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${Version.VERSION}  (build ${Version.VERSION_CODE} · " +
                        "${com.dmzs.datawatchclient.BuildConfig.GIT_SHA})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // S10-4 — alert tier indicator
            val alertContext = LocalContext.current
            val tier = remember { AlertTierDetector.resolve(alertContext) }
            val tierText =
                when (tier) {
                    AlertTier.UnifiedPush -> stringResource(R.string.alert_tier_unified_push)
                    AlertTier.CommChannel -> stringResource(R.string.alert_tier_comm_channel)
                    AlertTier.Background -> stringResource(R.string.alert_tier_background)
                }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        when (tier) {
                            AlertTier.UnifiedPush -> Icons.Filled.NotificationsActive
                            AlertTier.CommChannel -> Icons.Filled.SignalCellularAlt
                            AlertTier.Background -> Icons.Filled.NotificationsNone
                        },
                    contentDescription = null,
                    tint =
                        when (tier) {
                            AlertTier.UnifiedPush -> Color(0xFF00C853)
                            AlertTier.CommChannel -> Color(0xFF00BCD4)
                            AlertTier.Background -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(tierText, style = MaterialTheme.typography.bodySmall)
            }
            DaemonInfoRow(
                activeProfile = activeProfile,
                serverInfo = serverInfo,
                error = serverInfoError,
            )
            // v0.42.12 — relabel + trim to match PWA About card
            // (app.js:4233-4277). Drop "Package" + "License" rows
            // (PWA carries neither); rename "Parent project" →
            // "Project" and "Source" → "Mobile app" to match PWA's
            // labels exactly.
            val context = LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dmz006/datawatch"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Project", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "github.com/dmz006/datawatch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dmz006/datawatch-app"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Mobile app", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "github.com/dmz006/datawatch-app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.dmzs.datawatchclient"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Play Store", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "play.google.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // S6-1 (#71): single docs link — opens in-app DocsViewerSheet.
            TextButton(
                onClick = {
                    // Docs are server-local: a proxied remote (#234) opens its parent's.
                    val docsBase =
                        activeProfile?.let {
                            com.dmzs.datawatchclient.transport.ProxiedServers.docsBaseUrl(it)
                        }
                    docsUrl = "$docsBase/diagrams.html"
                },
            ) {
                Text(stringResource(R.string.about_docs_link))
            }
            // v0.33.13 (B25) sessions-details footer. Sourced from
            // `/api/stats` alongside the daemon-info fetch so the
            // About card communicates server activity at a glance.
            stats?.let { s ->
                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    color = LocalDatawatchColors.current.border,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Sessions", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${s.sessionsTotal} total · ${s.sessionsRunning} running · ${s.sessionsWaiting} waiting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Uptime", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatUptime(s.uptimeSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    docsUrl?.let { url ->
        DocsViewerSheet(
            url = url,
            onDismiss = { docsUrl = null },
            allowSelfSigned = activeProfile?.trustAnchorSha256 == ServiceLocator.TRUST_ALL_SENTINEL,
            pinSha256 = ServiceLocator.pinFor(activeProfile),
        )
    }
}

private fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3_600
    val m = (seconds % 3_600) / 60
    return buildString {
        if (d > 0) append("${d}d ")
        if (d > 0 || h > 0) append("${h}h ")
        append("${m}m")
    }
}

/**
 * Renders "Connected to <hostname> · datawatch vX.Y.Z". Em-dash fallback when
 * the active profile is null or the /api/info call hasn't landed yet. On a
 * NotFound (server predates /api/info) we fall back to "—" silently; older
 * servers shouldn't make the About card louder than it already is.
 */
@Composable
private fun DaemonInfoRow(
    activeProfile: ServerProfile?,
    serverInfo: ServerInfo?,
    error: String?,
) {
    // PWA loadVersionInfo (aboutVersion): server version as `vX.Y.Z` in accent2,
    // linking to its GitHub release. Fallbacks keep the em-dash style.
    val ver = serverInfo?.version?.trim().orEmpty()
    val tag: String? =
        if (ver.isNotEmpty() && ver != "?") (if (ver.startsWith("v")) ver else "v$ver") else null
    val fallback =
        when {
            activeProfile == null -> "No active server"
            error != null -> "— (${activeProfile.displayName} unreachable)"
            serverInfo != null -> "—"
            else -> stringResource(R.string.common_loading)
        }
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodyMedium)
        if (tag != null) {
            Text(
                tag,
                style = MaterialTheme.typography.bodySmall,
                color = LocalDatawatchColors.current.accent2,
                modifier =
                    Modifier.clickable {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/dmz006/datawatch/releases/tag/" + Uri.encode(tag)),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
            )
        } else {
            Text(
                fallback,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Settings / Observer card — thin alias over the shared collapsible [PwaCard] (D26a/D27a);
 * its "?" target comes from the DocsLinks entry for [id] (BL414).
 */
@Composable
internal fun Section(
    id: String,
    title: String,
    collapsible: Boolean = true,
    content: @Composable () -> Unit,
) {
    PwaCard(id = id, title = title, collapsible = collapsible) { content() }
}

@Composable
private fun SectionWithAction(
    id: String,
    title: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    PwaCard(
        id = id,
        title = title,
        headerActions = {
            IconButton(onClick = onAction) {
                Icon(
                    actionIcon,
                    contentDescription = actionDescription,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) { content() }
}

/**
 * Parity D57b — PWA "Restart required to apply changes. Restart now" inline
 * link (app.js backendRestartHint) instead of a persistent banner. Shown only
 * after a config save in this session ([com.dmzs.datawatchclient.events.ConfigSaveBus])
 * on a server with `server.auto_restart_on_config` off.
 */
@Composable
private fun RestartNeededBanner(profile: ServerProfile?) {
    var autoRestart by remember { mutableStateOf<Boolean?>(null) }
    var saved by remember { mutableStateOf(false) }
    var restarting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profile?.id) {
        autoRestart = null
        saved = false
        message = null
        val p = profile ?: return@LaunchedEffect
        ServiceLocator.transportFor(p).fetchConfig().onSuccess { cfg ->
            val srv = cfg.raw["server"] as? kotlinx.serialization.json.JsonObject
            val flag = srv?.get("auto_restart_on_config") as? kotlinx.serialization.json.JsonPrimitive
            autoRestart = flag?.content?.lowercase() == "true"
        }
    }
    LaunchedEffect(Unit) {
        com.dmzs.datawatchclient.events.ConfigSaveBus.events.collect { saved = true }
    }
    if (!restartHintVisible(saved = saved, autoRestart = autoRestart, message = message)) return

    val warn = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.warning
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message ?: stringResource(R.string.restart_required_hint),
            style = MaterialTheme.typography.labelSmall,
            color = if (message == null) warn else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (message == null) {
            Text(
                stringResource(R.string.restart_now_link),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .padding(start = 6.dp)
                        .clickable(enabled = !restarting) {
                            val p = profile ?: return@clickable
                            restarting = true
                            message = "Restarting daemon…"
                            scope.launch {
                                ServiceLocator.transportFor(p).restartDaemon().fold(
                                    onSuccess = { message = "Restart requested. Give the daemon 5–10 s to come back." },
                                    onFailure = { err -> message = "Restart failed — ${err.message ?: err::class.simpleName}" },
                                )
                                restarting = false
                                saved = false
                            }
                        },
            )
        }
    }
}

/** D57b: the inline hint shows after a save on a no-auto-restart server, or while a restart message is up. */
internal fun restartHintVisible(
    saved: Boolean,
    autoRestart: Boolean?,
    message: String?,
): Boolean = message != null || (saved && autoRestart == false)
