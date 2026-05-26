package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.TransportClient
import com.dmzs.datawatchclient.transport.dto.WebPushRegistrationDto
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun PushNotificationsCard() {
    val scope = rememberCoroutineScope()
    var registrations by remember { mutableStateOf<List<WebPushRegistrationDto>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var endpointInput by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var testBusy by remember { mutableStateOf(false) }

    suspend fun transport(): TransportClient? {
        val id = ServiceLocator.activeServerStore.get()
        val profiles = ServiceLocator.profileRepository.observeAll().first().filter { it.enabled }
        val p = profiles.firstOrNull { it.id == id } ?: profiles.firstOrNull()
        return p?.let { ServiceLocator.transportFor(it) }
    }

    suspend fun reload() {
        transport()?.listWebPushRegistrations()
            ?.onSuccess { registrations = it.registrations; banner = null }
            ?.onFailure { banner = it.message }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PwaSectionTitle("Push Notifications", docsAnchor = "push-notifications", modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { reload() } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        // Status badge
        val active = registrations.isNotEmpty()
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (active) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(
                if (active) "Push notifications active (${registrations.size})" else "Push notifications not configured",
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        banner?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }

        // Register new endpoint
        OutlinedTextField(
            value = endpointInput,
            onValueChange = { endpointInput = it },
            label = { Text("Endpoint URL") },
            placeholder = { Text("https://up.example.com/UP?token=…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    scope.launch {
                        registering = true
                        transport()?.addWebPushRegistration(endpointInput.trim())
                            ?.onSuccess { endpointInput = ""; reload() }
                            ?.onFailure { banner = it.message }
                        registering = false
                    }
                },
                enabled = !registering && endpointInput.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("Register") }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        testBusy = true
                        transport()?.sendTestWebPushNotification()
                            ?.onSuccess { banner = "Test notification sent" }
                            ?.onFailure { banner = "Error: ${it.message}" }
                        testBusy = false
                    }
                },
                enabled = !testBusy && active,
                modifier = Modifier.weight(1f),
            ) { Text("Send Test") }
        }

        // Registrations list
        if (registrations.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Text(
                "Registered endpoints",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            registrations.forEach { reg ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        reg.endpoint,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        scope.launch {
                            transport()?.removeWebPushRegistration(reg.id)
                                ?.onSuccess { reload() }
                                ?.onFailure { banner = it.message }
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Unregister", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
