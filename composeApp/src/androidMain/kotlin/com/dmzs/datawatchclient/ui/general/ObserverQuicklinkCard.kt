package com.dmzs.datawatchclient.ui.general

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.ui.theme.PwaCard

/**
 * v0.82.0 Sprint 13 — Observer quicklink card.
 * Provides a direct navigation shortcut to the Monitor tab (Observer surface).
 */
@Composable
public fun ObserverQuicklinkCard(onNavigateToMonitor: () -> Unit) {
    PwaCard(
        id = "observer_quicklink",
        title = stringResource(R.string.observer_quicklink_title),
        innerPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        OutlinedButton(
            onClick = onNavigateToMonitor,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.observer_quicklink_btn))
        }
    }
}
