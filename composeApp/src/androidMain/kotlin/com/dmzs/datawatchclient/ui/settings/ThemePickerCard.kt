package com.dmzs.datawatchclient.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.R
import com.dmzs.datawatchclient.ui.theme.PwaSectionTitle
import com.dmzs.datawatchclient.ui.theme.ThemeMode
import com.dmzs.datawatchclient.ui.theme.ThemePrefs
import com.dmzs.datawatchclient.ui.theme.pwaCard

@Composable
public fun ThemePickerCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(ThemePrefs.load(context)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pwaCard()
            .padding(12.dp),
    ) {
        PwaSectionTitle(stringResource(R.string.settings_theme_title))
        ThemeMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        selected = mode
                        ThemePrefs.save(context, mode)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == mode,
                    onClick = {
                        selected = mode
                        ThemePrefs.save(context, mode)
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when (mode) {
                        ThemeMode.Dark -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.Light -> stringResource(R.string.settings_theme_light)
                        ThemeMode.System -> stringResource(R.string.settings_theme_system)
                    },
                )
            }
        }
    }
}
