# Bug Backlog

Issues found during live E2E testing (2026-05-20). Ordered roughly by severity.

---

## Session Detail

### BL-SD-1: Session popup missing ✅ ALREADY IMPLEMENTED
- PWA has a popup/context menu on session rows; mobile has none.
- Status: Mobile already has both (a) ⋮ dropdown (rename/watch/restart/delete) and (b) `QuickCommandsSheet` (approve/reject/continue/skip/ESC/Ctrl-b/arrows/PgUp/PgDn/Tab/saved commands/custom). ▶ Commands button appears on `waiting_input` rows, matching PWA parity (`showCardCmds` also only renders for `isWaiting`).

### BL-SD-2: Timeline tab incorrect ✅ FIXED v0.132.0
- Timeline tab icons, font, and layout don't match PWA. Needs audit vs PWA.
- Fix: Timeline button icon changed from ⏱ to 🕐 (&#128336; matching PWA). Quick-key strip: added ␛ ESC and ⏎ Enter buttons, reordered arrows to match PWA order (↑ ↓ ← →).
- File: `ui/sessions/SessionDetailScreen.kt`

### BL-SD-3: Font dropdown incorrect ✅ FIXED v0.131.0
- Font size control in session detail doesn't match PWA behavior/location.
- Fix: Replaced A−/A+/Fit buttons with single "Aa▾" button that opens a `DropdownMenu` with A−, `{N}px` label, A+, and Fit items — matches PWA `app.js` font-size dropdown.
- File: `ui/sessions/TerminalToolbar.kt`

### BL-SD-4: Scroll back icon incorrect ✅ FIXED v0.131.0
- Scroll-back button used 📜 emoji; PWA uses ⤒ (U+2912) at 18sp bold.
- Fix: Changed label from `"📜"` to `"⤒"` with `scrollIcon = true` → 18sp bold styling.
- File: `ui/sessions/TerminalToolbar.kt`

### BL-SD-5: No microphone button in session detail ✅ FIXED v0.129.0 (via BL-MIC-1)
- PWA has a mic button directly in the session input area; mobile is missing it.
- Fix: Mic button now shows when `SpeechRecognizer.isRecognitionAvailable()` || `whisperConfigured`. Device speech recognition (Google ASR) is the primary path.
- File: `ui/sessions/SessionDetailScreen.kt`

### BL-SD-6: Tmux layout doesn't match PWA ✅ FIXED v0.132.0
- Location and layout of tmux pane controls don't match PWA. Needs full layout audit.
- Fix: Tab row (Tmux | Channel | Status + Aa▾ + ⤒ on right) already matched PWA. Quick-key strip had missing ESC/Enter buttons — added ␛ and ⏎, reordered arrows to PWA order (↑ ↓ ← →).
- File: `ui/sessions/SessionDetailScreen.kt`

### BL-SD-7: Generating indicator removed from PWA but present on mobile ✅ FIXED v0.128.0
- The "generating" indicator/animation was removed from the PWA. Mobile still shows it.
- Fix: Removed `GeneratingIndicator` composable and `isRunning` parameter from `SessionInputBar`.
- File: `ui/sessions/SessionDetailScreen.kt`

---

## Session List

### BL-SL-1: Filter badges don't fit one row
- Filter dropdown doesn't display all state badges in a single row.

### BL-SL-2: No "select all" in filter dropdown
- PWA has a select-all option for filter badges; mobile is missing it.

### BL-SL-3: Filter layout doesn't match PWA
- Overall filter/sort toolbar layout doesn't match PWA design.

---

## Automata

### BL-AT-1: Interview button in Automata does nothing ✅ FIXED v0.128.0
- "Interview" / Council persona wizard button (🤖 icon in Automata TopAppBar) was non-functional.
- Fix: Added `IdentityWizardSheet` rendering + identity load/save to `AutonomousScreen.kt`.
- File: `ui/autonomous/AutonomousScreen.kt`

---

## Mic / Voice

### BL-MIC-1: Mic should use device speech recognition (Google) ✅ FIXED v0.129.0
- Mic button should behave like PWA which uses browser WebSpeechAPI (Google ASR).
- Fix: Mic button now uses `SpeechRecognizer` (Android/Google ASR) when available; falls back to Whisper POST only when SpeechRecognizer is unavailable.
- Button visible even when Whisper is not configured (as long as device has speech recognition).
- File: `ui/sessions/SessionDetailScreen.kt`

---

## Terminal (xterm)

### BL-TX-2: Keyboard covers terminal cursor in tmux and xterm ✅ FIXED v0.127.0
- When soft keyboard opens in session detail, terminal didn't shrink — cursor area was hidden behind keyboard.
- Fix (v0.126.0): `imePadding()` moved to the outer Column; `contentWindowInsets = WindowInsets(0)` on Scaffold to prevent double-counting.
- Fix (v0.127.0): `safeFit()` in host.html now uses `window.visualViewport.height` to compute keyboard height and adjusts `container.style.bottom` so xterm.js `FitAddon` sees only the visible rows.
- Files: `ui/sessions/SessionDetailScreen.kt`, `assets/xterm/host.html`

### BL-TX-1: Typing in xterm doesn't send on Enter ✅ FIXED v0.124.0
- `onInput` bridge in `TerminalView.kt` was a no-op stub.
- Fix: call `WsOutbound.sendInput(sessionId, data)` in the bridge.
- File: `ui/sessions/TerminalView.kt`

---

## Dashboard

### BL-ST-1: Settings > Automata full page redo to match PWA ✅ FIXED v0.130.0
- Reordered to match PWA v8.6.0: ScanConfigCard + GuardrailLibraryCard moved to positions 8-9 (before Autonomous Config); SkillRegistries at 10; AutomataTypes + config panels at end.
- Identity icon changed to RecordVoiceOver (v0.126.0). Interview wizard wired in main Automata tab (v0.128.0).
- Datawatch PRD filed: 4df28305 — PWA to add Interview wizard to Settings > Automata.

### BL-ST-2: Settings > About — PWA parity for ops cards
- Datawatch PRD filed: d35561b2 — PWA to add ApiLinks, McpChannel, UpdateDaemon, SubsystemReload, RestartDaemon, KillOrphans, EncryptionStatus to About tab.

### BL-DB-1: No way to edit dashboard layout ✅ FIXED v0.124.0
- Dashboard had no edit button. Editing was buried in Settings > General (wrong).
- Fix: Added edit (pencil) icon to Dashboard TopAppBar → opens `DashboardCardsCard` in ModalBottomSheet.
- Removed `DashboardCardsCard` from Settings > General tab.
- Files: `ui/dashboard/DashboardScreen.kt`, `ui/dashboard/DashboardViewModel.kt`, `ui/settings/SettingsScreen.kt`

---

## Docs Viewer

### BL-DV-1: Docs viewer sheet blank (never loads) ✅ FIXED v0.124.0
- `DocsViewerSheet` used plain `WebViewClient` which silently fails SSL cert errors for self-signed certs.
- Fix: override `onReceivedSslError` to call `handler.proceed()`.
- File: `ui/common/DocsViewerSheet.kt`

---

## Sizing / Density

### BL-SZ-1: Font sizes and input boxes too large ✅ FIXED v0.124.0
- New Session screen, New Automata dialog, and configuration tabs had oversized text/spacing.
- Fix: `labelLarge` → `labelMedium` for section headers; outer padding 16→12dp; section spacing 16→12dp; task textarea min height 120→100dp.
- Files: `ui/sessions/NewSessionScreen.kt`, `ui/autonomous/NewPrdDialog.kt`
