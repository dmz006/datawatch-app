package com.dmzs.datawatchclient.ui.observer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.dto.MatrixStatusDto
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first

@Composable
public fun MatrixStatusCard() {
    var status by remember { mutableStateOf<MatrixStatusDto?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled } ?: run {
                banner = "No enabled server."
                return@LaunchedEffect
            }
        ServiceLocator.transportFor(profile).fetchMatrixStatus().fold(
            onSuccess = { status = it },
            onFailure = { banner = "Matrix status unavailable — ${it.message ?: it::class.simpleName}" },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("Matrix", docsAnchor = "communication-configuration")
        banner?.let {
            Text(
                it,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        status?.let { s ->
            val dw = LocalDatawatchColors.current
            MatrixInfoRow(
                label = "Status",
                value = if (s.connected) "Connected" else "Disconnected",
                valueColor = if (s.connected) dw.success else MaterialTheme.colorScheme.error,
            )
            if (s.homeserver.isNotBlank()) {
                MatrixInfoRow("Homeserver", s.homeserver, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.roomId.isNotBlank()) {
                MatrixInfoRow("Room ID", s.roomId, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.error?.takeIf { it.isNotBlank() }?.let { err ->
                MatrixInfoRow("Error", err, MaterialTheme.colorScheme.error)
            }
        }
        if (status == null && banner == null) {
            Text(
                "Loading…",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MatrixInfoRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            modifier = Modifier.weight(1f),
        )
    }
}
