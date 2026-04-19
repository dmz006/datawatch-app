package com.dmzs.datawatchclient.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.dmzs.datawatchclient.Version
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wear OS companion app main screen. Shows quick session counts (running /
 * waiting / total) for the primary paired profile, polled every 30 s.
 *
 * Phase 1 Wear data source: bypasses the phone's SQLDelight/Keystore chain
 * because Wear apps use a simpler on-device shared prefs store; for Sprint
 * 4 the Wear app receives server profile config via the Data Layer API
 * (play-services-wearable). Until that's wired, this screen shows a stub
 * until the user pairs via the phone Settings → Wear card (v0.5.0 Phase 2).
 */
public class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { WearRoot() }
        }
    }
}

@Composable
private fun WearRoot(vm: WearSessionCountsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Scaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "datawatch ${Version.VERSION}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            } else if (state.pairedServer == null) {
                Text(
                    "Pair phone in Settings",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface,
                )
            } else {
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountTile(value = state.running, label = "run")
                    CountTile(value = state.waiting, label = "wait", highlight = true)
                    CountTile(value = state.total, label = "total")
                }
                state.serverName?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountTile(value: Int, label: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.display3,
            color = if (highlight) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
    }
}

public class WearSessionCountsViewModel : ViewModel() {

    public data class UiState(
        val loading: Boolean = true,
        val pairedServer: String? = null,
        val serverName: String? = null,
        val running: Int = 0,
        val waiting: Int = 0,
        val total: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            // Sprint 4 Phase 2 reads this from Wear Data Layer; placeholder
            // pending pairing.
            _state.value = _state.value.copy(loading = false, pairedServer = null)
            while (isActive) {
                delay(30_000L)
            }
        }
    }
}
