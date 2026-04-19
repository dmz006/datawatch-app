# Approved Decisions (ADRs 0001–0041)

All decisions approved by user on 2026-04-17 during the four-batch design Q&A.
One consolidated file for pre-scaffold; will be split into `docs/decisions/NNNN-<slug>.md`
(MADR format) when the repo is scaffolded.

## Status legend

- **Accepted** — locked and load-bearing
- **Accepted (with variant mock)** — primary approved, alternative to be sketched for comparison
- **Deferred** — captured but not acted on until a later phase
- **Superseded by N** — replaced; reference retained for history

---

### Stack & platform

- **ADR-0001 — KMP + Compose Multiplatform.** Shared business logic; Wear OS + Android Auto
  native Kotlin modules; iOS app ships a skeleton pre-wired to KMP shared module.
- **ADR-0002 — Android SDK targets.** `minSdk = 29` (Android 10), `targetSdk = 35` (Android 15).
- **ADR-0003 — Repo + signing.** `github.com/dmz006/datawatch-app`, public, Polyform NC 1.0.0,
  CI = GitHub Actions, signing = Google Play App Signing.

### Messaging + reachability

- **ADR-0004 — Transport priority.** Primary direct API (REST/WS/MCP) → fallback Android Intent
  to on-device Signal/SMS/etc. apps targeting the number/ID the datawatch server already watches.
- **ADR-0009 — Reachability profiles.** Tailscale, local Wi-Fi LAN, DNS TXT covert channel,
  messaging-backend relay via Intent handoff. No Cloudflare. No public HTTPS exposure.
- **ADR-0010 — TLS / certs.** NetworkSecurityConfig per-profile trust anchors; self-signed
  supported via user-confirmed CA import; cert pinning opt-in per server.

### Scope + parity

- **ADR-0005 — Client parity scope.** 1:1 client-side parity with the datawatch server API +
  MCP; no local LLM/code execution. Flag items that don't make sense for user sign-off.
- **ADR-0008 — Pure client identity.** No local server process, no queue, no replay.
- **ADR-0019 — Flagged parity exclusions.**
  - eBPF network tracking → view only
  - GPU/CPU/memory stats → view only
  - `kill-orphans` → confirmation dialog, no biometric
  - Editing server token / crypto keys → blocked on mobile
  - Session pipelines/DAGs editor → read-only on phone, editable in PWA
  - Raw YAML config editor → disabled, structured form only

### Auth + identity

- **ADR-0011 — Auth.** Static bearer token per server profile (same as parent). Stored in
  EncryptedSharedPreferences backed by Android Keystore. No pairing flow, no biometric in v1.
  Domain abstracts a `Principal` for future multi-user.

### Push + realtime + offline

- **ADR-0012 — Push stack.** FCM primary (wake ping), ntfy fallback where configured,
  WebSocket realtime once app is open.
- **ADR-0013 — No offline queue.** Writes fail fast; user retries manually. Cached reads show
  stale timestamp; grey out after 30 s of unreachability.
- **ADR-0018 — Notification defaults.** Server-level enable; per-session mute; always-notify
  on "input needed." Wear actions: Approve / Deny / Reply / Mute 10m.
- **ADR-0027 — Offline voice refinement.** Recorded audio may persist in a retry UI — user taps
  retry; no background replay.

### MCP + real-time

- **ADR-0014 — Transport set.** REST + WebSocket `/ws` + MCP HTTP SSE, all three simultaneously.
- **ADR-0015 — Quick commands v1.** `session_list`, `session_reply`, `system_stats`. Expand
  as usage teaches us.

### Storage + backup

- **ADR-0016 — Local DB.** SQLDelight over SQLCipher-encrypted SQLite. Master key in Keystore.
- **ADR-0017 — Backup.** Android Auto Backup to Google Drive enabled. Encrypted DB + non-secret
  prefs included; Keystore material re-binds on restore (user re-enters token).

### Voice

- **ADR-0006 — Voice pipeline.** Record on device → upload to datawatch server → server
  Whisper transcribes. No on-device STT in v1.
- **ADR-0025 — Voice invocation.** Four surfaces: global FAB, chat composer mic, Android
  quick-tile, ASSIST intent for "Hey Google, talk to datawatch."
- **ADR-0026 — Transcript handling.** Default: auto-send for recognized prefixes
  (`new:`, `reply:`, `status:`); preview-before-send for free-text. User-configurable.

### UX / navigation

- **ADR-0020 — Server-first navigation.** Bottom nav = Sessions / Channels / Stats / Settings;
  server switcher as top-bar pill with tree drawer.
- **ADR-0021 — Proxy drill-down.** Primary: breadcrumb bar. Also mock breadcrumb + chips
  variant for comparison before finalizing.
- **ADR-0022 — Server tabs.** Unlimited; no pinning in v1.
- **ADR-0023 — Session detail layout.** Bottom-sheet pattern — chat is the spine;
  Terminal/Logs/Timeline/Memory slide up from sheets.
- **ADR-0024 — Terminal.** xterm.js in WebView for byte-for-byte PWA parity.

### Surfaces

- **ADR-0028 — Wear OS MVP surfaces.** Ongoing notification (w1), watchface complication (w3),
  rich Wear app with dictation reply (w4). Tile (w2) deferred.
- **ADR-0029 — Wear auth inheritance.** Token pulled from phone over Wearable Data Layer API.
  No separate pairing.
- **ADR-0031 — Android Auto dual-track.**
  - **Public build** (`com.dmzs.datawatchclient`, Play Store) — Messaging template only,
    compliant with Google Driver Distraction Guidelines.
  - **Internal build** (`com.dmzs.datawatchclient.dev`, Internal Testing only) — full
    passenger UI, voice-first, never promoted public.
  - Both installable simultaneously (distinct applicationIds, icons, FCM senders, signing keys).

### Branding

- **ADR-0030 — Identity (partially superseded by ADR-0041).** Theme ships dark (matching
  datawatch `#7c3aed` purple) + light + Material You; dark default. Icon direction in
  `branding.md`. App name portion is superseded by ADR-0041 at the bottom of this file.
- **ADR-0034 — Brand home.** dmzs.com hosts privacy, terms, support links. Source at
  dmz006/datawatch-app.

### Project delivery

- **ADR-0032 — Timeline.** MVP → internal track 2026-06-12 (8 weeks). Production → public
  production track 2026-07-10 (+4 weeks). 2-week sprints.
- **ADR-0033 — Play Console account.** Recreate under davidzendzian@gmail.com, personal
  registration ($25 one-time fee).
- **ADR-0035 — Data Safety declarations (draft).** See `data-safety-declarations.md`.
- **ADR-0036 — Sprint + release cadence.** See `sprint-plan.md`. Sprint 0 = design + scaffold;
  Sprints 1–4 = MVP; 5–6 = hardening + Play submission.

### Telemetry

- **ADR-0007 — Closed-loop telemetry.** No Crashlytics, no Sentry SaaS, no Firebase Analytics,
  no Google Analytics. Diagnostics stay local to device + user's datawatch server.

### Final pre-scaffold decisions (batch 5)

- **ADR-0037 — Icon concept B.** Phone silhouette with miniature datawatch eye on the
  screen and signal arcs above. Old-school handset reference; preserves datawatch purple
  palette.
- **ADR-0038 — Wear voice fallback chain.** Priority: (1) phone-proxy via Wearable Data
  Layer → phone → server Whisper; (2) direct watch-to-server if watch has its own
  reachability; (3) native Wear RemoteInput STT → send transcript as text command.
  Never show "Open on phone."
- **ADR-0039 — Upstream parent coordination.** The three mobile-needed parent endpoints
  are tracked as upstream issues: [#1](https://github.com/dmz006/datawatch/issues/1)
  device registration · [#2](https://github.com/dmz006/datawatch/issues/2) voice
  transcribe · [#3](https://github.com/dmz006/datawatch/issues/3) federation fan-out.
  Mobile ships MVP workarounds until upstream lands; see `api-parity.md`.
- **ADR-0040 — Play publisher name.** Display "dmz" if Play Console accepts it; fallback
  "dmzs" → "dmzs.com" → "Datawatch". Website field points at `https://dmzs.com`.

### Brand naming (adjustment)

- **ADR-0041 — App display name is `datawatch` (lowercase).** Supersedes the name
  portion of ADR-0030 ("Datawatch Client"). Rationale: align user-facing naming with the
  parent project brand — Play Store listing, launcher icon label, Wear watchface, Android
  Auto surface, iOS bundle display, and in-app word mark all read `datawatch`. The dev
  build reads `datawatch (dev)` to keep the two installs visually distinct. Technical
  identifiers (Kotlin packages, applicationId `com.dmzs.datawatchclient[.dev]`, GitHub
  repo name `dmz006/datawatch-app`, keystore file names) are unchanged — renaming those
  would be expensive busywork without user benefit. In prose docs, "datawatch mobile
  client" or "the datawatch mobile app" is used when disambiguation from the parent
  server daemon is needed.

### MVP scope (adjustment)

- **ADR-0042 — Five items promoted from post-MVP backlog to v1.0.0 scope** (2026-04-18).
  User decision: the following were previously deferred to post-MVP but are now
  required for the v1.0.0 production release. Partially supersedes ADR-0011
  (biometric deferred) and ADR-0028 (Wear Tile deferred):
    1. **Home-screen widget** — session count + voice quick-action. Lands Sprint 3.
    2. **Wear Tile (w2)** — at-a-glance session state on the watchface tile surface
       alongside the already-planned complication + rich app. Lands Sprint 4.
    3. **Android Auto Tile** — parked-state dashboard for the internal (dev)
       build; public build stays Messaging-only per ADR-0031 Play compliance.
       Lands Sprint 4.
    4. **Biometric unlock** — optional fingerprint / face gate on the token
       vault + app resume. Disabled by default, user opts in during onboarding
       or Settings. Single-user model from ADR-0011 stays. Lands Sprint 5
       (hardening).
    5. **3-finger-swipe-up server picker** — Home Assistant-style gesture for
       fast server switching, in addition to the tap-to-open tree drawer.
       Lands Sprint 2 alongside the multi-server picker work.

  Sprint timeline effect: Sprint 2–5 budgets tightened; MVP target 2026-06-12
  and production target 2026-07-10 held. If any of the five threatens those
  dates, the weakest (candidate: 3-finger gesture) slips back to post-MVP
  rather than pushing the release. User notified at sprint retro.
