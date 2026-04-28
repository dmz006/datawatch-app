package com.dmzs.datawatchclient.ui.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dmzs.datawatchclient.di.ServiceLocator
import com.dmzs.datawatchclient.transport.dto.ObserverEbpfDto
import com.dmzs.datawatchclient.ui.settings.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings → Monitor → eBPF status card. Mirrors PWA
 * `loadEBPFStatus()` (datawatch v4.1.1+).
 *
 * Renders three independent flags read from
 * `/api/observer/stats.host.ebpf`:
 *
 * - **configured** — operator opted in (`observer.ebpf_enabled =
 *   true|auto`)
 * - **capability** — CAP_BPF granted on the running binary
 *   (probed from /proc/self/status CapEff bit 39)
 * - **kprobes_loaded** — BL173 loader actually attached (false
 *   until v4.5.0+ deploys with bpf2go output)
 *
 * Plus a human-readable `message` line when non-blank. Card hides
 * itself when the daemon predates the observer endpoint and
 * returns no `host.ebpf` block.
 *
 * v0.36.0 — issue #4.
 */
@Composable
public fun EBpfStatusCard(vm: EBpfStatusViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    val ebpf = state.ebpf ?: return

    Section(title = "eBPF status") {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EBpfFlag("configured", ebpf.configured)
                EBpfFlag("capability", ebpf.capability)
                EBpfFlag("kprobes", ebpf.kprobesLoaded)
            }
            ebpf.message?.takeIf { it.isNotBlank() }?.let { msg ->
                Text(
                    msg,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EBpfFlag(label: String, ok: Boolean) {
    val color = if (ok) Color(0xFF22C55E) else Color(0xFFEF4444)
    Row(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (ok) "✓" else "✕",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

public class EBpfStatusViewModel : ViewModel() {
    public data class UiState(val ebpf: ObserverEbpfDto? = null)
    private val _state = MutableStateFlow(UiState())
    public val state: StateFlow<UiState> = _state

    public fun refresh() {
        viewModelScope.launch {
            val activeId = ServiceLocator.activeServerStore.get() ?: return@launch
            val profile =
                ServiceLocator.profileRepository.observeAll().first()
                    .firstOrNull { it.id == activeId && it.enabled } ?: return@launch
            ServiceLocator.transportFor(profile).observerStats().fold(
                onSuccess = { dto ->
                    _state.value = UiState(ebpf = dto.host?.ebpf)
                },
                onFailure = { _state.value = UiState(ebpf = null) },
            )
        }
    }
}
