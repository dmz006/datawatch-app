package com.dmzs.datawatchclient.ui.general

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard

/**
 * v0.82.0 Sprint 13 — Observer quicklink card.
 * Provides a direct navigation shortcut to the Monitor tab (Observer surface).
 */
@Composable
public fun ObserverQuicklinkCard(onNavigateToMonitor: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            PwaSectionTitle(stringResource(R.string.observer_quicklink_title))
            OutlinedButton(
                onClick = onNavigateToMonitor,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.observer_quicklink_btn))
            }
        }
    }
}
