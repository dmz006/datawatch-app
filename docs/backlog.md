# Bug Backlog

Issues found during live E2E testing (2026-05-20). Ordered roughly by severity.

---

## Session Detail

### BL-SD-1: Session popup missing
- PWA has a popup/context menu on session rows; mobile has none.

### BL-SD-2: Timeline tab incorrect
- Timeline tab icons, font, and layout don't match PWA. Needs audit vs PWA.

### BL-SD-3: Font dropdown incorrect
- Font size control in session detail doesn't match PWA behavior/location.

### BL-SD-4: Scroll back icon incorrect
- Scroll-back button uses wrong icon and/or is in the wrong location vs PWA.

### BL-SD-5: No microphone button in session detail
- PWA has a mic button directly in the session input area; mobile is missing it.

### BL-SD-6: Tmux layout doesn't match PWA
- Location and layout of tmux pane controls don't match PWA. Needs full layout audit.

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

### BL-MIC-1: Mic should use device speech recognition (Google)
- Mic button should behave like PWA which uses browser WebSpeechAPI (Google ASR).
- User wants Google/Gemini as speech recognition option, not just Whisper WAV.
- Current: records WAV → POST /api/voice/transcribe (Whisper only).
- Desired: offer Android native SpeechRecognizer (Google ASR) in addition to/instead of Whisper.

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

### BL-ST-1: Settings > Automata full page redo to match PWA
- Entire Settings > Automata page needs audit and reorder vs PWA. Icon on Identity card changed to RecordVoiceOver.
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
