# Sprint 23 — Watch Toggle Opt-In (Issue #116)

**Date:** 2026-05-10  
**Version at planning:** v0.92.0/170  
**Target version:** v0.93.0/171  
**Issue:** #116

## Scope

Modules affected: `composeApp`, `shared` (locale strings only), locale files (×5 ×2 surfaces)

Reuse audit: SharedPreferences pattern (existing: `ThemePreference`, `PushTokenStore`); `AlertsViewModel` existing chip-filter combine pattern extended; `BottomNavBar` existing badge extended.

## Motivation

Alert counts and badge are currently global — every alert from every session contributes to
the badge count and the notification surface. Operators running many concurrent sessions get
badge noise from sessions they don't care about. The watch toggle lets operators opt individual
sessions (and automata) into alert tracking. Only sessions with the toggle ON contribute to
badge counts and alert filtering.

Default: **OFF** (unwatched). This is conservative — existing behavior (all alerts shown) is
preserved if no session is ever watched. Badge becomes meaningful only when the operator
explicitly watches sessions.

## Implementation Plan

### Phase 1 — WatchedSessionsStore + WatchedAutomataStore
Status: Planned

- `composeApp/src/main/kotlin/.../local/WatchedSessionsStore.kt`
  - `SharedPreferences` key: `watched_sessions_${profileId}` (per-profile `StringSet`)
  - `fun isWatched(sessionId: String): Boolean`
  - `fun setWatched(sessionId: String, watched: Boolean)`
  - `fun watchedIds(): Set<String>`
  - `fun watchedFlow(): Flow<Set<String>>` — re-emits on every change via `callbackFlow`
- `composeApp/src/main/kotlin/.../local/WatchedAutomataStore.kt` — same shape, key `watched_automata_${profileId}`
- Tests: `WatchedSessionsStoreTest` (in-memory SharedPreferences via `ApplicationProvider` + Robolectric)
  - `setWatched_true_addsToSet`, `setWatched_false_removesFromSet`, `watchedIds_emptyByDefault`,
    `profileIsolation_separateSetsPerProfile`, `watchedFlow_emitsOnChange`

### Phase 2 — Sessions list + detail Watch toggle
Status: Planned

- `SessionsViewModel`: inject `WatchedSessionsStore`; expose `fun toggleWatch(sessionId)` + `watchedIds: StateFlow<Set<String>>`
- `SessionCard` composable: add `IconToggleButton` (🔔/🔕 or bell icon with checked state) driven by `watchedIds`
- Session detail header row: existing session-name row gets a `WatchToggle` companion button
- Tests: `SessionsViewModelTest` — `toggleWatch_updatesStore`, `watchedIds_reflectsStore`

### Phase 3 — Automata Watch toggle
Status: Planned

- `AutonomousViewModel`: inject `WatchedAutomataStore`; expose `fun toggleWatchAutomata(prdId)` + `watchedAutomataIds: StateFlow<Set<String>>`
- `PrdRowItem`: add `IconToggleButton` for watch state
- `PrdDetailDialog`: header watch toggle
- Tests: `AutonomousViewModelTest` — `toggleWatch_updatesStore` (extend existing test class)

### Phase 4 — AlertsViewModel filtering + BottomNavBar badge
Status: Planned

- `AlertsViewModel`: receive `WatchedSessionsStore`; add `_watchedIds: StateFlow<Set<String>>`
  - Outer combine gains 5th stream: `watchedIds`
  - When `watchedIds.isEmpty()` → show ALL alerts (backward-compat fallback for operators who never watch)
  - When `watchedIds.isNotEmpty()` → filter alerts to only those whose `sessionId` is in `watchedIds`
- `BottomNavBar` badge: pass filtered count (already uses `alertCount`; update the ViewModel to expose
  `watchedAlertCount` separate from total `alertCount`; `AppRoot` passes the right one)
- Tests: `AlertsViewModelTest` — `watchedFilter_emptyWatched_showsAll`, `watchedFilter_withWatched_filtersToWatched`,
  `watchedFilter_sessionRemovedFromWatch_alertDropsFromCount`

### Phase 5 — Locale strings (5 bundles × composeApp)
Status: Planned

Keys:
- `session_watch_toggle` — "Watch"
- `session_watch_on` — "Watching"
- `session_watch_off` — "Not watching"
- `session_watch_tip` — "Watch this session to include its alerts in your badge count"
- `automata_watch_toggle` — "Watch"
- `automata_watch_on` — "Watching"
- `automata_watch_off` — "Not watching"

Files: `composeApp/src/main/res/values[-de,-es,-fr,-ja]/strings.xml`  
(Wear does not show session/automata toggles — no wear locale change needed.)

### Phase 6 — Version bump + docs
Status: Planned

- `gradle.properties`: `0.93.0` / `171`
- `Version.kt`: `0.93.0` / `171`
- `composeApp/build.gradle.kts`: `versionName = "0.93.0"`, `versionCode = 171`
- `wear/build.gradle.kts`: same
- `CHANGELOG.md`: Sprint 23 entry
- `README.md`: current-release line
- `docs/plans/README.md`: Sprint 23 shipped → closed section
- `docs/testing-tracker.md`: new rows for WatchedSessionsStore, AlertsViewModel filter, BottomNavBar badge

## Testing Tracker Rows (to add)

| Surface | Feature | Tested | Validated |
|---------|---------|--------|-----------|
| Shared | `WatchedSessionsStore` set/get/flow | Yes | No |
| Phone | `SessionsViewModel` toggleWatch | Yes | No |
| Phone | `AutonomousViewModel` toggleWatchAutomata | Yes | No |
| Phone | `AlertsViewModel` watched-filter (empty=all, nonempty=filter) | Yes | No |
| Phone | `BottomNavBar` watchedAlertCount badge | No | No |
| Phone | Watch toggle UI — sessions list + detail | No | No |
| Phone | Watch toggle UI — automata list + detail | No | No |
