package com.dmzs.datawatchclient.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.dmzs.datawatchclient.Version

/**
 * Pre-MVP Wear placeholder. Sprint 4 implements W1 / W3 / W4 (notification listener,
 * complication data source, rich app) per docs/wear-os.md.
 */
public class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Datawatch ${Version.VERSION}")
                }
            }
        }
    }
}
