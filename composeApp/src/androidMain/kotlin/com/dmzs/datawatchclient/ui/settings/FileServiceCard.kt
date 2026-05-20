package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.FileServiceMetaDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first

/**
 * T30 — File Service status card (GET /api/files/meta).
 * Read-only card showing root path + discussion/peer counts.
 */
@Composable
public fun FileServiceCard() {
    var meta by remember { mutableStateOf<FileServiceMetaDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val id = ServiceLocator.activeServerStore.get()
        val p = ServiceLocator.profileRepository.observeAll().first()
            .firstOrNull { it.id == id && it.enabled }
        if (p != null) {
            ServiceLocator.transportFor(p).getFileServiceMeta()
                .onSuccess { meta = it; loadError = null }
                .onFailure { loadError = it.message }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            PwaSectionTitle(stringResource(R.string.file_service_title))

            when {
                loadError != null -> Text(
                    loadError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                meta == null -> Text(
                    stringResource(R.string.common_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> {
                    val m = meta!!
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.file_service_root),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            m.root.ifEmpty { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.file_service_discussions),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "${m.discussions.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.file_service_peers),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "${m.peers.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
