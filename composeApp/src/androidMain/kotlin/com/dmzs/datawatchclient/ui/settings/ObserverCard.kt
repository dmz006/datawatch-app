package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard

/**
 * v0.75.0 S6-7 (#88) — Observer card stub.
 *
 * Placed in Settings → Monitor tab. Full observer session management
 * is planned for a future sprint; this stub ensures the card is present
 * and locale strings are wired.
 */
@Composable
public fun ObserverCard() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PwaSectionTitle(stringResource(R.string.observer_title))
            Text(
                stringResource(R.string.observer_no_active),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
