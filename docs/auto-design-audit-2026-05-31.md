# Android Auto Design Audit — 2026-05-31

**Scope:** Full review of every Auto screen class against `docs/android-auto.md`,
`docs/plans/2026-05-31-auto-ux-overhaul.md`, ADR-0031, `docs/ux-voice.md`, and
`docs/ux-navigation.md`.

**Outcome:** 4 dead-code files deleted; docs rewritten; no functional regressions.
All core flows verified correct. Changes shipped in v1.0.36 (build 345).

---

## Issues Found and Fixed

### 1. Dead code — WaitingSessionsScreen + WaitingPrdsScreen (deleted)

**What they were:** The original v0.32.0 three-screen design had
`AutoSummaryScreen → WaitingSessionsScreen → SessionReplyScreen`. In the v1.0.28
UX overhaul, `WaitingSessionsScreen` was superseded by `AutoSessionListScreen`
(with full session list and state filtering) and `WaitingPrdsScreen` by
`AutoAutomataScreen`. Both files were never called from anywhere in the
navigation — confirmed by codebase-wide grep.

**Fix:** Deleted both files. Removed the corresponding existence tests from
`NavigationGraphTest`.

---

### 2. Dead code — SessionReplyScreen (deleted)

**What it was:** Also from the original three-screen design. Replaced by **inline
reply mode** inside `AutoSessionDetailScreen` — when the detail screen is at stack
depth 5 (via `AutoAutomataScreen → AutoSessionListScreen → detail`), pushing a new
screen would exceed the Car App Library 5-screen limit. Inline mode invalidates the
current template instead of pushing. `SessionReplyScreen` was never called from
anywhere.

**Fix:** Deleted the file. Removed its existence test. Cleaned up the stale comment
in `AutoSessionDetailScreen` that explained why it wasn't used.

---

### 3. Dead code — PreMvpPlaceholderScreen (deleted)

**What it was:** A stub shown before `AutoServiceLocator` / transport was wired up.
Obsolete since v0.50+. Never referenced in the navigation, only in a
`NavigationGraphTest` existence check with a comment saying "still exists (not
deleted) but is no longer the entry point."

**Fix:** Deleted the file. Removed the reference from `NavigationGraphTest`.

---

### 4. android-auto.md was v0.32.0 (fully rewritten)

**What it said:** Described the old three-screen navigation
(`AutoSummaryScreen → WaitingSessionsScreen → SessionReplyScreen`) with a test
flow referencing those screens. Last updated April 2026.

**Fix:** Full rewrite. Now documents the complete current navigation tree, all
14 active screens and their relationships, the context-sensitive ActionStrip
layouts per session state, the voice chain
(`VoiceRecordingScreen → TranscriptionConfirmScreen`), the TTS chain
(`speaker icon → LastOutputDetailScreen → LongOutputScreen`), the dependency
injection model, host validation, and an accurate test flow for in-car testing.

---

### 5. VoiceRecordingScreen uses SpeechRecognizer, not CarAudioRecord (intentional — noted)

**Background:** The overhaul plan (P9, v1.0.28) specified `CarAudioRecord` + Whisper
for voice capture. In v1.0.34, this was replaced with Android's built-in
`SpeechRecognizer` (Google ASR) because `CarAudioRecord` is blocked by the Android
Auto host while the vehicle is in motion, making voice input non-functional while
driving.

**Resolution:** The code change was intentional and correct. The overhaul plan is a
historical snapshot and does not need updating. The v1.0.34 CHANGELOG entry
documents the rationale. The `docs/android-auto.md` rewrite calls out
`SpeechRecognizer` explicitly.

---

## Verified Correct (No Changes Required)

### Navigation flows
- ✅ `AutoSummaryScreen` → `AutoSessionListScreen` / `AutoAutomataScreen` /
  `AutoServerPickerScreen` / `AutoMonitorScreen` / `AutoAboutScreen`
- ✅ `AutoSessionListScreen` → `AutoSessionDetailScreen`
- ✅ `AutoSessionDetailScreen` ActionStrip ℹ info slots → `LastOutputDetailScreen`
- ✅ `AutoSessionDetailScreen` ActionStrip 🔊 speaker → `LastOutputDetailScreen`
  (shows summary + Long Version button) — **fixed in v1.0.35**
- ✅ `AutoSessionDetailScreen` ActionStrip 🎤 mic → `VoiceRecordingScreen`
- ✅ `AutoSessionDetailScreen` inline reply mode (avoids stack depth 6)
- ✅ `VoiceRecordingScreen` → `TranscriptionConfirmScreen` on recognition
- ✅ `LastOutputDetailScreen` → `LongOutputScreen` (parked-only)
- ✅ `AutoMonitorScreen` forcedProfile drill-down
- ✅ `AutoAutomataScreen` → `AutoSessionListScreen(automataId=…)`

### ActionStrip layouts
- ✅ Detail screen: max 3 slots (up to 2 ℹ info + 1 🔊 TTS), state-dependent
- ✅ Reply strip: Continue / Skip / 🎤 mic / Cancel (4 slots, always `MessageTemplate`)
- ✅ `LastOutputDetailScreen`: Cancel + TTS icon
- ✅ `TranscriptionConfirmScreen`: Cancel + TTS icon
- ✅ `BlockDetailsScreen`: Cancel + TTS icon

### Template compliance (ADR-0031)
- ✅ All screens: `ListTemplate` or `MessageTemplate` only
- ✅ No `ForegroundCarColorSpan` (blocked for MESSAGING category)
- ✅ `minCarApiLevel = 2`

### Session state handling
- ✅ Running: status + long version info; last response info; TTS; Kill button
- ✅ Waiting / RateLimited: prompt/context info; status info; TTS; Reply + Kill
- ✅ Blocked: block details info; status info; TTS; Approve Gate + Kill
- ✅ Terminal: last response info; TTS; read-only body; ambient poll (60 s)

### Voice and TTS flows
- ✅ `VoiceRecordingScreen` — `SpeechRecognizer`, error states with Retry/Cancel
- ✅ `TranscriptionConfirmScreen` — auto-plays TTS on entry, Send/Re-record
- ✅ `LastOutputDetailScreen` — shows short text, plays on demand, Long Version button
- ✅ `LongOutputScreen` — full narrative TTS
- ✅ `GuardrailTtsBuilder` — formats guardrail verdicts for TTS

### Other
- ✅ Kill Pending: Confirm Kill (red) + Cancel + 15 s timeout
- ✅ Multi-server: `AutoServerPickerScreen`, `AutoMonitorScreen` forcedProfile
- ✅ Hash-based change detection (§15 pattern) on all polling screens
- ✅ `ConstraintManager.MAX_NUM_ROW` cap on list screens
- ✅ `AutoServiceLocator` correctly isolated from `:composeApp`
- ✅ Manifest: MESSAGING category, `minCarApiLevel = 2`, `foregroundServiceType = connectedDevice`

---

## Files Changed (v1.0.36, build 345)

| File | Action |
|---|---|
| `auto/…/WaitingSessionsScreen.kt` | Deleted |
| `auto/…/WaitingPrdsScreen.kt` | Deleted |
| `auto/…/SessionReplyScreen.kt` | Deleted |
| `auto/…/PreMvpPlaceholderScreen.kt` | Deleted |
| `auto/…/AutoSessionDetailScreen.kt` | Cleaned up stale comment |
| `auto/src/test/…/NavigationGraphTest.kt` | Removed 4 dead existence tests |
| `docs/android-auto.md` | Full rewrite |
| `gradle.properties` | 1.0.35 → 1.0.36, build 344 → 345 |
| `shared/…/Version.kt` | Same bump |
| `CHANGELOG.md` | v1.0.36 entry |
| `README.md` | Current release updated |
