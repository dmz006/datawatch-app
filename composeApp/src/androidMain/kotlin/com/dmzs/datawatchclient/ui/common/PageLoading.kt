package com.dmzs.datawatchclient.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R

/**
 * Full-page first-load state: the animated datawatch eye from the splash plus
 * a label such as "Loading sessions…". Use it when a page has nothing to show
 * yet, instead of an empty list or a skeleton that looks like missing data.
 */
@Composable
public fun PageLoading(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DatawatchLoadingContent(label = label, eyeSize = 96.dp, verticalPadding = 48.dp)
    }
}

/**
 * Thin progress bar plus "Showing saved data · refreshing…" under a page
 * header, shown while the page displays cached data and a refresh is running.
 *
 * Background polls refresh every few seconds, so the strip only appears when
 * the data on screen is older than [staleAfterMs] (for example after
 * returning to the app or switching servers). Fast routine polls stay quiet.
 * Pass [lastLoadedMs] = 0 when nothing has loaded yet in this session.
 */
@Composable
public fun CachedDataRefreshStrip(
    refreshing: Boolean,
    lastLoadedMs: Long,
    modifier: Modifier = Modifier,
    staleAfterMs: Long = 15_000L,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(refreshing) { now = System.currentTimeMillis() }
    val stale = lastLoadedMs == 0L || now - lastLoadedMs > staleAfterMs
    AnimatedVisibility(
        visible = refreshing && stale,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            Text(
                text = stringResource(R.string.refreshing_cached_data),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}
