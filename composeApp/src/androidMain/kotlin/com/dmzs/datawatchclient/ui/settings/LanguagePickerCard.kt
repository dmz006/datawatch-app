package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

private val LANGUAGE_OPTIONS = listOf(
    "auto" to "Auto (server default)",
    "en" to "English",
    "de" to "Deutsch",
    "es" to "Español",
    "fr" to "Français",
    "it" to "Italiano",
    "ja" to "日本語",
    "ko" to "한국어",
    "pt" to "Português",
    "ru" to "Русский",
    "zh" to "中文",
)

/**
 * Settings → About: whisper.language picker. Surfaced prominently here per
 * PWA v5.28.3 parity (language picker at top of About). Selecting a concrete
 * locale PUTs whisper.language to the server; selecting "Auto" leaves the
 * server config unchanged (parent server manages its own default).
 */
@Composable
internal fun LanguagePickerCard() {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("auto") }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
            ?: return@LaunchedEffect
        ServiceLocator.transportFor(profile).fetchConfig().onSuccess { cfg ->
            current = cfg.raw["whisper.language"]?.jsonPrimitive?.content ?: "auto"
        }
    }

    val displayLabel = LANGUAGE_OPTIONS.firstOrNull { it.first == current }?.second ?: current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PwaSectionTitle("Language / Whisper Language", docsAnchor = "language")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Whisper language",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                displayLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = true }.padding(4.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LANGUAGE_OPTIONS.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            expanded = false
                            current = code
                            if (code != "auto") {
                                scope.launch {
                                    val profiles = ServiceLocator.profileRepository.observeAll().first()
                                    val activeId = ServiceLocator.activeServerStore.get()
                                    val profile = profiles.firstOrNull { it.id == activeId }
                                        ?: profiles.firstOrNull() ?: return@launch
                                    ServiceLocator.transportFor(profile).setWhisperLanguage(code)
                                }
                            }
                        },
                    )
                }
            }
        }
        Text(
            "Sets server whisper.language. \"Auto\" leaves the server default unchanged.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
