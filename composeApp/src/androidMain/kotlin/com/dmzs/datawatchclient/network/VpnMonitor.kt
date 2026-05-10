package com.dmzs.datawatchclient.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public enum class VpnState { Connected, Dropped, Unknown }

/**
 * Monitors the Tailscale VPN connection via [ConnectivityManager.NetworkCallback].
 *
 * Always-on VPN: after a drop, waits 30 s for Android to auto-restart the VPN
 * before logging a warning. Non-always-on VPN: logs immediately on drop.
 *
 * Started from [com.dmzs.datawatchclient.DatawatchApp.onCreate]; the transport
 * layer can observe [state] to skip retries while VPN is known to be down.
 */
public class VpnMonitor(private val context: Context) {
    private val _state = MutableStateFlow(VpnState.Unknown)
    public val state: StateFlow<VpnState> = _state

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Always-on VPN detection requires MANAGE_VPN_STATE permission (not available to 3rd-party
    // apps). Fallback: check if device is API 30+ and assume non-always-on for now.
    // A future sprint can wire this via Settings.Secure or device policy if needed.
    private val isAlwaysOn: Boolean
        get() = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _state.value = VpnState.Connected
            dropJob?.cancel()
        }

        override fun onLost(network: Network) {
            handleVpnDrop()
        }
    }

    private var dropJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    public fun start() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    public fun stop() {
        try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        scope.cancel()
    }

    private fun handleVpnDrop() {
        _state.value = VpnState.Dropped
        if (isAlwaysOn) {
            // Always-on VPN: Android will restart it — wait 30 s silently before notifying
            dropJob = scope.launch {
                delay(30_000)
                if (_state.value == VpnState.Dropped) {
                    showVpnDropNotification()
                }
            }
        } else {
            // Non-always-on: notify immediately + show wake intent
            showVpnDropNotification()
        }
    }

    private fun showVpnDropNotification() {
        android.util.Log.w("VpnMonitor", "VPN dropped — user should re-enable Tailscale")
    }
}
