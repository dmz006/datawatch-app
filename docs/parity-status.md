# PWA feature-parity status

This document is the honest, up-to-the-commit answer to: **"Are we close to 100 %
feature parity with the PWA?"** It tracks every PWA feature against its mobile
client status and the sprint that delivers it. Updated each release.

Upstream tracking issue: [dmz006/datawatch#4](https://github.com/dmz006/datawatch/issues/4).
Decision authority: ADR-0005 (1:1 client-side parity).

## Honest current state — v0.2.1 (Sprint 1 complete)

What you're holding in your hand right now (April 2026) is the **foundation**:
the network/storage/security/onboarding spine, plus a minimal Sessions list.
Most user-visible PWA features are still ahead of us — Sprints 2–6 deliver them.

The shipping plan: **MVP into Play Console internal testing 2026-06-12**,
**public production track 2026-07-10**. We are on track per `sprint-plan.md`.

## Per-feature parity matrix

✅ shipped · 🚧 in current sprint · ⏳ planned sprint · ⏸ post-MVP backlog

### Core session interaction (PWA tab: Sessions)

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| List active sessions | ✅ | ✅ | shipped v0.2.1 | 1 |
| Show session state pills | ✅ | ✅ | shipped v0.2.1 | 1 |
| Open session detail | ✅ | ⏳ | planned | 2 |
| Live chat / message stream | ✅ | ⏳ | planned (WebSocket /ws) | 2 |
| Reply to pending prompt | ✅ | ⏳ | planned | 2 |
| Kill session | ✅ | ⏳ | planned (REST already has it; wire UI button) | 2 |
| Override session state | ✅ | ⏳ | planned | 2 |
| Filter sessions by state | ✅ | ⏳ | planned | 2 |
| Mute / un-mute session | ✅ | ⏳ | planned | 2 |
| Bulk delete completed sessions | ✅ | ⏳ | planned | 2 |
| Pull-to-refresh | ✅ | 🚧 | refresh button shipped; gesture next | 2 |

### Terminal + scrollback

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| xterm.js terminal view | ✅ | ⏳ | planned (WebView re-use) | 2 |
| ANSI color rendering | ✅ | ⏳ | bundled with xterm | 2 |
| Scrollback buffer | ✅ | ⏳ | planned | 2 |
| Copy text | ✅ | ⏳ | planned | 2 |
| Search in scrollback | ✅ | ⏳ | planned | 2 |
| Adjustable terminal dimensions | ✅ | ⏸ | post-MVP | — |

### New session creation (PWA tab: New)

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| Start session from form | ✅ | ⏳ | planned | 2 |
| `new: <task>` quick command | ✅ | ⏳ | planned (MCP SSE) | 3 |
| Voice → new session | n/a | ⏳ | planned | 3 |

### Alerts (PWA tab: Alerts)

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| Alerts list | ✅ | ⏳ | planned | 3 |
| Notification badge counter | ✅ | ⏳ | planned | 3 |
| Mark as read / dismiss | ✅ | ⏳ | planned | 3 |
| Push wake notification | ✅ (browser push) | ⏳ | planned (FCM) | 2 |
| Action buttons in notification | partial | ⏳ | Approve/Deny/Reply/Mute (richer than PWA) | 2 |

### Settings → LLM Backend Configuration

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| Pick LLM backend (Claude/Codex/Ollama/etc.) | ✅ | ⏳ | planned | 3 |
| Pick model variant | ✅ | ⏳ | planned | 3 |
| Edit endpoint URL / API token | ✅ | ⏳ | planned (structured form, raw YAML blocked per ADR-0019) | 3 |

### Settings → Comms (Channels)

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| List configured channels | ✅ | ⏳ | placeholder shipped; full UI next | 3 |
| Add Signal / Telegram / Slack / Matrix / Twilio / ntfy | ✅ | ⏳ | planned | 3 |
| Test message round-trip | ✅ | ⏳ | planned | 3 |
| Per-channel enable / disable | ✅ | ⏳ | planned | 3 |
| Download CA certificate (Web Server card) | ✅ | ⏳ | planned (mobile-side: import cert into trust store) | 3 |

### Settings → Session Preferences

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| Input mode (tmux / channel / none) | ✅ | ⏳ | planned | 3 |
| Output mode | ✅ | ⏳ | planned | 3 |
| Console dimensions | ✅ | ⏸ | post-MVP | — |

### Settings → System Configuration

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| Browser/local notification toggle | ✅ | ⏳ | planned | 2 |
| Active-session suppression | ✅ | ⏳ | planned | 2 |
| Auto-restart on config change | ✅ | ⏳ | planned | 3 |
| Recent-session retention window | ✅ | ⏳ | planned | 3 |
| Max concurrent sessions | ✅ | ⏳ | planned | 3 |
| Scrollback line count | ✅ | ⏳ | planned | 3 |

### Settings → Servers (mobile-only addition for multi-server)

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| List configured servers | n/a (single server) | ✅ | shipped v0.2.1 | 1 |
| Add server | n/a | ✅ | shipped v0.2.1 (with No-bearer-token + self-signed-trust opt-ins) | 1 |
| Delete server | n/a | ✅ | shipped v0.2.1 | 1 |
| Edit server | n/a | ⏳ | planned (tap-to-edit stubbed today) | 2 |
| Active-server picker (multi-server switching) | n/a | ⏳ | planned | 2 |
| Per-server status indicator (green/red) | n/a | ⏳ | planned | 2 |
| 3-finger swipe-up server picker (Home Assistant style) | n/a | ⏸ | post-MVP backlog (BL9) | — |

### Stats (PWA tab: Statistics Panel)

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| Real-time CPU / memory / disk / GPU | ✅ | ⏳ | placeholder; full dashboard | 3 |
| Active session counts | ✅ | ⏳ | planned | 3 |
| Uptime | ✅ | ⏳ | planned | 3 |
| eBPF per-process network | ✅ | ⏳ | view-only per ADR-0019 | 3 |

### About

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| App version | ✅ | ✅ | shipped (with build code + git SHA per build) | 1 |
| Daemon version | ✅ | ⏳ | planned (call /api/health and display) | 2 |
| License attribution | ✅ | ✅ | shipped | 1 |
| Browser/env details | ✅ | ✅ | shipped (package id, source link, parent project link) | 1 |
| Connection status indicator | ✅ | ⏳ | planned | 2 |

### Memory + Knowledge graph

| Feature | PWA | Mobile | Status | Sprint |
|---------|-----|--------|--------|--------|
| memory_recall | ✅ | ⏳ | planned (MCP SSE) | 3 |
| memory_remember | ✅ | ⏳ | planned | 3 |
| KG query / timeline | ✅ | ⏳ | planned | 3 |
| KG graph view | ✅ | ⏳ | planned | 3 |

### Voice (mobile-first; PWA has limited voice)

| Feature | Mobile | Status | Sprint |
|---------|--------|--------|--------|
| Push-to-talk on FAB / composer / quick-tile / ASSIST intent | ⏳ | planned | 3 |
| Server-side Whisper transcription | ⏳ | planned (depends on dmz006/datawatch#2) | 3 |
| Prefix auto-send (`new:` / `reply:` / `status:`) | ⏳ | planned | 3 |

### Wear OS (mobile-only)

| Feature | Mobile | Status | Sprint |
|---------|--------|--------|--------|
| Notification reply via watch | ⏳ | planned | 4 |
| Watchface complication | ⏳ | planned | 4 |
| Rich Wear app | ⏳ | planned | 4 |
| Wear Tile (BL4) | ⏳ | **promoted to v1.0.0 per ADR-0042** | 4 |

### Android Auto (mobile-only)

| Feature | Mobile | Status | Sprint |
|---------|--------|--------|--------|
| Public messaging template (Play-compliant) | ⏳ | planned | 4 |
| Internal full passenger UI | ⏳ | planned | 4 |
| Auto Tile — parked-state dashboard (dev flavor) (BL10) | ⏳ | **promoted to v1.0.0 per ADR-0042** | 4 |

### Home screen + gestures (mobile-only)

| Feature | Mobile | Status | Sprint |
|---------|--------|--------|--------|
| Home-screen widget — session count + voice quick action (BL6) | ⏳ | **promoted to v1.0.0 per ADR-0042** | 3 |
| 3-finger-swipe-up server picker, HA-style (BL9) | ⏳ | **promoted to v1.0.0 per ADR-0042** | 2 |

### Security (mobile-only)

| Feature | Mobile | Status | Sprint |
|---------|--------|--------|--------|
| Biometric unlock (optional, opt-in) (BL2) | ⏳ | **promoted to v1.0.0 per ADR-0042** (amends ADR-0011) | 5 |

## Confidence

**100 % parity is the goal**, recorded in ADR-0005 and tracked upstream in
dmz006/datawatch#4. The sprint plan reaches functional parity at MVP
(2026-06-12). Public production target 2026-07-10 includes hardening +
Play Store review.

**What I commit to:**
- Every PWA feature in this matrix has a planned sprint.
- New PWA features added in `dmz006/datawatch` after this date trigger an
  issue in `dmz006/datawatch-app` linked to upstream #4 — they don't get
  forgotten.
- A row stays as ⏳ until the corresponding mobile commit lands; then it
  flips to ✅ with the version that shipped it.
- This file is updated in the same commit as each feature ships.

**What I don't commit to:**
- Pixel-exact UI replication of every PWA card — mobile UX adapts to Material
  3 conventions and form-factor (phone, watch, car).
- Web-only features that don't translate (e.g., `/api/docs` Swagger UI is a
  link-out, not embedded).

## Sprint timeline reminder

| Sprint | Dates | Version | Focus |
|--------|-------|---------|-------|
| 0 | 2026-04-17 → 2026-05-01 | v0.1.x | Design + scaffold ✅ |
| 1 | 2026-05-02 → 2026-05-15 | **v0.2.x ← we are here** | Foundation: transport, storage, onboarding, sessions list ✅ |
| 2 | 2026-05-16 → 2026-05-29 | v0.3.0 | Session detail, terminal, WebSocket, FCM, edit/picker, alerts skeleton |
| 3 | 2026-05-30 → 2026-06-12 | v0.4.0 | Voice, MCP SSE, all-servers, channels, stats, memory/KG → MVP |
| 4 | 2026-06-13 → 2026-06-26 | v0.5.0 | Wear OS + Android Auto |
| 5 | 2026-06-27 → 2026-07-03 | v0.9.0 | Harden, Play submission, Auto Driver Distraction review |
| 6 | 2026-07-04 → 2026-07-10 | **v1.0.0** | Open testing → Production |

This document is reviewed at the close of every sprint and re-validated
against the current PWA at every release.
