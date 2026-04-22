# Plans, bugs, and backlog tracker

Single source of truth per AGENT.md "Project Tracking". IDs are permanent — never
reuse a bug (B#), backlog (BL#), or feature (F#) number.

Plans: individual dated documents live as `YYYY-MM-DD-<slug>.md` in this directory
when work warrants formal planning (3+ files or non-trivial architecture).

## Active bugs

*Live-device testing against a v4.0.7 server on 2026-04-21 + 22
surfaced 18 new items on top of the v0.19-era B1–B7 set. B1/B2/B4
are closed by v0.33.x fixes; B3/B5/B6/B7 remain. New items B8–B25
classified below and cross-linked to
[dmz006/datawatch-app#1](https://github.com/dmz006/datawatch-app/issues/1)
where relevant.*

### Closed by v0.33.x

| ID | Title | Closed in | Notes |
|----|-------|-----------|-------|
| B1 | Terminal TUI unreadable on mobile | v0.23.0 + v0.33.5 + v0.33.8 | `resize_term` WS frame (BL18 path), single-source pane_capture renderer, host.html horizontal-scroll + 11px default font, pane_capture live bus bypassing the DB. Verify: live claude-code session renders at 120 cols with horizontal swipe to pan. |
| B2 | Android Auto head unit doesn't list datawatch | v0.33.0 + v0.33.9 | v0.33.0 bundled `:auto` into the composeApp APK (`missingDimensionStrategy`); v0.33.9 added the `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission Android 14+ requires for CarAppService's `connectedDevice` foregroundServiceType. User must still toggle Android Auto → Settings → version 10× → Unknown sources, which is one-time per phone. |
| B4 | Settings → LLM / Comms say "server unreachable" | v0.33.6 | `listBackends` + `listChannels` DTOs were expecting a different shape than the shipped server; fixed to accept both old and new shapes. Serialization errors no longer bucket as `Unreachable`. |

### Open

| ID | Title | Reported | Status | Notes |
|----|-------|----------|--------|-------|
| B3 | Swipe-to-mute on a session row does not toggle mute | 2026-04-19 | Open | Gesture in `SessionsScreen` swipe handler; mute state round-tripped via `/api/sessions/mute`. Needs reproducer trace — threshold fires but toggle doesn't land. |
| B5 | Stats screen lacks PWA density + eBPF / GPU detail | 2026-04-19 | Open | `StatsScreen` shows CPU / mem / disk / GPU + session counts + uptime. PWA shows eBPF per-process, disk partitions, GPU detail. Partially addressed by v0.33.7 Monitor card reshuffle but eBPF row remains ⏳ post-1.0 per parity-plan. |
| B6 | Push notifications not confirmed working | 2026-04-19 | Open | Needs live FCM + ntfy test. No code change until a failure mode isolates. |
| B7 | CI ktlintCheck fails parsing FederationDtos.kt | 2026-04-20 | Open | Pre-existing kt-lint/Kotlin version skew. Local `:composeApp:ktlintCheck` passes; CI runner version differs. Options: bump ktlint-gradle, revert to warnings-only, restructure FederationDtos.kt. |
| B8 | Session terminal starts, refreshes, then blank | 2026-04-22 | **Fixed in v0.33.13** | 5-second watchdog in TerminalView captured `events` as a stale closure (LaunchedEffect keyed on sessionId only), fired `dwClear()` 5 s after open regardless of whether pane_captures had been streaming. Watchdog removed — the single-source pane_capture pipeline (v0.33.8) handles "no frame yet" by not writing, so the belt-and-braces clear was a v0.5.0 leftover that caused more harm than good. |
| B9 | Sessions list lacks datawatch eye background watermark | 2026-04-22 | Open | PWA shows the brand eye centered behind the session list at ~85 % page width. Needs a transparent drawable layered behind the LazyColumn. Cosmetic — defer if session-tab density work lands first. |
| B10 | Monitor — no live system-stats streaming | 2026-04-22 | Open | StatsScreenContent polls `/api/stats` every 5 s. PWA updates at ~1 s over WS. Replace poll with a WS subscription or tighten interval. |
| B11 | Monitor — no session-stats panel with eBPF wheels/graphs | 2026-04-22 | Open | Extends B5. PWA has per-session CPU / mem / net wheel + per-backend graphs. Full scope post-1.0 (ADR-0019). |
| B12 | Monitor — no active-sessions list with link to "all" | 2026-04-22 | Open | PWA embeds a compact sessions list in Monitor so admins see active work without switching tabs. Could be a small card that reuses the sessions row renderer. |
| B13 | Monitor — no "chat channels configured + status" summary | 2026-04-22 | Open | Separate from Comms → Messaging channels (which is CRUD). PWA's Monitor has a per-channel status row (connected / last message / last error). |
| B14 | Monitor — no "LLM backend configured + status" summary | 2026-04-22 | Open | Same pattern as B13: a read-only Monitor card for the active backend with its health + last-used timestamp. |
| B15 | Monitor — no list of chats not-enabled | 2026-04-22 | Open | PWA surfaces disabled-channel rows so admins can quickly re-enable. |
| B16 | Monitor — "Schedules" should be "Scheduled Events" + auto-sync, no refresh button | 2026-04-22 | Open | SchedulesCard currently has a manual refresh. PWA title is "Scheduled Events" and it syncs on WS events + 15 s poll. Rename + drop the refresh button + add WS subscription. |
| B17 | Monitor — Scheduled Events needs pagination | 2026-04-22 | Open | PWA paginates at ~10 rows per page. Mobile currently renders every schedule in a scrolling column that hijacks the outer Settings scroll. |
| B18 | Monitor — Network Interfaces card doesn't belong on Monitor | 2026-04-22 | Open | Move to a server-health surface or remove. PWA doesn't put it here. |
| B19 | Monitor — Update daemon + Restart daemon belong on About | 2026-04-22 | Open | Those actions target the daemon meta — About is where the daemon version + build info already live. |
| B20 | Monitor — Kill Orphans should appear between System Statistics and Memory Browser | 2026-04-22 | Open | Current order: Stats → Log → Interfaces → Kill Orphans → Update → Restart. Wanted order: Stats → Kill Orphans → Memory Browser → Schedules → Log. |
| B21 | Comms — "Messaging channels" card should be titled "Communication Configuration" | 2026-04-22 | Open | Matches PWA card title verbatim. Just a label rename. |
| B22 | LLM — missing entire LLM Configuration section | 2026-04-22 | Open | PWA's LLM tab opens with a top "LLM Configuration" card listing the active backend + its configured model + per-backend base_url / api_key health. Our tab jumps straight to Memory. Add the missing top card. |
| B23 | LLM — Detection Filters fields are empty on load | 2026-04-22 | Open | DetectionFiltersCard reads config.detection.*_patterns but displays blanks — either the read path doesn't pick up arrays under the autosave-flat-patch contract, or the initial GET /api/config now returns a different nesting. Needs logcat on a live profile. |
| B24 | About — MCP tools list doesn't belong on About | 2026-04-22 | Open | PWA renders MCP docs inside a dedicated route/tab, not About. Either move to a new Monitor subsection or drop the card. |
| B25 | About — missing sessions-details footer | 2026-04-22 | Open | PWA's About shows a tally of total sessions ever, current active count, server uptime. Mobile About only shows app version + daemon hostname. |

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
| v0.11.0 | Session power-user parity (rename / restart / delete, terminal search+copy, start-session form, active backend picker, CA cert, connection status) — **shipped 2026-04-20** | [plan](2026-04-20-v0.11-session-power-user.md) · [parity-plan §6 v0.11.0](../parity-plan.md#v0110--session-power-user-parity) |
| v0.12.0 | Schedules CRUD + file picker + saved command library (BL20) + read-only config viewer + session output backlog pager. Rescoped 2026-04-20 from the original "channels + schedules + file picker + …" line — half the PWA-side endpoints don't exist in parent v3.0.0 yet; deferred items listed in the plan. | [plan](2026-04-20-v0.12-schedules-files-config.md) · [parity-plan §6 v0.12.0](../parity-plan.md#v0120--channels--schedules--file-picker) |
| v0.13.0 | Channels CRUD + per-session model pickers + session timeline + daemon logs/interfaces/restart + structured config editor + Signal device-linking (BL21) + Memory / KG panel + eBPF view. Everything gated on parent-side endpoints. | [parity-plan §6 v0.13.0](../parity-plan.md#v0130--memory--kg--daemon-config) |
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
| BL19 | Local-LLM orchestration — in-app PRD/HLD authoring + Ollama backend + task fire-off | User vision (2026-04-20): author a PRD or high-level design inside the mobile app, fire it off as a new datawatch session, and let a local Ollama backend (via the server's `/api/backends` indirection, or potentially direct if the user's Ollama is network-reachable) drive task orchestration. Lands explicitly **after 1.0 (full PWA parity)** — this is a mobile-first feature that the PWA does not yet have, so it needs its own ADR capturing the orchestration model (session-as-PRD, backend-selection UX, how PRD artefacts persist) before scoping into a sprint. |
| BL21 | Signal device-linking flow (`/api/link/*` + QR SSE) | PWA exposes `POST /api/link/start` + `GET /api/link/stream` (SSE) + `GET /api/link/status` for Signal device linking. Target v0.13 — needs its own design pass (QR rendering from SSE frames, paired-state persistence). |

## Completed features

| ID | Title | Shipped in | Plan |
|----|-------|-----------|------|
| F1 | Sprint 1 — shared foundation (transport + storage + onboarding) | v0.2.0 | [2026-04-18-sprint-1-foundation.md](2026-04-18-sprint-1-foundation.md) |
| F2 | Sprint 2 — WebSocket + session detail + xterm WebView + push + multi-server picker (incl. BL9) | v0.3.0 | [2026-04-19-sprint-2-session-ux.md](2026-04-19-sprint-2-session-ux.md) |
| F3 | Sprint 3 — voice (Whisper) + MCP SSE skeleton + all-servers federation + home widget (BL6) + stats + channels tab | v0.4.0 | — |
| F4 | Sprint 4 — Wear OS dashboard + Wear Tile (BL4) + Android Auto ListTemplate (BL10) | v0.5.0 | — |
| F5 | Sprint 5 — hardening + biometric unlock (BL2) | v0.9.0 | — |
| F6 | Sprint 6 — ADR-0042 scope closed (every promoted item shipped) | v0.10.0 | — |
| F7 | Sprint 7 — session power-user parity (8 flows) | v0.11.0 | [2026-04-20-v0.11-session-power-user.md](2026-04-20-v0.11-session-power-user.md) |

(F8 reserved for v0.12 schedules/files/config once it ships — see plan
[2026-04-20-v0.12-schedules-files-config.md](2026-04-20-v0.12-schedules-files-config.md).)

## Completed backlog

| ID | Title | Shipped in | Notes |
|----|-------|-----------|-------|
| BL2 | Biometric unlock | v0.9.0 (Sprint 5) | Promoted to v0.10.0 per ADR-0042. Passphrase-bound variant tracked as BL16. |
| BL4 | Wear Tile (W2) | v0.5.0 (Sprint 4) | Promoted per ADR-0042. Data Layer pipe tracked as BL17. |
| BL6 | Home-screen widget | v0.4.0 (Sprint 3) | Promoted per ADR-0042. |
| BL8 | SQLCipher Android driver swap | v0.1.x (Sprint 1 Phase 2) | `net.zetetic:sqlcipher-android` + Keystore-derived passphrase. |
| BL9 | 3-finger swipe-up server picker | v0.3.0 (Sprint 2) | Promoted per ADR-0042. |
| BL10 | Android Auto Tile (parked-state dashboard — dev flavor) | v0.5.0 (Sprint 4) | Promoted per ADR-0042. Auto API has no direct "tile" concept; ListTemplate covers the Messaging-template glance per Play policy. |
| BL20 | Saved command library (`/api/commands` CRUD) | v0.12.0 (Phase 4) | Settings → Saved commands card + New Session "From library" dropdown. |
