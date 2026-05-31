# Android Auto — architecture + in-car test guide

*Last updated 2026-05-31 for v1.0.35+.*

## What ships in the APK

The `:auto` module is bundled into the `composeApp` APK via
`missingDimensionStrategy("surface", "publicMessaging")` in the
`publicTrack` flavor. The `CarAppService` + manifest metadata merge
automatically — no separate Auto APK.

`minCarApiLevel = 2`. Category: `MESSAGING`.

---

## Navigation architecture

All screens use `ListTemplate` or `MessageTemplate` only (ADR-0031 Play
compliance). No free-form UI.

```
AutoSummaryScreen (home)
├── AutoSessionListScreen    ← tap session counts
├── AutoAutomataScreen       ← tap automata count
├── AutoServerPickerScreen   ← tap server header
├── AutoMonitorScreen        ← ActionStrip monitor icon
└── AutoAboutScreen          ← ActionStrip info icon

AutoSessionListScreen
└── AutoSessionDetailScreen  ← tap any session row

AutoSessionDetailScreen
├── LastOutputDetailScreen   ← ActionStrip ℹ info slots (status / response / prompt)
│   └── LongOutputScreen     ← "Long Version" button (parked-only)
├── LastOutputDetailScreen   ← ActionStrip 🔊 voice/speaker icon (always shown)
├── BlockDetailsScreen       ← ActionStrip ℹ slot when session is blocked
└── VoiceRecordingScreen     ← ActionStrip 🎤 mic slot (non-terminal sessions)
    └── TranscriptionConfirmScreen ← on successful recognition

AutoMonitorScreen
├── AutoSessionListScreen    ← Sessions action (single-server mode)
└── AutoMonitorScreen        ← tappable server row → forcedProfile drill-down

AutoAutomataScreen
└── AutoSessionListScreen    ← tap automata row → filtered by automataId
```

Max stack depth: 5. `AutoSessionDetailScreen` uses **inline reply mode**
(template invalidation, not a screen push) to stay within the limit when
reached via `AutoAutomataScreen → AutoSessionListScreen → detail`.

---

## Screen reference

### AutoSummaryScreen
Home screen. Shows session counts (running / waiting / total) and automata
count for the active server. Polls `GET /api/sessions` + `GET /api/prds`
every 15 s.

**ActionStrip:** Monitor icon → `AutoMonitorScreen` · Info icon →
`AutoAboutScreen`.

Server name in the title taps to `AutoServerPickerScreen`.

---

### AutoSessionListScreen
`ListTemplate` with one row per session, sorted by urgency (blocked → waiting →
rate-limited → running → terminal). Colored dot icons per state. Capped by
`ConstraintManager.MAX_NUM_ROW` items.

Optional `automataId` parameter filters to sessions belonging to one automaton
(pushed from `AutoAutomataScreen`).

---

### AutoSessionDetailScreen
`MessageTemplate`. Body is a formatted status block (state, task, progress,
prompt context, last response, guardrail verdicts). Polls every 10 s (active
sessions), 60 s (terminal).

**ActionStrip (context-sensitive, max 3 slots):**

| Session state | Slot 1 | Slot 2 | Slot 3 |
|---|---|---|---|
| Running | ℹ status+long | ℹ last response | 🔊 TTS |
| Waiting / RateLimited | ℹ prompt/context | ℹ status | 🔊 TTS |
| Blocked | ℹ block details | ℹ status | 🔊 TTS |
| Terminal | ℹ last response | — | 🔊 TTS |

🔊 TTS slot always pushes `LastOutputDetailScreen` with `shortText =
currentStatus ?: lastResponse ?: lastPrompt` and `longText =
currentStatusLong`.

**Primary actions (inline, state-dependent):**
- Running: **Kill** (→ Kill Pending confirmation with 15 s timeout + Cancel)
- Waiting/RateLimited: **Reply** (enters inline reply mode) · **Kill**
- Blocked: **Approve Gate** · **Kill** (if active)
- Terminal: read-only

**Reply mode** (inline `MessageTemplate`, no screen push):
- Body: first 200 chars of prompt context / last prompt
- Buttons: **Yes** · **No**
- ActionStrip: **Continue** · **Skip** · 🎤 mic (→ `VoiceRecordingScreen`) · **Cancel**

---

### VoiceRecordingScreen
Uses Android's built-in `SpeechRecognizer` (Google ASR) — not `CarAudioRecord`
— so it works while driving without triggering the host's recording restriction.
On successful recognition pushes `TranscriptionConfirmScreen`. Handles mic/network
errors with Retry + Cancel.

---

### TranscriptionConfirmScreen
Shows the recognized text and auto-plays it via TTS on entry (preference
`auto_play_transcription`, default true). **Send** fires
`POST /api/sessions/reply` with `\r` suffix. **Re-record** pops back to
`VoiceRecordingScreen`.

---

### LastOutputDetailScreen
`MessageTemplate`. Body is `shortText`. ActionStrip: Cancel · TTS icon
(play/stop). **Long Version** button (parked-only) pushes `LongOutputScreen`
when `longText` is available.

---

### BlockDetailsScreen
`MessageTemplate`. Shows formatted guardrail verdicts via `GuardrailTtsBuilder`.
ActionStrip: Cancel · TTS icon. **Approve Gate** button (if active).

---

### AutoMonitorScreen
System stats (CPU, RSS, GPU, net Rx/Tx) per server. Multi-server mode shows
one compact row per server; tap → `AutoMonitorScreen(forcedProfile=…)` for
single-server detail. Sessions ActionStrip action → `AutoSessionListScreen`.

---

### AutoAutomataScreen
Automata list with state-colored dots and progress bars. Tap a row →
`AutoSessionListScreen(automataId=…)`.

---

### AutoAboutScreen
App version + server version. **Check for Update** action (fails-safe on
404/error, shows UP_TO_DATE so old server versions don't show a spurious button).

---

### AutoServerPickerScreen
`ListTemplate` of enabled server profiles. Tap → writes to `ActiveServerStore`
(shared with phone app), returns to summary.

---

## Dependency injection

`:auto` can't depend on `:composeApp` (library → app layering violation).
`AutoServiceLocator` rebuilds the same dependency graph independently:
`KeystoreManager` → `DatabaseFactory` → `ServerProfileRepository` →
`SessionRepository` → `RestTransport` — all from `:shared`. Same SQLCipher DB,
same bearer tokens. Init happens in `DatawatchMessagingService.onCreate()`
before any `Screen` is constructed.

---

## Host validation

| Build | Validator |
|---|---|
| Debug | `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` (DHU-friendly) |
| Release | Strict allowlist — `auto/src/publicMain/res/values/hosts_allowlist.xml` |

---

## In-car testing

### Requirements

- Phone with the Play Store release installed (must share distribution cert with
  Auto — sideloading a differently-signed debug build over a Play Store install
  breaks SQLCipher + Android Keystore).
- Car or DHU with Android Auto.
- Auto developer mode: open Android Auto app → tap version number ~10 times →
  enable **Unknown sources** in the unlocked developer settings.

### DHU (no car available)

```bash
sdkmanager "extras;google;auto"
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```
Connect phone over USB + ADB with Auto developer mode on. DHU mirrors the
Auto projection.

### Test flow

1. Open datawatch in the car display (Messaging category in app drawer).
2. Summary screen → confirm session counts update within 15 s.
3. Tap a waiting session → detail screen → tap **Reply** → Yes/No/Continue/Skip.
4. Tap 🎤 mic in reply strip → speak → confirm transcript → Send.
5. Tap 🔊 speaker icon → `LastOutputDetailScreen` → TTS plays summary →
   tap **Long Version** (parked-only) → hear full narrative.
6. Blocked session → ActionStrip ℹ → `BlockDetailsScreen` → Approve Gate.
7. Kill a session → **Kill** → **Confirm Kill** (red, 15 s window) or **Cancel**.

---

## Known gaps

| Item | State |
|---|---|
| In-car TTS for incoming waiting-input alerts | ❌ Notifications post to phone shade + Wear; no Auto TTS announcement via Car Message API |
| RemoteInput voice reply from Auto notification | ❌ Auto's TTS-reply notification path is separate from the in-app voice flow |
| DHU simulator runtime smoke (CI) | ❌ Builds and lints pass; never run against a live DHU in CI |
