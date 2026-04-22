package com.dmzs.datawatchclient.wear

import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.dmzs.datawatchclient.Version
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Wear OS companion main screen. Subscribes to the `/datawatch/counts`
 * DataItem published by the phone's [WearSyncService] and renders
 * running / waiting / total + the active server name. Watch never
 * holds a bearer token; phone owns auth.
 *
 * If the DataItem is absent (datawatch isn't open on the phone yet, or
 * the phone is out of Bluetooth range) the screen shows "Open datawatch
 * on your phone" — not a pairing instruction, because pairing is handled
 * by Galaxy Wearable / Wear OS itself and is orthogonal.
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
private fun WearRoot(
    vm: WearSessionCountsViewModel =
        viewModel(
            factory =
                ViewModelProvider.AndroidViewModelFactory.getInstance(
                    LocalApp.current,
                ),
        ),
) {
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
            when {
                state.loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                }
                state.pairedServer.isEmpty() -> {
                    Text(
                        "Open datawatch on your phone",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface,
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CountTile(value = state.running, label = "run")
                        CountTile(value = state.waiting, label = "wait", highlight = true)
                        CountTile(value = state.total, label = "total")
                    }
                    Text(
                        state.serverName,
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
private fun CountTile(
    value: Int,
    label: String,
    highlight: Boolean = false,
) {
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

public class WearSessionCountsViewModel(app: Application) : AndroidViewModel(app) {
    public data class UiState(
        val loading: Boolean = true,
        val pairedServer: String = "",
        val serverName: String = "",
        val running: Int = 0,
        val waiting: Int = 0,
        val total: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    private val dataClient: DataClient = Wearable.getDataClient(app)

    private val listener =
        DataClient.OnDataChangedListener { buffer: DataEventBuffer -> consume(buffer) }

    init {
        // One-shot read of the existing DataItem so we don't flash the
        // "open datawatch" message when the watch UI launches while a
        // valid DataItem is already synced.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val items = dataClient.dataItems.await()
                try {
                    items.forEach { item ->
                        if (item.uri.path == COUNTS_PATH) {
                            applyMap(DataMapItem.fromDataItem(item).dataMap)
                        }
                    }
                } finally {
                    items.release()
                }
            }
            if (_state.value.loading) {
                _state.value = _state.value.copy(loading = false)
            }
        }
        dataClient.addListener(listener)
    }

    override fun onCleared() {
        super.onCleared()
        dataClient.removeListener(listener)
    }

    private fun consume(buffer: DataEventBuffer) {
        try {
            for (event in buffer) {
                if (event.type == DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == COUNTS_PATH
                ) {
                    applyMap(DataMapItem.fromDataItem(event.dataItem).dataMap)
                }
            }
        } finally {
            buffer.release()
        }
    }

    private fun applyMap(map: com.google.android.gms.wearable.DataMap) {
        _state.value =
            UiState(
                loading = false,
                pairedServer = map.getString("serverId", ""),
                serverName = map.getString("serverName", ""),
                running = map.getInt("running", 0),
                waiting = map.getInt("waiting", 0),
                total = map.getInt("total", 0),
            )
    }

    public companion object {
        public const val COUNTS_PATH: String = "/datawatch/counts"
    }
}

// Convenience for reaching the current Application from a Composable.
private object LocalApp {
    val current: Application
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = androidx.compose.ui.platform.LocalContext.current.applicationContext as Application
}

// Hoisted kotlinx-coroutines-play-services await() extension — the
// Tasks API is callback-based; this makes it suspendable without
// pulling in a new dependency.
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resumeWith(Result.success(it)) }
        addOnFailureListener { cont.resumeWith(Result.failure(it)) }
        addOnCanceledListener { cont.cancel() }
    }
