package com.dmzs.datawatchclient.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R

/**
 * PWA per-block placeholder: muted "Loading…" (`common_loading`) inside a card
 * while its first fetch is in flight. Used by the Observer cards instead of a
 * full-screen spinner.
 */
@Composable
public fun PwaLoadingText(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.common_loading),
        modifier = modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
