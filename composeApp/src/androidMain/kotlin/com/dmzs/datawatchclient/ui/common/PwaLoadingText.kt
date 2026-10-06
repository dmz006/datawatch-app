package com.dmzs.datawatchclient.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R

/**
 * Per-card loading placeholder: the animated datawatch eye with a pulsing
 * "Loading…" label ([DatawatchLoadingContent], the same eye as the splash),
 * compact enough to sit inside a card.
 *
 * Operator 2026-10-06: card loading must show the animated icon (the eye the
 * Observer cards had before the 2026-10-05 parity pass swapped it for the web
 * UI's plain "Loading…" text), not a bare text line or shimmer bars. Every
 * card loading state on Android uses this; iOS `CardSkeleton` matches.
 */
@Composable
public fun PwaLoadingText(
    modifier: Modifier = Modifier,
    /** Kept for call-site compatibility; the eye is centred in the card. */
    @Suppress("UNUSED_PARAMETER") horizontalPadding: Dp = 12.dp,
) {
    DatawatchLoadingContent(
        modifier = modifier.padding(horizontal = 12.dp),
        label = stringResource(R.string.common_loading),
        eyeSize = 32.dp,
        verticalPadding = 12.dp,
    )
}
