package com.dmzs.datawatchclient.ui.settings

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerInfo
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportError
import com.dmzs.datawatchclient.ui.splash.MatrixLogoAnimated
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
 * Sub-tabs — order matches PWA `app.js:3089`:
 * **Monitor, General, Comms, LLM, About**. Keeping this ordering is
 * load-bearing for user muscle memory; the PWA stashes the default
 * active tab in `localStorage.cs_settings_tab = 'monitor'` so users
 * land on Monitor first.
 */
private enum class SettingsTab(val label: String) {
    Monitor("Monitor"),
    General("General"),
    Comms("Comms"),
    Llm("LLM"),
    About("About"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SettingsScreen(
    onAddServer: () -> Unit = {},
    onEditServer: (String) -> Unit = {},
) {
    val profiles by ServiceLocator.profileRepository.observeAll()
        .collectAsState(initial = emptyList())
    val activeId by ServiceLocator.activeServerStore.observe()
        .collectAsState(initial = null)
    val activeProfile: ServerProfile? =
        remember(profiles, activeId) {
            val enabled = profiles.filter { it.enabled }
            if (activeId == ActiveServerStore.SENTINEL_ALL_SERVERS) {
                enabled.firstOrNull()
            } else {
                enabled.firstOrNull { it.id == activeId } ?: enabled.firstOrNull()
            }
        }

    var activeTab by remember { mutableStateOf(SettingsTab.Monitor) }
    var serverPickerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings")
                        activeProfile?.let {
                            Text(
                                it.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    // Server picker — per-server surfaces (Monitor / General /
                    // Comms / LLM) target whichever server this resolves to.
                    // Tapping opens the same bottom sheet the 3-finger-swipe
                    // summons, so user gesture + explicit tap both land on the
                    // same chooser, and picking a server here re-fires every
                    // settings card's refresh via activeProfileFlow().
                    IconButton(onClick = { serverPickerOpen = true }) {
                        Icon(
                            Icons.Filled.Storage,
                            contentDescription = "Switch server",
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
                        onClick = { activeTab = tab },
                        selectedContentColor = dw.accent2,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
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
                            SettingsTab.Monitor -> {
                                // v0.36.0 federated monitoring suite. Cards
                                // self-hide when their backing endpoint
                                // returns nothing useful (older daemons,
                                // single-node setups), so the Monitor tab
                                // stays readable on minimal deployments and
                                // grows on richer ones.
                                com.dmzs.datawatchclient.ui.stats.StatsScreenContent()
                                com.dmzs.datawatchclient.ui.monitoring.EBpfStatusCard()
                                com.dmzs.datawatchclient.ui.monitoring.ClusterNodesCard()
                                com.dmzs.datawatchclient.ui.monitoring.FederatedPeersCard()
                                com.dmzs.datawatchclient.ui.monitoring.PluginsCard()
                                com.dmzs.datawatchclient.ui.memory.MemoryCard()
                                com.dmzs.datawatchclient.ui.memory.MempalaceActionsCard()
                                com.dmzs.datawatchclient.ui.schedules.SchedulesCard()
                                com.dmzs.datawatchclient.ui.ops.DaemonLogCard()
                            }
                            SettingsTab.General -> {
                                // v0.42.6 — General tab now mirrors PWA's
                                // GENERAL_CONFIG_FIELDS array order verbatim
                                // (Datawatch → Auto-Update → Session → Pipelines
                                // → Autonomous → Orchestrator → Plugins →
                                // Whisper), then the explicit cards: Project
                                // Profiles → Cluster Profiles → Container
                                // Workers → Notifications. RTK lives in the
                                // LLM tab now (PWA's LLM_CONFIG_FIELDS) — the
                                // duplicate gc_rtk panel was a mobile-only
                                // artifact and is gone.
                                SecurityCard()
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Datawatch,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.AutoUpdate,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Session,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Pipelines,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Autonomous,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Orchestrator,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Plugins,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Whisper,
                                )
                                com.dmzs.datawatchclient.ui.voice.TestWhisperCard()
                                com.dmzs.datawatchclient.ui.profiles.KindProfilesCard(
                                    kind = "project",
                                    title = "Project profiles",
                                )
                                com.dmzs.datawatchclient.ui.profiles.KindProfilesCard(
                                    kind = "cluster",
                                    title = "Cluster profiles",
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Agents,
                                )
                                com.dmzs.datawatchclient.ui.notifications.NotificationsCard()
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
                                com.dmzs.datawatchclient.ui.channels.ChannelsCard()
                                com.dmzs.datawatchclient.ui.federation.FederationPeersCard()
                                com.dmzs.datawatchclient.ui.cert.CertInstallCard()
                            }
                            SettingsTab.Llm -> {
                                // Matches PWA `data-group="llm"`: LLM
                                // Configuration → lc_* (Memory, LlmRtk) →
                                // Detection Filters → Saved Commands →
                                // Output Filters.
                                //
                                // v0.33.13 (B22): LlmConfigCard at the top
                                // shows each registered backend + "(default)"
                                // badge, matching PWA's first card.
                                com.dmzs.datawatchclient.ui.channels.LlmConfigCard()
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.Memory,
                                )
                                com.dmzs.datawatchclient.ui.configfields.ConfigFieldsPanel(
                                    com.dmzs.datawatchclient.ui.configfields.ConfigFieldSchemas.LlmRtk,
                                )
                                com.dmzs.datawatchclient.ui.detection.DetectionFiltersCard()
                                com.dmzs.datawatchclient.ui.commands.SavedCommandsCard()
                                com.dmzs.datawatchclient.ui.filters.FiltersCard()
                            }
                            SettingsTab.About -> {
                                // Daemon admin cluster — About carries the
                                // actions that target daemon meta / process
                                // lifecycle. v0.35.7 strips the raw
                                // `ConfigViewerCard` to align with PWA About
                                // (which has none); Settings → General config
                                // panels already surface every actionable key.
                                AboutCard(activeProfile = activeProfile)
                                com.dmzs.datawatchclient.ui.about.ApiLinksCard()
                                com.dmzs.datawatchclient.ui.ops.UpdateDaemonCard()
                                com.dmzs.datawatchclient.ui.ops.SubsystemReloadCard()
                                com.dmzs.datawatchclient.ui.ops.RestartDaemonCard()
                                com.dmzs.datawatchclient.ui.ops.KillOrphansCard()
                            }
                        }
                    }
                } // end LocalTextStyle provider
            } // end settings-scale MaterialTheme
        }
    }

    if (serverPickerOpen) {
        com.dmzs.datawatchclient.ui.servers.ServerPickerSheet(
            onDismiss = { serverPickerOpen = false },
            onAdd = {
                serverPickerOpen = false
                onAddServer()
            },
        )
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
                Toast.makeText(
                    context,
                    "Saved to Downloads as $filename. Opening system trust-anchor screen…",
                    Toast.LENGTH_LONG,
                ).show()
                // Hand off to the OS flow. User picks the PEM from Downloads.
                val intent =
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        Toast.makeText(
                            context,
                            "Couldn't open security settings — install manually from Downloads.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
            } else {
                Toast.makeText(
                    context,
                    "Downloaded cert but couldn't save to Downloads.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        },
        onFailure = { err ->
            val msg =
                when (err) {
                    is TransportError.NotFound ->
                        "Server doesn't expose /api/cert (parent-repo support pending)."
                    else -> "Cert download failed — ${err.message ?: err::class.simpleName}"
                }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
    var enabled by remember { mutableStateOf(gate.enabled()) }
    val canAuth = remember { gate.canAuthenticate(context) }

    Section(title = "Security") {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text("Biometric unlock", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (canAuth) {
                        "Require fingerprint or face on every app open."
                    } else {
                        "Unavailable — no Class-3 biometric enrolled on this device."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = {
                    gate.setEnabled(it)
                    enabled = it
                },
                enabled = canAuth,
            )
        }
    }
}

@Composable
private fun CommsCard() {
    Section(title = "Comms") {
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

    Section(title = "About") {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Mobile app", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "github.com/dmz006/datawatch-app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "Play Store link will land here once the app is published.",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val label =
        when {
            activeProfile == null -> "No active server"
            serverInfo != null -> "${serverInfo.hostname} · datawatch v${serverInfo.version}"
            error != null -> "— (${activeProfile.displayName} unreachable)"
            else -> "Loading…"
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Connected to", style = MaterialTheme.typography.bodyMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PwaSectionTitle(title)
            content()
        }
    }
}

@Composable
private fun SectionWithAction(
    title: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionDescription: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PwaSectionTitle(
                    title,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAction) {
                    Icon(
                        actionIcon,
                        contentDescription = actionDescription,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            content()
        }
    }
}

/**
 * Inline banner shown above the Settings tab content. Probes the
 * active server's `/api/config` for `server.auto_restart_on_config`:
 *  - **true** → server restarts itself on any PUT /api/config; banner
 *    shows a neutral "Changes auto-apply" note so saves feel trusted.
 *  - **false** → banner turns amber with a prominent "Restart now"
 *    button that hits POST /api/restart. Otherwise users type into
 *    fields, see "Saving…", but their change never activates until
 *    someone manually restarts the daemon (user-reported 2026-04-24).
 *
 * Re-fetches whenever the active profile changes.
 */
@Composable
private fun RestartNeededBanner(profile: ServerProfile?) {
    var autoRestart by remember { mutableStateOf<Boolean?>(null) }
    var restarting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profile?.id) {
        autoRestart = null
        message = null
        val p = profile ?: return@LaunchedEffect
        ServiceLocator.transportFor(p).fetchConfig().onSuccess { cfg ->
            val srv =
                (cfg.raw["server"] as? kotlinx.serialization.json.JsonObject)
            val flag =
                srv?.get("auto_restart_on_config") as? kotlinx.serialization.json.JsonPrimitive
            // JsonPrimitive.content returns "true"/"false" for booleans;
            // parse defensively since the server may emit either case.
            autoRestart = flag?.content?.lowercase() == "true"
        }
    }

    val showAmber = autoRestart == false
    // v0.42.6 — only show the banner when there's a problem to flag
    // (auto-restart OFF) or a transient status to show ("Restarting
    // daemon…"). User direction 2026-04-28: the green
    // "auto-restarts on save" affirmation was visual noise on every
    // healthy server.
    if (!showAmber && message == null) return

    val bg =
        if (showAmber) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val fg =
        if (showAmber) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    androidx.compose.material3.Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message
                    ?: "⚠ Daemon auto-restart is OFF. Some settings won't take effect until you restart.",
                style = MaterialTheme.typography.bodySmall,
                color = fg,
                modifier = Modifier.weight(1f),
            )
            if (showAmber) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val p = profile ?: return@OutlinedButton
                        restarting = true
                        message = "Restarting daemon…"
                        scope.launch {
                            ServiceLocator.transportFor(p).restartDaemon().fold(
                                onSuccess = {
                                    message = "Restart requested. Give the daemon 5–10 s to come back."
                                    restarting = false
                                },
                                onFailure = { err ->
                                    message = "Restart failed — ${err.message ?: err::class.simpleName}"
                                    restarting = false
                                },
                            )
                        }
                    },
                    enabled = !restarting,
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 4.dp,
                        ),
                ) {
                    Text(
                        if (restarting) "…" else "Restart now",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
