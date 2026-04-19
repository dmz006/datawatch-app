# Usage guide

A screen-by-screen walkthrough of the datawatch mobile app at v1.0.0.

## Launch flow

1. **Splash** — animated Matrix/Earthrise scene; stays on screen for the
   minimum brand dwell (~3.2 s) and until the encrypted DB has unlocked.
2. **Biometric prompt** *(only if enabled in Settings)* — fingerprint or
   face. Cancel = app closes.
3. **Onboarding** — shown only on first install with zero servers. Tap
   **Get started** to add your first server.
4. **Home shell** — bottom nav with Sessions / Alerts / Channels / Stats /
   Settings tabs.

## Sessions tab

The default tab. Lists every session for the currently-active profile.

### Top bar

- **Server name** (tap-down-arrow) — opens a dropdown listing every
  configured profile. Tap a row to switch; tap "Edit" next to a row to
  modify it; the "All servers" row aggregates across every enabled
  profile via `/api/federation/sessions`.
- **Refresh icon** — pulls `/api/sessions` now (or parallel fan-out in
  All-servers mode).

### Filter chips

All / Running / Waiting / Completed / Error. The "Completed" chip also
includes Killed so you don't have to hunt through two buckets.

### Session rows

- **Tap** — open the session detail screen.
- **Horizontal swipe** (either direction, ≥64 dp) — toggle mute/unmute
  for that row. The notification bell on the right reflects the state.

### 3-finger swipe up

On any screen inside the app, swipe **three fingers upward** to pop the
server picker as a bottom sheet. Useful when the top bar isn't in reach.

## Session detail

Opens via tapping a session row, a notification, or a deep link
(`dwclient://session/<id>`).

### Top bar

- Task summary / session id as title, state pill subtitle.
- **Terminal icon** — opens the xterm.js bottom sheet (ANSI colour, 5000-
  line scrollback). Output events stream in live.
- **Bell icon** — mute / unmute.
- **Overflow menu** — kill session (with confirm dialog), override state
  (picker for Running / Waiting / Completed / Error / etc.).

### Event stream

Chat-style spine from newest at bottom. Types:

| Type | Renders as |
|------|------------|
| `Output` | Body in mono font, stderr tinted red, system dimmed |
| `StateChange` | `[state] from → to` italic line |
| `PromptDetected` | Highlighted banner with the prompt text |
| `RateLimited` | Amber inline note with retry-after ts if provided |
| `Completed` | `✓ completed (exit N)` dimmed line |
| `Error` | Red line with message |
| `Unknown` | `(type)` placeholder — forward-compat for future server events |

### Reply composer

- **Text field** — expands to 4 lines; disabled while a send is in flight.
- **Mic button** — press, speak, press again (becomes a Stop icon while
  recording). AAC / 16 kHz mono → multipart POST to `/api/voice/transcribe`
  with `auto_exec=false`, so the transcript drops into the text field for
  you to edit + send.
- **Send button** — POST `/api/sessions/reply`. Disables until there's
  non-whitespace text.

Per [ADR-0013](decisions/README.md), failed sends show an inline banner;
nothing is queued.

## Alerts tab

Surfaces sessions where `needsInput && !muted` for the active profile.
A badge on the bottom-nav Alerts icon shows the count. Tap a row to jump
straight into that session's detail (the reply composer is focused if
the server still has the prompt open).

## Channels tab

- **LLM backends** card — live from `GET /api/backends`. Active backend
  gets a "active" chip.
- **Messaging channels** card — read-only note pointing at server-side
  config (`~/.datawatch/config.yaml`). Per-channel enable/disable from the
  phone arrives once the parent exposes that REST surface.

## Stats tab

Live polling of `GET /api/stats` every 5 seconds for the active profile.

- CPU / Memory / Disk / GPU progress bars (disk and GPU only render when
  the server provides them). Bar colour flips amber >70 %, red >90 %.
- Session counts (total / running / waiting).
- Daemon uptime, formatted as `Nd Nh Nm`.

## Settings tab

- **Servers card** — list configured profiles with status badges
  (no-auth, trust-all-TLS). Add (+), edit (tap row), delete (trash icon).
- **Security card** — biometric unlock toggle.
- **Comms card** — placeholder for full channel config (v1.1+).
- **About card** — live animated logo, app version + build SHA, license,
  package id, source + parent project links.

## Notifications

The app registers five channels in Android system settings. You can toggle
any subset from Settings → Apps → datawatch → Notifications:

| Channel | Importance | When it fires |
|---------|------------|---------------|
| Input needed | High | Session pauses waiting for your reply |
| Completed | Default | Session finished (exit code either way) |
| Rate limited | Default | Upstream LLM rate-limited; session will resume |
| Errors | High | Session hit an error |
| Background services | Low | ntfy fallback foreground service marker |

### Inline reply

The **Input needed** notification has a **Reply** action backed by Android
`RemoteInput`. Type or dictate, tap Send — the phone POSTs
`/api/sessions/reply` via the app's `ReplyBroadcastReceiver` without
bringing the app to the foreground.

### Deep links

Tapping any notification opens the app with
`dwclient://session/<session-id>`, which the app routes straight to the
session detail screen.

## Widget

Long-press your home screen → Widgets → datawatch → drag the **Sessions**
widget. Shows running / waiting / total for the active profile. Tap opens
the app.

## Wear OS

- **Dashboard** — three-count view in the watch app.
- **Tile** — glance surface on the Tiles carousel (swipe right from the
  watch face). Identical counts.

## Android Auto

Under the **Communication** category on your head unit. Shows a
three-row list (running / waiting / total) — driver-safe by policy.
Deep interaction stays on the phone.

## Gestures summary

| Gesture | Result |
|---------|--------|
| Tap session row | Open detail |
| Horizontal swipe on session row | Toggle mute |
| 3-finger swipe up (anywhere) | Open server picker bottom sheet |
| Swipe right from watch face | Reveal Tile |

## Keyboard shortcut / voice

No on-device voice assistant wiring yet in v1.0.0 — voice is limited to
the mic button in the session detail composer. Quick-tile and ASSIST
intent wiring arrive in v1.1 when the Wear Data Layer pair lands.
