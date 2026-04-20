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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerInfo
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportError
import com.dmzs.datawatchclient.ui.splash.MatrixLogoAnimated
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

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
        ) {
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
            SecurityCard()
            com.dmzs.datawatchclient.ui.schedules.SchedulesCard()
            com.dmzs.datawatchclient.ui.commands.SavedCommandsCard()
            com.dmzs.datawatchclient.ui.config.ConfigViewerCard()
            CommsCard()
            AboutCard(activeProfile = activeProfile)
        }
    }
}

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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Package", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "com.dmzs.datawatchclient",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("License", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Polyform Noncommercial 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Source", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "github.com/dmz006/datawatch-app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Parent project", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "github.com/dmz006/datawatch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
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
private fun Section(
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
