package com.dmzs.datawatchclient.ui.cert

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.prefs.ActiveServerStore
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard
import kotlinx.coroutines.flow.first

/**
 * Settings → Comms → CA certificate card mirroring PWA's
 * `_tls_install` HTML block. Two action buttons (download + open
 * security settings) plus expandable Android / iPhone install
 * steps verbatim from the PWA instructions.
 */
@Composable
public fun CertInstallCard() {
    val context = LocalContext.current
    var baseUrl by remember { mutableStateOf<String?>(null) }
    var showAndroidSteps by remember { mutableStateOf(false) }
    var showIPhoneSteps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val profiles = ServiceLocator.profileRepository.observeAll().first()
        val activeId = ServiceLocator.activeServerStore.get()
        val profile =
            profiles.firstOrNull {
                it.id == activeId && it.enabled && activeId != ActiveServerStore.SENTINEL_ALL_SERVERS
            } ?: profiles.firstOrNull { it.enabled }
        baseUrl = profile?.baseUrl
    }
    val base = baseUrl
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).pwaCard(),
    ) {
        PwaSectionTitle("CA certificate")
        if (base == null) {
            Text(
                "No enabled server.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Text(
            "If the server uses self-signed TLS, install the CA here " +
                "so Android trusts it in Chrome + WebView + the app.",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("$base/api/cert?format=der")),
                    )
                },
            ) { Text("Download (.crt)", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            ) { Text("Security settings", style = MaterialTheme.typography.labelSmall) }
        }
        Text(
            (if (showAndroidSteps) "▾" else "▸") + " Android install steps",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { showAndroidSteps = !showAndroidSteps }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (showAndroidSteps) {
            Text(
                """
                1. Tap "Download (.crt)" above — Chrome saves
                   datawatch-ca.crt to the Downloads folder.
                2. Open system Settings → Security & privacy → More
                   security & privacy → Encryption & credentials →
                   Install a certificate → CA certificate.
                3. Select the downloaded datawatch-ca.crt.
                4. Confirm install (may require a device PIN).
                5. Remove any old home-screen shortcut; revisit the
                   HTTPS site, tap ⋮ → Install app to recreate.
                """.trimIndent(),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (showIPhoneSteps) "▾" else "▸") + " iPhone / iPad install steps",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { showIPhoneSteps = !showIPhoneSteps }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (showIPhoneSteps) {
            Text(
                """
                1. Tap "Download (.crt)" above to download the PEM.
                2. Settings → General → VPN & Device Management →
                   tap the downloaded profile → Install.
                3. Settings → General → About → Certificate Trust
                   Settings → enable full trust for the datawatch
                   certificate.
                """.trimIndent(),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
