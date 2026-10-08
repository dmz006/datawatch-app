package com.dmzs.datawatchclient.ui.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.ui.common.proxiedParentName
import com.dmzs.datawatchclient.ui.common.rememberProxiedPickerProfiles

/**
 * Bottom-sheet server picker. Triggered from the three-finger upward swipe gesture
 * (BL9) installed at the AppRoot level. Tapping a row switches the active profile.
 * Includes "Add server…" affordance for the empty-state edge case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ServerPickerSheet(
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val profiles by ServiceLocator.profileRepository.observeAll()
        .collectAsState(initial = emptyList())
    val activeId by ServiceLocator.activeServerStore.observe().collectAsState(initial = null)
    // #234 — each profile followed by its remotes reached through /api/proxy.
    val rows = rememberProxiedPickerProfiles(profiles)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.settings_switch_server),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            if (profiles.isEmpty()) {
                Text(
                    stringResource(R.string.sessions_no_servers),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rows.forEach { p ->
                ProfileRow(
                    profile = p,
                    viaParent = proxiedParentName(p, profiles),
                    isActive = p.id == activeId,
                    onSelect = {
                        ServiceLocator.activeServerStore.set(p.id)
                        onDismiss()
                    },
                )
                HorizontalDivider()
            }
            Row(
                modifier =
                    Modifier.fillMaxWidth().clickable {
                        onAdd()
                        onDismiss()
                    }.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    onAdd()
                    onDismiss()
                }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sessions_add_server)) }
                Text(stringResource(R.string.sessions_add_server), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ServerProfile,
    viaParent: String?,
    isActive: Boolean,
    onSelect: () -> Unit,
) {
    // A proxied remote sits indented under its parent: "workstation › demo",
    // subtitle "via workstation".
    val indent = if (viaParent != null) 24.dp else 0.dp
    Row(
        modifier =
            Modifier.fillMaxWidth().clickable(onClick = onSelect)
                .padding(start = 24.dp + indent, end = 24.dp, top = 24.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(enabled = profile.enabled)
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(profile.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                viaParent?.let { stringResource(R.string.server_via_parent, it) } ?: profile.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isActive) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Active",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatusDot(enabled: Boolean) {
    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(modifier = Modifier.size(10.dp).clip(CircleShape)) {
        Surface(color = color, modifier = Modifier.size(10.dp), shape = CircleShape) {}
    }
}
