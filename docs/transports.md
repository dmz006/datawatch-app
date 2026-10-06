# Transports

*Created 2026-10-06 for v1.28.0.* Required by [AGENT.md](../AGENT.md) › Documentation
Rules › *New transport*. Sequence diagrams for each flow are in
[data-flow.md](data-flow.md).

Every network call the Android, Wear (through the phone), Android Auto and iOS apps make
goes through the shared Kotlin module (`shared/src/commonMain/kotlin/com/dmzs/datawatchclient/transport/`).
All of them talk to **user-configured datawatch servers only** (plus Google FCM / Apple
APNs for push wake, per [security-model.md](security-model.md)). The app is a pure
client: nothing is queued and replayed in the background (ADR-0013). A failed write shows
an error and the user retries.

| Transport | Code | Used for | Fallback |
|-----------|------|----------|----------|
| REST (HTTPS + JSON) | `rest/RestTransport.kt` behind `TransportClient` | Every read and write: sessions, Automata, settings, council, profiles, devices, commands | None. Errors surface as `TransportError` (Unauthorized / ServerError / RateLimited / Unreachable) |
| WebSocket `/ws` | `ws/WebSocketTransport.kt`, `ws/WsOutbound.kt`, hubs in `ws/` | Live session output, input, terminal resize, session-list / stats / automaton / alert / channel-ready pushes | REST polling (sessions list falls back from push to a 30 s poll) |
| SSE: council run events | `RestTransport.councilRunEvents`, `CouncilSseLineParser.kt`, `CouncilLive.kt` | Live council rounds and replies | Poll `GET /api/council/runs/{id}` every 5 s |
| SSE: Automata planning stream | `RestTransport.decomposeEvents`, `sse/DecomposeStream.kt` | Stories appearing live while an automaton is planned | Reconnect with `Last-Event-ID`; afterwards the detail reloads over REST |
| SSE: push alerts | `RestTransport.subscribePushAlerts` | UnifiedPush / in-app alert stream (`/api/push/alerts`) | Exponential backoff reconnect |
| MCP over SSE | `mcp/McpSseTransport.kt` | Transport primitive for the server's MCP SSE endpoint | Not wired to any screen today |

---

## REST

- **When it's used:** everything that isn't a live stream. `TransportClient` is the
  interface; `RestTransport` is the only implementation. One instance per server profile.
- **Prerequisites:** a server profile (base URL + bearer token, or the "no bearer token"
  opt-in). Every request sends `Authorization: Bearer <token>` when a token is set.
- **Engine:** Ktor. Android uses OkHttp (so NetworkSecurityConfig trust anchors apply);
  iOS uses the Darwin engine (URLSession).
- **Fallback:** none. Writes fail fast and visibly (ADR-0013). Some features probe a
  newer endpoint and fall back to an older one; for example the Automata tab reads
  `/api/autonomous/config` and falls back to `/api/config`.
- **Commands with an acknowledgement:** `POST /api/command {text}` runs the same command
  router as the WebSocket `command` frame but returns `{result}`. Scroll mode uses it
  (`tmux-copy-mode <id>`, `sendkey <id>: Escape`) so the UI changes only after the server
  confirms.
- **Limitations:** the server's REST responses are not versioned. The shared DTOs decode
  with `ignoreUnknownKeys` and default every new field, so newer servers don't break older
  apps. Older servers simply lack the field or endpoint, and the screen hides or falls back.
- **Security notes:**
  - TLS is always verified.
  - A server can be **pinned** to its certificate's SHA-256 (Android `AndroidCertPinning`;
    iOS since v1.23.x): only that certificate is accepted, and the hostname must still
    match.
  - The insecure "trust all certificates" opt-in applies **only to that profile's host**
    (since v1.27.0). Other hosts, including the docs viewer, go through the system trust
    store.
  - Literal API keys in LLM / profile bodies are masked in the UI and restored from the
    stored copy on save (`SecretMask`, `ProfileSecrets`). They are never displayed, and
    bearer tokens are never logged.

## WebSocket `/ws`

- **When it's used:**
  - Session detail opens a socket and sends a `subscribe` frame for that session. The
    server pushes output, `pane_capture`, chat messages, state, alerts and
    `channel_ready`.
  - Outbound frames (`WsOutbound`) carry `send_input`, `resize_term` and `command`
    (`sendkey …`, `tmux-copy-mode …`).
  - A **global stream** (`globalStream()`) subscribes to no session and routes
    session-list (`sessions` / single-session `session_state` diffs), stats and automaton
    frames to `SessionsHub`, `StatsHub` and `PrdHub`, so lists update live.
  - `WsConnectionHub` counts open sockets and drives the red / green status dot.
  - `ChannelReadyHub` records the `channel_ready` frame and scans output for the
    MCP-channel / ACP ready markers to clear the "Waiting for MCP channel…" banner.
- **Prerequisites:** same profile and token as REST. `http://` becomes `ws://` and
  `https://` becomes `wss://`. Android uses a dedicated WS client (`AndroidWsHttpClient`)
  with the same trust rules.
- **Fallback:**
  - Reconnects with jittered exponential backoff: 500 ms, doubling, 60 s cap, plus up to
    500 ms jitter.
  - The session terminal retries 3 times before offering Retry / "Use without terminal".
  - Lists fall back to REST polling while the socket is down.
  - Replies are never queued.
- **Limitations:**
  - A frame sent while the socket is reconnecting is dropped silently. That is why scroll
    mode moved to REST `/api/command`; the WS `command` frame is now only its fallback.
  - The server has no replay, so missed output is recovered from the next `pane_capture`.
- **Security notes:**
  - Same TLS / pinning / trust-all scoping as REST.
  - The bearer token goes in the upgrade request. It never appears in a URL or a log.

## SSE: council live run events

- **When it's used:** after `POST /api/council/run` returns `{id, events_path}`, the app
  opens `GET /api/council/runs/{id}/events` (`text/event-stream`) and reduces each event
  (`hello`, round started, persona reply, run completed, …) with `CouncilLiveReducer`.
  Android and iOS share `TransportClient.watchCouncilRun`.
- **Prerequisites:** a server with Council Mode and at least one working LLM (otherwise the
  run errors).
- **Fallback:**
  - On `hello` for a run that has already finished, the app shows the persisted run detail
    instead (the server keeps no replay).
  - After the terminal event, it fetches `GET /api/council/runs/{id}` (up to 3 × 1 s) to
    fill in replies sent before it connected.
  - If the stream drops, it polls the detail every 5 s (up to 720 polls) until the run is
    persisted, then shows "Lost connection to the council run." if it never is.
- **Limitations:**
  - No `Last-Event-ID` replay on this topic, so a reconnect cannot resume the stream.
  - No server keepalive. The read is held open with a 15-minute socket timeout, because a
    persona can think for minutes.
- **Security notes:** bearer auth on the stream request; same TLS rules as REST. Cancel is
  `POST /api/council/runs/{id}/cancel`.

## SSE: Automata live planning (decompose) stream

- **When it's used:** after `POST /api/autonomous/prds/{id}/decompose` (202
  `{task_id, stream_url}`), the app opens
  `GET /api/autonomous/prds/{id}/decompose/stream`. Each frame is `id: N` plus a JSON
  `data:` line, where `type` is `story`, `progress`, `complete` or `error`.
  `DecomposeLiveState` dedupes replayed stories.
- **Prerequisites:** a server with Automata enabled and the live planning stream.
- **Fallback:**
  - On a drop, the app reconnects with `Last-Event-ID: <last id>` (the server replays
    everything after it).
  - Backoff is 1 s, 2 s, 4 s … capped at 30 s, giving up after 3 consecutive failures.
  - A `404` means there is no planning job (already done or never started) and the stream
    ends quietly.
  - The automaton detail reloads over REST either way.
- **Limitations:** the server keepalive is 25 s, so the read uses a 60 s socket timeout.
  Events are only for the automaton being planned.
- **Security notes:** bearer auth; same TLS rules as REST.

## SSE: push alerts

- **When it's used:** `POST /api/push/register` registers the device's push endpoint. While
  the app runs, `GET /api/push/alerts` streams alert events (UnifiedPush tier).
- **Fallback:** exponential backoff reconnect starting at 1 s. When no push tier is active,
  alerts still reach the app over the WebSocket while it is open.
- **Limitations:**
  - Android only.
  - iOS push goes through APNs instead: the app calls `POST /api/devices/register` with
    `kind: apns` and `apns_environment` on every launch. Delivery needs the server's APNs
    sender ([dmz006/datawatch#183](https://github.com/dmz006/datawatch/issues/183)).
- **Security notes:** the stream carries alert titles and bodies. Notification content
  stays on the device.

## MCP over SSE

- **When it's used:** `McpSseTransport.listen()` streams MCP JSON-RPC frames from a server's
  MCP SSE endpoint, which runs on its own port and can have its own token
  (`McpSseEndpoint`). It is a primitive. As of v1.28.0 no screen opens it; the MCP tools
  card in About reads `GET /api/mcp/docs` over REST.
- **Fallback:** exponential backoff reconnect.
- **Limitations:** no `Last-Event-ID` replay and no auto-resume.
- **Security notes:** the endpoint's bearer token is kept separate from the REST token, so
  the two are never conflated.

## Not transports (for completeness)

- **Intent relay / DNS TXT channel** (data-flow diagrams 10–11): design-time fallbacks.
  They are not implemented as `TransportClient` transports in v1.28.0.
- **Wear OS** never talks to a server. It reads the phone's Wearable Data Layer items
  (`/datawatch/*`).
- **Android Auto** uses the phone's shared `TransportClient`.
