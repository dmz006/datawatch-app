package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.EncryptionStatusDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first

/**
 * T30 — Encryption Status card (GET /api/security/encryption/status).
 * Read-only card showing secure_mode + per-file encrypted/exists status.
 */
@Composable
public fun EncryptionStatusCard() {
    var status by remember { mutableStateOf<EncryptionStatusDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val id = ServiceLocator.activeServerStore.get()
        val enabled = ServiceLocator.profileRepository.observeAll().first()
            .filter { it.enabled }
        val p = enabled.firstOrNull { it.id == id } ?: enabled.firstOrNull()
        if (p != null) {
            ServiceLocator.transportFor(p).getEncryptionStatus()
                .onSuccess { status = it; loadError = null }
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
            PwaSectionTitle(stringResource(R.string.encryption_status_title))

            when {
                loadError != null -> Text(
                    loadError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                status == null -> Text(
                    stringResource(R.string.common_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> {
                    val s = status!!
                    // Secure mode row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (s.secureMode) Color(0xFF10B981) else Color(0xFFFF5722),
                            modifier = Modifier.size(8.dp),
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(
                                if (s.secureMode) R.string.encryption_secure_on
                                else R.string.encryption_secure_off,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (s.files.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(
                            stringResource(R.string.encryption_files_title),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        s.files.forEachIndexed { idx, file ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        file.path.ifEmpty { "—" },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (file.encrypted) stringResource(R.string.encryption_check) else stringResource(R.string.encryption_cross),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (file.encrypted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.encryption_encrypted_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (file.exists) stringResource(R.string.encryption_check) else stringResource(R.string.encryption_cross),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (file.exists) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.encryption_exists_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (idx < s.files.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
