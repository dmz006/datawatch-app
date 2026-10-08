package com.dmzs.datawatchclient.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.FedConnPhase
import com.dmzs.datawatchclient.transport.FedConnStatus

/**
 * #236.2 — the connection status of the active proxied remote, or null when
 * there's nothing to show ([activeProfileId] is a real server, "All servers"
 * is selected ([allMode]), or real data already arrived).
 */
@Composable
internal fun rememberFedConnStatus(
    activeProfileId: String?,
    allMode: Boolean = false,
): FedConnStatus? {
    val status by ServiceLocator.fedConnMonitor.status.collectAsState()
    return status?.takeIf { !allMode && activeProfileId != null && it.profileId == activeProfileId }
}

/**
 * PWA `renderSessionsView` federated status (app.js, v8.73.2–v8.73.3):
 * the eye + "Connecting to X…" / "Loading sessions from X…" while the real
 * probe runs, or ⚠ "Could not reach this server" with "X: reason" (the
 * specific auth text on 401/403) and a button back to the server the remote
 * is reached through. No sessions watermark (PWA v8.73.3).
 */
@Composable
internal fun FedConnStatusPane(
    status: FedConnStatus,
    modifier: Modifier = Modifier,
) {
    when (status.phase) {
        FedConnPhase.CONNECTING ->
            DatawatchLoadingContent(
                modifier = modifier.padding(top = 32.dp),
                label = stringResource(R.string.fed_conn_connecting, status.serverName),
            )
        FedConnPhase.LOADING_SESSIONS ->
            DatawatchLoadingContent(
                modifier = modifier.padding(top = 32.dp),
                label = stringResource(R.string.fed_conn_loading_sessions, status.serverName),
            )
        FedConnPhase.ERROR -> FedConnErrorPane(status, modifier)
    }
}

@Composable
private fun FedConnErrorPane(
    status: FedConnStatus,
    modifier: Modifier,
) {
    val reason =
        if (status.authFailed) stringResource(R.string.fed_conn_error_auth) else status.reason.orEmpty()
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⚠️", style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.fed_conn_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.fed_conn_error_body, status.serverName, reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { ServiceLocator.activeServerStore.set(status.parentId) },
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text(stringResource(R.string.fed_conn_back_to_local, status.parentName))
            }
        }
    }
}
