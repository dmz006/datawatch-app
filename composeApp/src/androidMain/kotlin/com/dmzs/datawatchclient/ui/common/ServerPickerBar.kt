package com.dmzs.datawatchclient.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ProxiedServersCoordinator
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.transport.ProxiedServers
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors

/**
 * Parity D2a — the PWA per-tab server picker bar (`_serverPickerBar`,
 * app.js BL312 S3): a compact strip under the header reading
 * `Server: [All] [a] [b] …`. The active chip is filled accent2 with white
 * semibold text; the rest sit on `--bg3` with a 1dp `--border` stroke.
 *
 * Hidden when there is nothing to pick (fewer than two choices), mirroring
 * the PWA hiding the bar when no remote servers are registered — except
 * while the remote list is first loading, when it reads "Loading servers…".
 *
 * #234 — remote servers configured on each profile (reached through its
 * `/api/proxy/<name>`) get a chip right after their parent: "parent › remote",
 * or just "remote" when only one real server exists (the PWA's
 * "Local · remote" feel). [profiles] stays the real-profile list; the
 * proxied ones come from [ServiceLocator.proxiedServers].
 *
 * @param showAll adds the leading "All" chip (Sessions / Automata / Alerts).
 */
@Composable
internal fun ServerPickerBar(
    profiles: List<ServerProfile>,
    activeId: String?,
    allMode: Boolean,
    onSelect: (String) -> Unit,
    showAll: Boolean = false,
    onSelectAll: () -> Unit = {},
) {
    val real = profiles.filter { it.enabled && !ProxiedServers.isProxied(it.id) }
    val enabled = rememberProxiedPickerProfiles(real)
    // PWA `server_picker_loading` placeholder (v8.73.2): while a server's
    // remote list is first being fetched the bar says so instead of hiding.
    val loading = rememberProxiedPickerLoading(real)
    if (enabled.size < 2 && !loading) return
    val dw = LocalDatawatchColors.current
    Column(modifier = Modifier.fillMaxWidth().background(dw.bg2)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.server_picker_label),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showAll && enabled.size >= 2) {
                ServerChip(
                    label = stringResource(R.string.server_all_label),
                    active = allMode,
                    onClick = onSelectAll,
                )
            }
            if (enabled.size >= 2) {
                val effectiveActive =
                    if (allMode && showAll) null else (enabled.firstOrNull { it.id == activeId } ?: enabled.first()).id
                enabled.forEach { p ->
                    ServerChip(
                        label = ProxiedServers.chipLabel(p, real.size),
                        active = p.id == effectiveActive,
                        onClick = { onSelect(p.id) },
                    )
                }
            }
            if (loading) {
                Text(
                    stringResource(R.string.server_picker_loading),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dw.border))
    }
}

/**
 * #234 — [real] profiles with each enabled one's proxied remotes inserted
 * right after it (picker order). Kicks a discovery refresh when first shown
 * so a freshly opened picker reflects the parent's current Remote Servers.
 */
@Composable
internal fun rememberProxiedPickerProfiles(real: List<ServerProfile>): List<ServerProfile> {
    val byParent by ServiceLocator.proxiedServers.byParent.collectAsState()
    LaunchedEffect(Unit) { ProxiedServersCoordinator.refreshNow() }
    val plain = real.filterNot { ProxiedServers.isProxied(it.id) }
    return ProxiedServers.groupedForPicker(plain, byParent.values.flatten())
}

/**
 * #236.1 / PWA `server_picker_loading` — true while a remote-list refresh is
 * running and some enabled server in [real] has never been listed yet.
 */
@Composable
internal fun rememberProxiedPickerLoading(real: List<ServerProfile>): Boolean {
    val inFlight by ServiceLocator.proxiedServers.inFlight.collectAsState()
    val byParent by ServiceLocator.proxiedServers.byParent.collectAsState()
    return ServiceLocator.proxiedServers.firstLoadPending(real, inFlight, byParent)
}

/** "via <parent>" subtitle for a proxied remote, or null for a real profile. */
internal fun proxiedParentName(
    profile: ServerProfile,
    all: List<ServerProfile>,
): String? {
    if (!ProxiedServers.isProxied(profile.id)) return null
    val parentId = ProxiedServers.parentIdOf(profile.id)
    return all.firstOrNull { it.id == parentId }?.displayName
        ?: profile.displayName.substringBefore(ProxiedServers.NAME_JOINER)
}

@Composable
private fun ServerChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val dw = LocalDatawatchColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier =
            Modifier
                .background(if (active) dw.accent2 else dw.bg3, shape)
                .border(1.dp, dw.border, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
