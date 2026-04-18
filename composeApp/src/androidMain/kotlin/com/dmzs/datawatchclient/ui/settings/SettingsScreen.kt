package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.Version
import com.dmzs.datawatchclient.di.ServiceLocator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SettingsScreen() {
    val profiles by ServiceLocator.profileRepository.observeAll()
        .collectAsState(initial = emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            Section("Servers") {
                if (profiles.isEmpty()) {
                    Text(
                        "No servers yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    profiles.forEach { p ->
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Text(p.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                p.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
            Section("About") {
                Text(
                    "datawatch v${Version.VERSION}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    content()
    HorizontalDivider()
}
