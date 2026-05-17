package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.SecretsStatusDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first

/**
 * v0.75.0 S6-3 (#82) — Vault/Secrets status card.
 *
 * Shown in Settings → General after SecurityCard. Only renders the vault
 * detail section when activeBackend == "vault"; local-secrets setups see
 * a compact "Local secrets (no vault)" note. Mirrors the PWA secrets
 * status surface from datawatch v7+.
 *
 * GET /api/secrets/status
 */
@Composable
public fun SecretsStatusCard() {
    var status by remember { mutableStateOf<SecretsStatusDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            ServiceLocator.profileRepository.observeAll().first()
                .firstOrNull { it.id == activeId && it.enabled } ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).getSecretsStatus().fold(
            onSuccess = { s -> status = s; error = null },
            onFailure = { err -> error = err.message ?: err::class.simpleName },
        )
    }

    val s = status ?: return // hide while loading or on error

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (s.activeBackend == "vault") {
                // Vault detail section
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PwaSectionTitle(
                        stringResource(R.string.vault_status_title),
                        Modifier.weight(1f),
                        docsAnchor = "secrets-store",
                    )
                    // Reachability dot — green if reachable, red if not.
                    Canvas(Modifier.size(10.dp)) {
                        drawCircle(
                            color = if (s.reachable) Color(0xFF00E676) else Color(0xFFEF4444),
                        )
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    s.address?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall)
                    }
                    s.mount?.let {
                        Text("mount: $it", style = MaterialTheme.typography.labelSmall)
                    }
                    s.lastSuccess?.let {
                        Text(
                            stringResource(R.string.vault_last_success, it),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (!s.reachable) {
                        Text(
                            stringResource(R.string.vault_unreachable),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    s.lastError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                // Local secrets — compact note.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PwaSectionTitle(
                        stringResource(R.string.vault_status_title),
                        Modifier.weight(1f),
                        docsAnchor = "secrets-store",
                    )
                    Text(
                        stringResource(R.string.vault_backend_local),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
