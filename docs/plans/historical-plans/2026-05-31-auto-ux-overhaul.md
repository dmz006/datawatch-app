# Android Auto UX Overhaul — 2026-05-31

**Status:** All phases complete (shipped in v1.0.28 build 337)

## Summary

Full Android Auto UX overhaul adding context-sensitive ActionStrips, new detail screens,
voice recording flow, and tappable monitor rows. Targets driver distraction compliance
while surfacing richer session detail via push navigation.

## Phases

| Phase | Description | Status |
|-------|-------------|--------|
| P1 | AutoSummaryScreen: title, automata counts, last output row | Done (previous session) |
| P2 | AutoSessionListScreen: ConstraintManager cap for MAX_ROWS | Done (previous session) |
| P3 | AutoSessionDetailScreen: ActionStrip + Kill Pending + Voice row | Done |
| P4 | LastOutputDetailScreen + LongOutputScreen | Done |
| P5 | BlockDetailsScreen | Done |
| P6 | AutoAutomataScreen: colored dot icons + progress bar | Done |
| P7 | AutoMonitorScreen: forcedProfile + tappable rows | Done |
| P8 | AutoAboutScreen: check-for-update flow | Done |
| P9 | VoiceRecordingScreen (CarAudioRecord → Whisper) | Done |
| P10 | TranscriptionConfirmScreen | Done |
| P11 | Version bump 1.0.27→1.0.28 (build 336→337), CHANGELOG, docs | Done |

## Key Design Decisions

- **ActionStrip uses `ic_auto_info` as universal fallback icon** — specific state icons (status ◈, response ◀, block ⚠) don't exist as drawables so we reuse the existing info icon for all ActionStrip actions. The title/context makes the destination clear.
- **Kill Pending shows Cancel button** — replaces the old implicit timeout-only cancel with an explicit Cancel action alongside Confirm Kill (red).
- **VoiceRecordingScreen uses CarAudioRecord 1.7.0 API** — AUDIO_CONTENT_MIME for Whisper upload, reads raw PCM in background IO coroutine.
- **TranscriptionConfirmScreen auto-plays TTS on enter** via SharedPreferences `auto_play_transcription` (default true).
- **AutoMonitorScreen forcedProfile** enables drill-down from multi-server list; Session row in single-server mode is tappable.
- **AutoAboutScreen update check** fails-safe to UP_TO_DATE on 404 / error, so older servers don't show a spurious Update button.
- **BlockDetailsScreen** receives typed `List<GuardrailVerdictDto>` — not `List<Any>`.

## Files Created

- `auto/src/main/kotlin/com/dmzs/datawatchclient/auto/LastOutputDetailScreen.kt`
- `auto/src/main/kotlin/com/dmzs/datawatchclient/auto/BlockDetailsScreen.kt`
- `auto/src/main/kotlin/com/dmzs/datawatchclient/auto/VoiceRecordingScreen.kt`
- `auto/src/main/kotlin/com/dmzs/datawatchclient/auto/TranscriptionConfirmScreen.kt`

## Files Modified

- `AutoSessionDetailScreen.kt` — ActionStrip, Kill Pending Cancel, Voice row, promptContext/lastPrompt
- `AutoAutomataScreen.kt` — colored dot icons, progress bar
- `AutoMonitorScreen.kt` — forcedProfile, tappable rows
- `AutoAboutScreen.kt` — check-for-update flow
- `gradle.properties` — version bump
- `Version.kt` — version bump
- `CHANGELOG.md` — 1.0.28 section
- `docs/testing-tracker.md` — new Auto screen rows
