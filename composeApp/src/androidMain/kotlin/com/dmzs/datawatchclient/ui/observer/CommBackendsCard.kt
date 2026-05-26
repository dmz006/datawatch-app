package com.dmzs.datawatchclient.ui.observer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

private val COMM_BACKENDS = listOf(
    "telegram", "discord", "slack", "matrix", "ntfy",
    "email", "twilio", "github_webhook", "webhook", "dns_channel",
)

@Composable
internal fun CommBackendsCard() {
    var enabledBackends by remember { mutableStateOf<List<String>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val id = ServiceLocator.activeServerStore.get()
        val profiles = ServiceLocator.profileRepository.observeAll().first().filter { it.enabled }
        val profile = profiles.firstOrNull { it.id == id } ?: profiles.firstOrNull()
            ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).fetchConfig().fold(
            onSuccess = { cfg ->
                enabledBackends = COMM_BACKENDS.filter { key ->
                    val section = cfg.raw[key] as? JsonObject ?: return@filter false
                    (section["enabled"] as? JsonPrimitive)?.booleanOrNull == true
                }
            },
            onFailure = { banner = it.message },
        )
    }

    if (enabledBackends.isEmpty() && banner == null) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard().padding(12.dp),
    ) {
        PwaSectionTitle("Communication Backends", docsAnchor = "communication-configuration")

        banner?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            return@Column
        }

        if (enabledBackends.isEmpty()) {
            Text(
                "No communication backends enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            enabledBackends.forEach { backend ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981),
                        modifier = Modifier.size(8.dp),
                    ) {}
                    Text(
                        backend.replace('_', ' ').replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
