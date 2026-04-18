# Data Flow

Sequence diagrams for every interaction the app performs. Numbering matches the feature
rollout in `sprint-plan.md`.

## 1. Bootstrap + server pairing

```mermaid
sequenceDiagram
    actor U as User
    participant App as Phone app
    participant KS as Android Keystore
    participant DB as Encrypted DB
    participant DW as datawatch server

    U->>App: First launch
    App->>KS: Generate master key (alias: dw.master)
    KS-->>App: Key handle
    App->>DB: Open SQLCipher with key
    U->>App: Enter Server URL + bearer token
    App->>DW: GET /api/health (bearer)
    DW-->>App: 200 OK + server version
    App->>DB: INSERT ServerProfile (token encrypted)
    App->>DW: POST /api/devices/register (FCM token, app version)
    DW-->>App: device_id
    App->>DB: UPDATE profile.device_id
    App-->>U: Home screen, profile listed
```

Note: `POST /api/devices/register` is a **proposed new endpoint on the parent datawatch
server**. If unavailable, fallback is subscribing to the user's configured ntfy topic for
push wake. Needs confirmation before requiring parent change.

## 2. List sessions (active server + all-servers fan-out)

```mermaid
sequenceDiagram
    actor U as User
    participant App as Phone app
    participant Cache as SQLDelight cache
    participant T as TransportClient
    participant DW as datawatch server

    U->>App: Open Sessions tab
    App->>Cache: SELECT sessions WHERE profile_id = active
    Cache-->>App: Cached rows + last_seen_ts
    App-->>U: Render stale list instantly
    par Live refresh
        App->>T: GET /api/sessions
        T->>DW: HTTPS
        DW-->>T: Session[]
        T-->>App: Sessions
        App->>Cache: UPSERT
        App-->>U: Refresh UI with fresh data
    end
```

"All-servers" view fans out one request per enabled profile in parallel with coroutines.

## 3. Open session detail + live terminal

```mermaid
sequenceDiagram
    actor U as User
    participant App as Phone app
    participant WS as WebSocket /ws
    participant DW as datawatch server
    participant Web as xterm.js WebView

    U->>App: Tap session row
    App->>WS: CONNECT wss://server/ws?session=ID (bearer)
    WS->>DW: Upgrade
    DW-->>WS: Session output frames (stdout/stderr/ansi)
    App->>Web: Load xterm bundle from assets/xterm/
    loop Stream
        WS-->>App: frame
        App->>Web: postMessage(frame)
        Web-->>U: Rendered terminal
    end
```

Connection drops → grey-out banner after 30 s (ADR-0013) + "reconnect" button. No replay
buffer; the daemon holds session scrollback.

## 4. Reply to a prompt (phone)

```mermaid
sequenceDiagram
    actor U as User
    participant App as Phone app
    participant T as TransportClient
    participant DW as datawatch server

    U->>App: Type reply + Send
    alt Online
        App->>T: POST /api/sessions/reply {id, text}
        T->>DW: HTTPS
        DW-->>T: 200
        T-->>App: OK
        App-->>U: Message pinned in chat
    else Offline
        App-->>U: Red "Not sent — tap to retry" banner
        Note over App: ADR-0013 — not queued, user action required
    end
```

## 5. Voice reply (any surface)

```mermaid
sequenceDiagram
    actor U as User
    participant Src as FAB / composer / tile / ASSIST
    participant V as VoiceCapture
    participant T as TransportClient
    participant DW as datawatch server

    U->>Src: Press-and-hold
    Src->>V: start()
    V-->>Src: recording...
    U->>Src: Release
    Src->>V: stop()
    V-->>Src: audio blob (opus/ogg, mono)
    Src->>T: POST /api/voice {audio, session_id?}
    T->>DW: HTTPS (multipart)
    DW->>DW: Whisper transcribe
    alt Prefix recognized (new:/reply:/status:)
        DW->>DW: Auto-execute
        DW-->>T: {transcript, action, session_id}
        T-->>Src: success + summary
        Src-->>U: TTS confirmation
    else Free text
        DW-->>T: {transcript}
        T-->>Src: transcript
        Src-->>U: Preview → Send
    end
```

## 6. Push wake + deep link (FCM primary path)

```mermaid
sequenceDiagram
    participant DW as datawatch server
    participant FCM as Google FCM
    participant App as Phone app (backgrounded or killed)
    participant WS as WebSocket
    actor U as User

    DW->>FCM: Send wake (dumb ping, no payload)
    FCM-->>App: onMessageReceived
    App->>App: Read event kind from secure notification channel
    App->>DW: GET /api/sessions/{id}?since=last_seen (bearer)
    DW-->>App: Delta events + pending prompt
    App-->>U: Notification (prompt summary, actions)
    alt Input needed
        U->>App: Tap "Reply"
        App->>WS: CONNECT /ws?session=ID
    else Mute 10m
        App->>App: Silence this session_id until ts + 10m
    end
```

## 7. ntfy fallback push

```mermaid
sequenceDiagram
    participant DW as datawatch server
    participant Ntfy as ntfy.sh or self-hosted
    participant App as Phone app

    DW->>Ntfy: PUT /topic {title, body, actions}
    App->>Ntfy: Long-poll SSE subscription (token)
    Ntfy-->>App: event
    App->>App: Deliver local notification
```

Used when user's server doesn't have FCM configured. Higher battery cost; foreground service
with notification shown for transparency.

## 8. MCP tool invocation (streaming)

```mermaid
sequenceDiagram
    actor U as User
    participant App as Phone app
    participant McpC as MCP SSE client
    participant DW as datawatch server

    U->>App: Quick command / tool picker → "memory_recall"
    App->>McpC: invoke("memory_recall", {query})
    McpC->>DW: POST /mcp/sse {tool, args} (bearer)
    loop Streaming
        DW-->>McpC: event: tool_result chunk
        McpC-->>App: Flow<McpStream>
        App-->>U: Incremental rendering
    end
    DW-->>McpC: event: done
    McpC->>App: Close
```

## 9. Proxy chain — drill into a child server

```mermaid
sequenceDiagram
    actor U as User
    participant App as Phone app
    participant Primary as Primary datawatch (proxy)
    participant Child as workstation-a3f2 (proxied)

    U->>App: Server picker → primary › us-west-2 › workstation-a3f2
    App->>Primary: GET /api/servers/workstation-a3f2/sessions (bearer)
    Primary->>Child: Relay (datawatch proxy mode)
    Child-->>Primary: Sessions
    Primary-->>App: Sessions (hostname-prefixed)
    App-->>U: Render with breadcrumb primary › us-west-2 › workstation-a3f2
```

The mobile app never contacts proxied children directly — the primary relays per parent
datawatch proxy mode. This keeps the trust boundary at the primary.

## 10. Android Intent handoff fallback (direct API unreachable)

```mermaid
sequenceDiagram
    actor U as User
    participant App as Phone app
    participant Sig as Signal app (on device)
    participant DW as datawatch server (watching Signal backend)

    App->>App: ping /api/health → timeout
    App->>App: Profile relay preference = Signal
    App-->>U: "Can't reach server — send via Signal?"
    U->>App: Yes
    App->>Sig: Intent.ACTION_SENDTO (pre-fill text to configured Signal ID)
    U->>Sig: Tap Send
    Sig->>DW: (via Signal network → datawatch signal-cli bridge)
    DW->>DW: Process command
    Note over DW,App: Response will arrive via next push / reconnect
```

## 11. DNS TXT covert channel (low-bandwidth last resort)

```mermaid
sequenceDiagram
    participant App as Phone app
    participant DNS as Recursive DNS resolver
    participant DW as datawatch server (DNS channel listener)

    App->>App: Build command + HMAC + nonce (parent datawatch spec)
    App->>DNS: TXT query for <cmd>.<nonce>.<domain>
    DNS->>DW: Forward
    DW-->>DNS: TXT record = ack + ref_id
    DNS-->>App: ack
    App->>App: Poll TXT <ref_id>.<domain> for response
    DW-->>App: TXT chunks, reassembled
```

## 12. Wear OS notification + reply action

```mermaid
sequenceDiagram
    participant Phone as Phone app
    participant WearSvc as Wear Data Layer
    participant Watch as Wear app
    actor U as User
    participant DW as datawatch server

    Phone->>WearSvc: Post MessagingStyle notification
    WearSvc-->>Watch: Mirror
    Watch-->>U: Buzz
    U->>Watch: Tap "Reply"
    Watch->>U: Voice dictation UI
    U->>Watch: Speak
    Watch->>WearSvc: RemoteInput reply
    WearSvc->>Phone: Deliver
    Phone->>DW: POST /api/sessions/reply
    DW-->>Phone: 200
    Phone->>WearSvc: Update notification (sent ✓)
```

## 13. Android Auto — public Messaging build

```mermaid
sequenceDiagram
    participant Phone as Phone app
    participant Auto as Auto messaging session
    participant Car as Head unit
    actor U as User
    participant DW as datawatch server

    DW->>Phone: Push (prompt needs input)
    Phone->>Auto: CarMessageController.post(Message)
    Auto-->>Car: Read title + TTS body
    Car-->>U: Audio
    U->>Car: "Reply"
    Car->>Auto: Voice reply intent
    Auto->>Phone: Transcribed reply
    Phone->>DW: POST /api/sessions/reply
    DW-->>Phone: OK
    Auto-->>Car: "Sent"
```

## 14. Backup + restore

```mermaid
sequenceDiagram
    participant App as Phone app
    participant AAB as Android Auto Backup
    participant GD as Google Drive

    Note over App: Nightly, platform-driven
    App->>AAB: AutoBackupAgent.onBackup
    AAB->>AAB: Include encrypted DB + settings.json (no keystore)
    AAB->>GD: Upload encrypted archive (Google key)
    Note over App: On new device
    AAB-->>App: onRestore
    App->>App: DB restored; Keystore material missing
    App-->>U: "Re-enter server token to unlock"
    U->>App: Paste token
    App->>KS: Import + rebind
    App-->>U: Ready
```

## Error + reconnection behavior summary

| State | UI | Behavior |
|---|---|---|
| Online, healthy | No banner | Normal |
| Transient network blip (< 30 s) | Amber pill "syncing" | Retry exponential |
| Unreachable (> 30 s) | Grey-out + red banner "Disconnected from `<server>` — last sync 4m ago" | No writes; reads from cache, clearly marked stale |
| Write attempted while disconnected | Inline red "Not sent — Retry" | User-driven retry only (ADR-0013) |
| Audio upload fails | Retry card with waveform | User taps retry; blob persisted in retry UI until resolved (ADR-0027) |
