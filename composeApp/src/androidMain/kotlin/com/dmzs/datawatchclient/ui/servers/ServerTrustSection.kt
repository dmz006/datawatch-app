package com.dmzs.datawatchclient.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.transport.CertFingerprint
import com.dmzs.datawatchclient.transport.CertPins

/**
 * Parity D91a — Android port of the iOS Add/Edit server "Security" section
 * (`ServerTrustSection` in AddServerView.swift): pin the server's leaf
 * certificate (trust-on-first-use, user confirms the SHA-256), or fall back to
 * the insecure trust-all switch. Pinning keeps hostname verification on.
 */
@Composable
internal fun ServerTrustSection(
    baseUrl: String,
    selfSigned: Boolean,
    pinnedSha: String?,
    pinning: Boolean,
    pinCandidate: CertFingerprint?,
    pinError: String?,
    onProbe: () -> Unit,
    onConfirmPin: () -> Unit,
    onCancelPin: () -> Unit,
    onRemovePin: () -> Unit,
    onSelfSigned: (Boolean) -> Unit,
    disabled: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Security", style = MaterialTheme.typography.titleSmall)
        if (pinnedSha != null) {
            Text(
                "🔒 Pinned certificate",
                style = MaterialTheme.typography.bodyMedium,
                color = com.dmzs.datawatchclient.ui.theme.LocalDatawatchColors.current.success,
            )
            SelectionContainer {
                Text(
                    CertPins.display(pinnedSha),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRemovePin, enabled = !disabled) {
                Text("Remove pin", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onProbe,
                    enabled = !disabled && !pinning && baseUrl.trim().startsWith("https://"),
                ) {
                    Text("🔒 Pin server certificate…")
                }
                if (pinning) {
                    Spacer(Modifier.width(12.dp))
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (pinError != null) {
            Text(pinError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = selfSigned, onCheckedChange = onSelfSigned, enabled = !disabled)
            Text(
                "  Trust all certificates (insecure)",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (selfSigned) {
            Text(
                "Disables certificate validation for this server. Prefer pinning the certificate above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "Pinning trusts exactly this server's certificate — self-signed is fine, but it must " +
                "name this host. If the server's certificate is replaced, connections fail until you re-pin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
    if (pinCandidate != null) {
        AlertDialog(
            onDismissRequest = onCancelPin,
            title = { Text("Pin this certificate?") },
            text = {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(pinCandidate.subject, style = MaterialTheme.typography.bodySmall)
                        if (pinCandidate.issuer != pinCandidate.subject) {
                            Text("Issuer: ${pinCandidate.issuer}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("SHA-256", style = MaterialTheme.typography.labelSmall)
                        Text(
                            pinCandidate.display,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            "Verify this matches the fingerprint shown on the server before trusting it.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onConfirmPin) { Text("Trust & pin") } },
            dismissButton = { TextButton(onClick = onCancelPin) { Text("Cancel") } },
        )
    }
}
