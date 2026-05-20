# Feature Ideas — datawatch + Android/Wear OS/Android Auto

Living list of improvements, enhancements, and fun ideas. Add as they come up during testing.

---

## Android App

### Quality of Life
- **Session transcript export** — share session history as Markdown or JSON to Files/Share sheet
- **Multi-select bulk actions** — select multiple sessions for bulk kill/restart/tag
- **PiP (Picture-in-Picture) monitor** — float a session status bubble while using other apps
- **Offline / last-seen cache** — show last-known state when server unreachable, clearly marked stale
- **Session timeline scrubber** — horizontal scroll through session events by time
- **Biometric gate for commands** — require fingerprint before sending kill/restart to high-risk sessions
- **Session tags / labels** — user-defined labels; filter sessions list by tag
- **Deep-link from notification** — tap notification → lands on exact session detail, not just the app
- **Push notification grouping** — group by server so a busy server doesn't flood the notification shade
- **Saved command quick-tile** — Android Quick Settings tile to launch a saved command in one tap

### Autonomous / PRD
- **PRD decompose streaming UI** — server now streams stories via SSE (v8.2.0 BL328); mobile should show them appearing one by one with a live counter "3/8 stories generated…"
- **PRD template gallery** — pick from common project types (web app, CLI tool, refactor, etc.)
- **PRD progress widget** — home-screen widget showing active PRD name + % complete
- **Council reply threading** — show each persona's response as a collapsible thread in session view
- **Inline task approval** — approve/reject individual PRD tasks with swipe gesture
- **Decompose reconnect** — if the SSE stream drops mid-decompose, reconnect via `Last-Event-ID` and resume from where it left off (server already supports this in v8.2.0)
- **Discussion scope viewer** — in-session view showing the discussion WAL for cross-agent memory (Discussion Scopes shipped in v8.4.0)

### Dashboard
- **Real-time dashboard push** — WebSocket subscription so cards update without polling
- **Dashboard card reorder** — drag-and-drop card ordering on the Dashboard tab
- **Custom card color themes** — per-card accent color for visual triage
- **Session spotlight card** — pin one session's telemetry as a full-width hero card
- **Alert heatmap card** — show alert frequency by hour-of-day across the week

### Comms & Notifications
- **In-app notification inbox** — persistent tray of all received alerts/replies
- **Scheduled silence windows** — don't notify between 22:00–07:00 (configurable)
- **Reply templates** — pre-written quick replies: "ok", "stop", "continue", "I'll check later"

---

## Wear OS

- **Watch face complication** — active session count badge on any watch face
- **Per-alert-severity haptic patterns** — short pulse for info, double for warning, long for critical
- **Voice reply from wrist** — dictate a reply to a waiting session without touching the phone
- **Session tiles** — swipe-up tile showing top 3 active sessions with one-tap focus/kill
- **Wrist-tap quick commands** — configure a saved command to fire on double-tap gesture
- **Watch-only mode** — minimal status display even when phone is out of Bluetooth range (cached)
- **Health-sensor session trigger** — start a session when heart rate crosses a threshold (fun/experimental)

---

## Android Auto

- **"What's my session doing?" voice command** — full TTS response with current session state
- **Session queue announcements** — announce new waiting_input sessions while driving
- **Hands-free quick-reply** — voice dictation reply to sessions via Auto mic
- **Traffic-light session status** — green/yellow/red indicator per session on summary screen
- **Turn-by-turn style alerts** — interrupt music briefly to announce critical session alerts
- **Auto-launch on drive start** — optionally open datawatch Auto screen when CarPlay/AA connects

---

## Datawatch Server

### API & Performance
- ~~**PRD decompose streaming**~~ — ✅ shipped v8.2.0 (BL328), async SSE
- **WebSocket real-time session events** — push session state changes to mobile instead of polling
- **Session cost tracking** — token usage × configured cost rates per session, shown in telemetry
- **Batch session operations** — `POST /api/sessions/batch { action: "kill", filter: { tag: "test" } }`
- **LLM hot-swap** — change an active session's backend mid-run without restart
- **Session archiving API** — mark sessions as archived; mobile shows them in a separate "Archive" tab
- **File service mobile UI** — server has federated file service (v8.3.0 BL333); mobile needs upload/browse/share UI beyond the Settings card
- **Discussion scope conflicts UI** — server detects WAL write conflicts (v8.4.0); mobile could surface a "conflict detected" badge when two agents wrote conflicting entries

### Intelligence
- **Conditional alert rules** — "alert if session in waiting_input for >15 minutes"
- **Session pattern detection** — flag sessions that match historical "about to fail" patterns
- **Cross-session memory search** — global search across all session memories from mobile
- **PRD similarity detection** — warn before creating a PRD that looks identical to an existing one
- **Council auto-quorum** — skip unanimous council votes; only surface dissenting opinions

### Multi-Server / Federation
- **Differential config sync** — propagate config changes from primary to peer servers
- **Cross-server session migration** — move a running session to another server
- **Federation health dashboard** — see all peer server health in one view on mobile
- **Channel routing mobile UI** — server v8.3.0 has channel routing rules (channel pattern → peer); mobile Settings → Comms card exists but could also surface active routing decisions in the Observer tab
- **File sharing between peers** — server v8.3.0 has `GET /api/files/peers/{name}`; mobile could show files shared by federated peers in a "Shared Files" section

### Identity & Context
- ~~**Identity endpoint**~~ — ✅ shipped v8.2.0 (BL329), GET/POST/PUT/PATCH /api/identity
- ~~**UnifiedPush registration**~~ — ✅ shipped v8.2.0 (BL330), full push pipeline
- **Context auto-summarize** — periodically compress identity context_notes using LLM
- **Per-project identity profiles** — different role/focus when working in different project directories
- **Encrypted identity store** — identity fields encrypted at rest when `--secure` mode active (server encrypts inference/llms.json and others in v8.6.0; identity.json could follow)

### Matrix Backend (v8.7.0 / BL241)
- ~~**Matrix.org backend foundation**~~ — ✅ shipped v8.7.0 (BL241-P1): cleartext send, alias resolution, bridge classifier, 7-surface parity
- **Matrix channel status in Observer** — server moved Matrix status to Observer tab in PWA (v8.7.0); mobile Observer tab needs a Matrix backend status card to match (gap: mobile has config in Settings → Comms but no Observer status)
- **Matrix secret ref enforcement** — v8.7.0 enforces `auth_secret_ref` (no plaintext tokens); mobile `access_token` field should show a hint to use `${secret:name}` vault references
- **Matrix session_id embed** — v8.7.0 embeds session IDs in Matrix messages (Q5.3); mobile session detail could show the Matrix room thread link

### Security
- **Encryption status card** — server v8.6.0 has `GET /api/security/encryption/status`; mobile Settings → About should surface whether encryption is active and which stores are protected
- **Secure mode toggle** — one-tap enable/disable of `--secure` mode from mobile (with confirmation dialog given the migration step involved)
- **Encrypted log viewer** — server v8.6.0 has `datawatch security logs`; mobile could show daemon log tail in Settings → About with decryption handled transparently

---

## Fun / Experimental

- **"Daemonwatch" mode** — mobile monitors a session for signs of getting stuck and auto-nudges it
- **Session "mood ring"** — ML classifier on token output patterns to detect "confused", "on-track", "spinning"
- **Wear OS health-triggered sessions** — start a focus session when heart rate drops (idle detected)
- **AR session overlay** — ARCore overlay showing session status floating above your desk (very extra)
- **Voice-activated server reboot** — "Hey datawatch, restart the daemon" via Wear OS mic
- **Session music pairing** — low-focus lo-fi playlist when in waiting_input, stop when active
