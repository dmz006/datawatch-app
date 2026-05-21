package com.dmzs.datawatchclient.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.prefs.TerminalPrefs
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.pwaCard

@Composable
internal fun TerminalDimensionsCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var cols by remember { mutableIntStateOf(prefs.getInt(TerminalPrefs.KEY_COLS, TerminalPrefs.DEFAULT_COLS)) }
    var rows by remember { mutableIntStateOf(prefs.getInt(TerminalPrefs.KEY_ROWS, TerminalPrefs.DEFAULT_ROWS)) }

    fun saveCols(v: Int) { cols = v; prefs.edit().putInt(TerminalPrefs.KEY_COLS, maxOf(0, v)).apply() }
    fun saveRows(v: Int) { rows = v; prefs.edit().putInt(TerminalPrefs.KEY_ROWS, maxOf(0, v)).apply() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle(stringResource(R.string.terminal_dims_title), docsAnchor = "session")
        Text(
            stringResource(R.string.terminal_dims_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        TermDimRow(
            label = stringResource(R.string.terminal_dims_cols),
            value = cols,
            min = 0,
            max = 250,
            step = 10,
            onDecrement = { saveCols(maxOf(0, cols - 10)) },
            onIncrement = { saveCols(minOf(250, cols + 10)) },
        )
        TermDimRow(
            label = stringResource(R.string.terminal_dims_rows),
            value = rows,
            min = 0,
            max = 80,
            step = 5,
            onDecrement = { saveRows(maxOf(0, rows - 5)) },
            onIncrement = { saveRows(minOf(80, rows + 5)) },
        )
    }
}

@Composable
private fun TermDimRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement, enabled = value > min) {
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        Text(
            if (value == 0) stringResource(R.string.terminal_dims_auto) else value.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onIncrement, enabled = value < max) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}
