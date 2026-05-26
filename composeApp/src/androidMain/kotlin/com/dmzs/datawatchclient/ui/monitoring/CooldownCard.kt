package com.dmzs.datawatchclient.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.CooldownStatusDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Observer tab — Global Cooldown card. Mirrors PWA cooldown section:
 * shows current status, preset set buttons (15m–24h), reason field, and clear.
 */
@Composable
public fun CooldownCard() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<CooldownStatusDto?>(null) }
    var banner by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }

    suspend fun transport(): TransportClient? {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        return if (activeId == ActiveServerStore.SENTINEL_ALL_SERVERS) {
            profiles.firstOrNull { it.enabled }
        } else {
            profiles.firstOrNull { it.id == activeId && it.enabled }
                ?: profiles.firstOrNull { it.enabled }
        }?.let { ServiceLocator.transportFor(it) }
    }

    suspend fun reload() {
        transport()?.getCooldownStatus()
            ?.onSuccess { status = it; banner = null }
            ?.onFailure { banner = it.message }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle("Global Cooldown", docsAnchor = "global-cooldown")

        banner?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        val s = status
        if (s == null) {
            Text(
                "Loading…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        if (s.active) {
            val remainingMs = (s.untilUnixMs ?: 0L) - System.currentTimeMillis()
            val remainingMin = if (remainingMs > 0) Math.ceil(remainingMs / 60000.0).toInt() else 0
            val label = buildString {
                append("⚠ Active")
                if (remainingMin > 0) append(" — ${remainingMin}m remaining")
                s.reason?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
            }
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF59E0B),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Text(
                "✓ No active cooldown",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF10B981),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Reason (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf(15 to "15m", 30 to "30m", 60 to "1h", 240 to "4h", 480 to "8h", 1440 to "24h")
                .forEach { (mins, label) ->
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val t = transport() ?: return@launch
                                t.setCooldown(System.currentTimeMillis() + mins * 60_000L, reason)
                                    .onSuccess { reload(); reason = "" }
                                    .onFailure { banner = it.message }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(2.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
        }

        if (s.active) {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    scope.launch {
                        transport()?.clearCooldown()
                            ?.onSuccess { reload() }
                            ?.onFailure { banner = it.message }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear Cooldown", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
