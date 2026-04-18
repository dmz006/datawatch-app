package com.dmzs.datawatchclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dmzs.datawatchclient.ui.theme.DatawatchTheme

/**
 * Placeholder launch Activity — Sprint 1 replaces this with the full navigation host.
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DatawatchTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PreMvpPlaceholder()
                }
            }
        }
    }
}

@Composable
private fun PreMvpPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Datawatch Client ${Version.VERSION}\n" +
                "Pre-MVP scaffold — see docs/ for the design package.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
