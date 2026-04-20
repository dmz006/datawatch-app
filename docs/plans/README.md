# Plans, bugs, and backlog tracker

Single source of truth per AGENT.md "Project Tracking". IDs are permanent — never
reuse a bug (B#), backlog (BL#), or feature (F#) number.

Plans: individual dated documents live as `YYYY-MM-DD-<slug>.md` in this directory
when work warrants formal planning (3+ files or non-trivial architecture).

## Active bugs

| ID | Title | Reported | Status | Notes |
|----|-------|----------|--------|-------|
| B1 | Terminal appears frozen / unreadable on TUI sessions (e.g. Claude Code) | 2026-04-19 | Investigating — partial mitigation | Live-validated 2026-04-20 00:05 against session `787e*` on the user's Galaxy S24 Ultra (Android 16). **Actual root cause is NOT a freeze.** Logcat confirms `onReady` + FitAddon + `initial flush` + continuous `incremental:` writes all fire correctly. Screenshot shows scattered single characters (`p g`, `a i`, `B strapping…` / `Bootstrapping…`) — the server is streaming a TUI (Claude Code's cursor-positioned UI) built for ~80 cols, and xterm is sized to 39 cols × 29 rows (FitAddon's honest answer to a 384 px viewport). Cursor-position escapes like `\e[12;45H` address columns that don't exist, xterm drops the char into wrap territory, result is unreadable. Phase 1 (write-cursor keyed to sessionId) still landed and did fix a real latent bug — but it was not the primary symptom. Real fix needs server-side PTY resize driven by a client-sent `{"type":"resize","cols":N,"rows":M}` WS frame; the WebSocket transport has no outbound API for this today, and parent-repo coordination is required to confirm the server accepts it. Short-term mitigation shipped: WebView pinch-zoom enabled so the full 80-col TUI can be panned/zoomed. Proper fix tracked as **BL18** (WS resize negotiation) for v1.1. |
| B2 | Android Auto head unit does not list the datawatch app | 2026-04-19 | Open | User confirmed the phone app is installed; Auto category `androidx.car.app.category.MESSAGING` is declared on `com.dmzs.datawatchclient` (public) in `auto/src/publicMessaging/AndroidManifest.xml`. Likely causes: (a) user is running the `.dev` variant, which is Auto-enabled only in the `devPassenger` flavor behind the gate from ADR-0042; (b) CarAppService isn't being enumerated because the car-app minimum API on the head unit is below what we declare; (c) Play "unknown sources" Auto list needs a DHU-side toggle. Verify by `adb shell pm list packages \| grep datawatch` + check which applicationId is installed. |
| B3 | Swipe-to-mute on a session row does not toggle mute | 2026-04-19 | Open | Gesture is in `SessionsScreen` swipe handler; mute state is round-tripped via `/api/sessions/mute` on the server and locally in `SessionRepository`. Verify gesture threshold fires, transport call succeeds, and DB mute flag is observed by the list. |
| B4 | Channels tab shows "server unreachable" | 2026-04-19 | Open | `ChannelsScreen` reads `/api/backends` on the active profile. "Server unreachable" is the generic unreachable banner from `TransportError.Unreachable`. Likely causes: (a) active profile has a stale URL; (b) `/api/backends` is not exposed on the server version the user is pointing at; (c) bearer token rejected; (d) the screen is retrying a cached profile after the active profile was swapped. Confirm by tapping a session in the same session — if Sessions works but Channels fails, it's endpoint-specific. |
| B5 | Stats screen is minimal compared to PWA | 2026-04-19 | Open | `StatsScreen` shows CPU / Memory / Disk / GPU bars + session counts + uptime (`/api/stats`, 5 s poll). PWA exposes more (eBPF per-process network, disk-partition breakdowns, GPU detail). Extended metrics are out-of-scope per ADR-0019 and are view-only on the parity-plan (v1.3). This bug tracks the **UX perception** — title, density, legend — not the server data contract. Decide: is this a bug (fix layout/density) or a BL (re-scope)? Needs user triage. |
| B6 | Push notifications not confirmed working | 2026-04-19 | Open | `PushRegistrar` fires `POST /api/devices/register` on first successful connection (FCM token preferred, ntfy fallback). Diagnostic steps: (1) Settings → Diagnostics → last push registration result; (2) `adb logcat PushRegistrar:V NtfyFallbackService:V`; (3) server-side `GET /api/devices` to confirm the phone's registration; (4) trigger a test from the server. No code change made until a failure mode is isolated. |

## Planned / In Progress

v0.10.0 shipped 2026-04-19 (originally mis-tagged v1.0.0 — the 1.0 label
is reserved for full PWA parity; see `docs/parity-status.md`); v0.10.1
(terminal-primary session UX) followed the same day. Parity feature work
is scheduled in [`docs/parity-plan.md`](../parity-plan.md) — the v0.11 →
v0.14 PWA parity roadmap is the authoritative planning surface and is
re-audited at every release close against the parent repo's
`internal/server/web/` + `docs/api/openapi.yaml`. v1.0.0 tags the release
that closes that audit with every row ✅.

| Release | Theme | Parity-plan anchor |
|---------|-------|--------------------|
| v0.11.0 | Session power-user parity (rename / restart / delete, terminal search+copy, start-session form, active backend picker, CA cert, connection status) | [parity-plan §6 v0.11.0](../parity-plan.md#v0110--session-power-user-parity) |
| v0.12.0 | Channels + schedules + file picker + session prefs + timeline + per-session model pickers + daemon log viewer | [parity-plan §6 v0.12.0](../parity-plan.md#v0120--channels--schedules--file-picker) |
| v0.13.0 | Memory / KG panel + structured daemon config editor + eBPF view + daemon update | [parity-plan §6 v0.13.0](../parity-plan.md#v0130--memory--kg--daemon-config) |
| v0.14.0 | Federation polish (federated servers view, cross-server memory diff, peer broker status) | [parity-plan §6 v0.14.0](../parity-plan.md#v0140--federation-polish) |
| v1.0.0 | 100% PWA parity — every row in `docs/parity-status.md` flips to ✅ | — |

## Backlog

Items here are **not yet** scheduled against a minor release. When a backlog
item is pulled into a v1.x milestone, note the target in the parity-plan and
leave the BL# row here until it ships, then move it to Completed backlog.

| ID | Title | Notes |
|----|-------|-------|
| BL1 | Split consolidated `decisions/README.md` into per-ADR MADR files | Low priority; 41 ADRs. Docs-only. |
| BL3 | Tablet two-pane layout | Post-1.0 (design + responsive audit required) |
| BL5 | iOS content phase | Skeleton only in v0.10.0; real content lands after Android parity stabilises |
| BL7 | Foldable layout | Post-1.0 |
| BL11 | Full schedule editor CRUD | Covered by v0.12 parity scope — keep BL11 open until shipped |
| BL12 | KG Add / Timeline / Research deeper views | Covered by v0.13 parity scope — keep BL12 open until shipped |
| BL13 | Adjustable terminal dimensions | Post-1.0; depends on xterm reflow UX decisions |
| BL14 | Raw YAML config editor (gated behind biometric + confirm) | Post-1.0; revisits ADR-0019. Deliberately out-of-scope for v0.13 structured editor. |
| BL15 | Localization (DE, ES, FR, JA) | Post-1.0 |
| BL16 | Biometric-bound DB passphrase | v0.10.0 gates only the UI with BiometricPrompt; `deriveDatabasePassphrase` still runs unconditionally. Wrap the Keystore key with an auth-required spec so the DB cannot open without a biometric challenge. v0.11 candidate. |
| BL17 | Wear Data Layer pairing (phone ↔ watch counts) | Wear Tile + dashboard show placeholder zeros in v0.10.0. Needs `play-services-wearable` MessageClient bridge from phone `WearBridgeService` to watch `TileService`. v0.11 candidate. |
| BL18 | WebSocket PTY-resize negotiation | Client needs to announce its xterm cols/rows to the datawatch hub so the server-side PTY resizes to match. Without this, Claude Code TUIs are unreadable on mobile (see B1). Requires a new outbound `resize` frame on `WebSocketTransport` (currently send-path only carries `subscribe`) and a parent-repo issue to confirm the hub's frame shape. v0.11 candidate. |

## Completed features

| ID | Title | Shipped in | Plan |
|----|-------|-----------|------|
| F1 | Sprint 1 — shared foundation (transport + storage + onboarding) | v0.2.0 | [2026-04-18-sprint-1-foundation.md](2026-04-18-sprint-1-foundation.md) |
| F2 | Sprint 2 — WebSocket + session detail + xterm WebView + push + multi-server picker (incl. BL9) | v0.3.0 | [2026-04-19-sprint-2-session-ux.md](2026-04-19-sprint-2-session-ux.md) |
| F3 | Sprint 3 — voice (Whisper) + MCP SSE skeleton + all-servers federation + home widget (BL6) + stats + channels tab | v0.4.0 | — |
| F4 | Sprint 4 — Wear OS dashboard + Wear Tile (BL4) + Android Auto ListTemplate (BL10) | v0.5.0 | — |
| F5 | Sprint 5 — hardening + biometric unlock (BL2) | v0.9.0 | — |
| F6 | Sprint 6 — ADR-0042 scope closed (every promoted item shipped) | v0.10.0 | — |

## Completed backlog

| ID | Title | Shipped in | Notes |
|----|-------|-----------|-------|
| BL2 | Biometric unlock | v0.9.0 (Sprint 5) | Promoted to v0.10.0 per ADR-0042. Passphrase-bound variant tracked as BL16. |
| BL4 | Wear Tile (W2) | v0.5.0 (Sprint 4) | Promoted per ADR-0042. Data Layer pipe tracked as BL17. |
| BL6 | Home-screen widget | v0.4.0 (Sprint 3) | Promoted per ADR-0042. |
| BL8 | SQLCipher Android driver swap | v0.1.x (Sprint 1 Phase 2) | `net.zetetic:sqlcipher-android` + Keystore-derived passphrase. |
| BL9 | 3-finger swipe-up server picker | v0.3.0 (Sprint 2) | Promoted per ADR-0042. |
| BL10 | Android Auto Tile (parked-state dashboard — dev flavor) | v0.5.0 (Sprint 4) | Promoted per ADR-0042. Auto API has no direct "tile" concept; ListTemplate covers the Messaging-template glance per Play policy. |
