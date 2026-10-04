package com.dmzs.datawatchclient.ui.theme

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmzs.datawatchclient.R

/**
 * Parity D26a + D27a — the shared Observer / Settings card shell.
 *
 * Mirrors the PWA's `<div class="settings-section">` +
 * `settingsSectionHeader(key, title)` + `secContent(key)` trio
 * (app.js `toggleSettingsSection`):
 *  - a disclosure chevron (▼ expanded / ▶ collapsed, rotated over 150 ms like
 *    the PWA's `transition: transform 0.15s`) and the uppercase title form a
 *    single tap target that toggles the card body;
 *  - the collapsed state is persisted per [id] ([PwaCardCollapseStore]); ids are
 *    the PWA section keys where a PWA card exists (`schedules`, `gc_dw`, …);
 *  - a docs "?" link opens the server's manual at [docsAnchor] (the PWA's
 *    `defsLink(title)` slug — see [pwaDocsSlug]) in the in-app docs viewer.
 *
 * PWA default is every card expanded (`cs_settings_collapsed` starts `{}`).
 *
 * [modifier] is the outer modifier (margins) applied before the card surface;
 * [innerPadding] is applied inside the surface around header and body so cards
 * migrated from `.pwaCard().padding(x)` keep their density. [headerActions] are
 * trailing header controls (refresh / add); like the PWA, which keeps actions in
 * the section body, they are hidden while the card is collapsed.
 */
@Composable
public fun PwaCard(
    id: String,
    title: String,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    docsAnchor: String? = null,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    collapsible: Boolean = true,
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val collapsed = collapsible && PwaCardCollapseStore.isCollapsed(context, id)
    Column(modifier = modifier.pwaCard().padding(innerPadding)) {
        if (collapsible) {
            PwaCardHeader(
                title = title,
                collapsed = collapsed,
                onToggle = { PwaCardCollapseStore.toggle(context, id) },
                docsAnchor = docsAnchor,
                headerActions = headerActions,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PwaSectionTitle(title, modifier = Modifier.weight(1f), docsAnchor = docsAnchor)
                headerActions?.invoke(this)
            }
        }
        if (!collapsed) content()
    }
}

/** Header row for [PwaCard]: chevron + title (tap target), docs link, actions. */
@Composable
internal fun PwaCardHeader(
    title: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    docsAnchor: String?,
    headerActions: (@Composable RowScope.() -> Unit)?,
) {
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "pwa-card-chevron",
    )
    val toggleLabel = stringResource(if (collapsed) R.string.card_expand else R.string.card_collapse)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = toggleLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp).rotate(rotation),
            )
            Text(
                title.uppercase(),
                modifier = Modifier.padding(start = 4.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
            )
        }
        if (docsAnchor != null) DocsInlineButton(anchor = docsAnchor)
        if (!collapsed && headerActions != null) headerActions()
    }
}

/**
 * PWA `defsLink(title)` slug: lowercase, runs of non-alphanumerics → '-',
 * leading/trailing dashes trimmed. Feed it the PWA's English card title so the
 * anchor matches the web UI regardless of the app's locale.
 */
public fun pwaDocsSlug(title: String): String =
    title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

/**
 * Persisted collapsed-card ids (PWA `cs_settings_collapsed` analogue). Backed by
 * the app's "settings" SharedPreferences; exposed as Compose state so every
 * [PwaCard] recomposes on toggle.
 */
public object PwaCardCollapseStore {
    internal const val PREFS = "settings"
    internal const val KEY = "pwa_cards_collapsed"

    // Created lazily on first read (prefs need a Context) and never written
    // during composition — only [toggle] mutates it.
    private var holder: MutableState<Set<String>>? = null

    private fun state(context: Context): MutableState<Set<String>> =
        holder ?: mutableStateOf(
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet())
                .orEmpty()
                .toSet(),
        ).also { holder = it }

    public fun isCollapsed(
        context: Context,
        id: String,
    ): Boolean = id in state(context).value

    public fun toggle(
        context: Context,
        id: String,
    ) {
        val s = state(context)
        val next = if (id in s.value) s.value - id else s.value + id
        s.value = next
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, next)
            .apply()
    }

    /** Test hook: drop the in-memory cache so the next read reloads prefs. */
    internal fun resetForTest() {
        holder = null
    }
}
