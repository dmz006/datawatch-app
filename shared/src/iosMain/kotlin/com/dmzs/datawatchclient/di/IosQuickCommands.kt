package com.dmzs.datawatchclient.di

import com.dmzs.datawatchclient.domain.ServerProfile
import com.dmzs.datawatchclient.domain.Session
import com.dmzs.datawatchclient.transport.ws.WsOutbound
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A saved command from `GET /api/commands`, flattened for Swift. */
public data class IosSavedCommand(val name: String, val command: String)

/**
 * Quick commands from the session list (parity B4, PWA `cardSendCmd`). The list has
 * no open session socket, so each send opens a short-lived `/ws` subscription to the
 * session, waits for the first frame (server is now routing for it), emits the frame
 * through [WsOutbound], then closes.
 */
public object IosQuickCommands {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    public fun loadSaved(
        profile: ServerProfile,
        onResult: (List<IosSavedCommand>) -> Unit,
    ) {
        scope.launch {
            val list = IosServiceLocator.transportFor(profile).listCommands().getOrNull().orEmpty()
            onResult(list.map { IosSavedCommand(it.name, it.command) })
        }
    }

    /**
     * [value] is a command (sent as text + Enter) or one of the PWA's special values:
     * `__esc__` (Escape key) and `__ctrlb__` (tmux prefix).
     */
    public fun send(
        profile: ServerProfile,
        session: Session,
        value: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            val firstFrame = CompletableDeferred<Unit>()
            val stream =
                launch {
                    IosServiceLocator.wsTransportFor(profile)
                        .events(session.fullId, session.id)
                        .collect { firstFrame.complete(Unit) }
                }
            val connected = withTimeoutOrNull(8_000) { firstFrame.await() } != null
            if (!connected) {
                stream.cancel()
                onError("Couldn't reach the session to send the command.")
                return@launch
            }
            val emitted =
                when (value) {
                    "__esc__" -> WsOutbound.sendCommand(session.id, "sendkey ${session.fullId}: Escape")
                    "__ctrlb__" -> WsOutbound.sendCommand(session.id, "sendkey ${session.fullId}: C-b")
                    else -> WsOutbound.sendInput(session.id, value + "\r")
                }
            // Give the writer a moment to flush before closing the socket.
            delay(600)
            stream.cancel()
            if (emitted) onSuccess() else onError("Send failed — try again.")
        }
    }
}
