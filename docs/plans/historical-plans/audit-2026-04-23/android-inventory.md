# Android Inventory — v0.34.6 Release (2026-04-23)

**Branch:** main  
**Latest commit:** v0.34.6 release  
**Scope:** Phone composeApp surface only (Wear OS and Android Auto excluded)

This document inventories the current state of every Android screen, capturing fields, buttons, dialogs, data sources, and known gaps vs. PWA. Goal is to produce the "Android today" column of a PWA-parity master inventory.

---

## Navigation Structure

### Bottom-Nav Tabs (3 items)
Located in `BottomNavBar.kt`. Appears at the bottom of the Home destination.

| Label | Route | Destination |
|-------|-------|-------------|
| Sessions | `home/sessions` | `SessionsScreen` |
| Alerts | `home/alerts` | `AlertsScreen` |
| Settings | `home/settings` | `SettingsScreen` |

**Notes:**
- Mobile uses 3 tabs + FAB on Sessions for "New" (vs PWA's 4-tab layout with a dedicated New tab).
- Stats tab was moved into Settings/Monitor.
- Channels tab was removed; backend picker now lives under Settings/LLM.

### Root Navigation Destinations

| Route | Composable | Entry Point |
|-------|-----------|------------|
| `splash` | `MatrixSplashScreen` | Cold launch; 3.2s dwell then auto-advances |
| `splash/replay` | `MatrixSplashScreen` | Replay action (v0.33.7) |
| `onboarding` | `OnboardingScreen` | First-time setup (no profiles exist) |
| `servers/add` | `AddServerScreen` | New server form |
| `servers/edit/{profileId}` | `EditServerScreen` | Edit existing server |
| `home` | `HomeShell` → `NavHost` with 3 tabs | Main authenticated surface |
| `sessions/new` | `NewSessionScreen` | Start-session form |
| `sessions/{sessionId}` | `SessionDetailScreen` | Session detail + terminal/chat |

---

## Screen Inventory

### 1. SessionsScreen
**Route:** `home/sessions`  
**File:** `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/sessions/SessionsScreen.kt`

#### How it flows
Sessions tab loads the cached session list from the active server's transport client and renders them with filter/sort controls. Users can:
- Search/filter by name/task/id/backend
- Switch servers or view all-servers fan-out
- Toggle session mute via horizontal swipe (right threshold)
- Quick-action buttons (Stop/Restart/Delete) per row
- Enter bulk-select mode for multi-delete
- Enter reorder mode to shuffle custom ordering
- Access the details screen on tap

#### Top App Bar
- **Title**: Server picker dropdown (shows "All servers" or active profile name) + reachability dot
  - Dot colors: green (online), red (unreachable), amber (probing)
  - Tap dot → bottom sheet with last-probe timestamp + Retry button
- **Actions**:
  - Refresh spinner (inline, no separate button; auto-polls every 5s)
  - Reorder mode toggle (icon tint changes when active)
- **Bulk-select mode** (when any sessions selected):
  - Replaces top bar with "N selected" title
  - Delete button (error-colored, enabled only if delete is supported)

#### Toolbar (below top bar)
- **Search field**: Free-text filter (search by name, task, id, backend); X clears
- **Backend chips** (if >1 backend): Tap to filter by backend; tap again to clear
- **History toggle**: "Show history (N)" / "Hide history (N)" button
- **Sort dropdown**: Recent Activity / Started / Name / Custom (with checkmark for current)

#### Watermark
- Eye logo (from launcher foreground drawable) centered at 85% width, 10% alpha behind list

#### Session Rows
Each row is a card with PWA-style left border (state color).

**Row content:**
- **Header**: Session ID | State pill | Backend badge (if present) | Mute icon | [Reorder arrows | More menu]
- **Body**: Task name (80-char truncated, prefers user name over task)
- **Meta**: Hostname · Time ago (e.g., "us-west-2c · 3m ago")
- **Context**: If waiting_input, 4-line quote block with latest prompt (100 chars/line)
- **Quick-actions** (inline):
  - Running/Waiting: Stop button + Commands button (if waiting_input)
  - Terminal (Completed/Killed/Error): Restart + Delete (if delete supported)
- **Icons**:
  - Paper icon (if lastResponse present) → Last Response Sheet
  - Mute/notification icon (right side of header)
- **Long-press** or row click: Toggle selection in bulk-select mode (or navigate to detail if not selecting)
- **Swipe right** (64dp threshold): Toggle mute
- **Overflow menu** (more icon):
  - Rename → RenameSessionDialog
  - Restart → Confirm dialog
  - Delete → Confirm dialog (greyed-out if delete not supported or session running)

#### Dialogs/Sheets

**RenameSessionDialog**
- Single-line text field (pre-filled with current name or task summary)
- Save button (disabled while blank)
- Cancel

**LastResponseSheet**
- Title: "Last response"
- Body: Monospace, scrollable text of session.lastResponse
- Dismiss to close

**QuickCommandsSheet**
- Title: "Quick commands"
- System section: 13 chips (yes, no, continue, skip, /exit, ESC, Ctrl-b, arrow keys, Page Up/Down, Tab)
- Saved section (if present): List of saved commands from /api/commands
- Custom section: Text field + Send button
- Swipe down to dismiss

**Confirmation Dialogs**
- Restart: "Warm-resume this session on the server…"
- Kill: "Signal the server to terminate this running session…"
- Delete: "The session history will be removed from the server…"

**Bulk Delete Confirmation**
- "Delete N sessions?"
- "These sessions will be removed from the server. This cannot be undone."

#### States/Data Bindings
| Field | StateFlow | ViewModel Call |
|-------|-----------|----------------|
| `state` | `vm.state` | `SessionsViewModel.state` |
| Filter text | `state.filterText` | `setFilterText(text)` |
| Backend filter | `state.backendFilter` | `toggleBackendFilter(name)` |
| Show history | `state.showHistory` | `toggleShowHistory()` |
| Sort order | `state.sortOrder` | `setSortOrder(order)` |
| Visible sessions | `state.visibleSessions` | (computed from sessions + filters) |
| Reorder mode | `state.reorderMode` | `toggleReorderMode()` |
| Refresh spinner | `state.refreshing` | Auto-managed by VM polling |
| Active profile | `state.activeProfile` | `selectProfile(id)` / `selectAllServers()` |
| Reachability | `state.activeReachable` | Transport-managed |

#### Actions/Endpoints
| Action | VM Function | Endpoint |
|--------|-------------|----------|
| Mute/unmute | `toggleMute(id, muted)` | `PUT /api/sessions/{fullId}` (via Session.fullId) |
| Rename | `rename(id, newName)` | `PUT /api/sessions/{fullId}` + {"name": …} |
| Kill | `kill(id)` | `DELETE /api/sessions/{fullId}?mode=kill` |
| Restart | `restart(id)` | `POST /api/sessions/{fullId}/restart` |
| Delete | `delete(id)` | `DELETE /api/sessions/{fullId}` |
| Delete many | `deleteMany(ids)` | Calls delete() per id |
| Quick reply | `quickReply(id, text)` | `POST /api/sessions/{sessionId}/reply` |
| Fetch saved commands | `fetchSavedCommands(id)` | `GET /api/commands` |
| Refresh | `refresh()` | `GET /api/sessions` + transport ping |
| Move up/down | `moveUp(id)` / `moveDown(id)` | Updates local `customOrder` list |

#### Data Sources
- **Sessions**: `ServiceLocator.transportFor(profile).listSessions()` (cached, auto-polled every 5s)
- **Backends**: Per-session `backend` field from SessionDto
- **Profiles**: `ServiceLocator.profileRepository.observeAll()`
- **Active server**: `ServiceLocator.activeServerStore.observe()`
- **Reachability**: Transport's isReachable state

#### Known Gaps vs PWA
- None identified in current implementation. Mobile uses bulk-select + delete; PWA uses individual row delete buttons. Both are present in current code.

#### Known Recently-Fixed Items (v0.34.6)
- Session kill/restart/delete now pass fullId + {"id":...} (See Session.fullId computed prop, StateOverrideDto SerialName changes)
- Delete button added to detail and list rows; gated on terminal states
- ChatTranscriptPanel added for output_mode=="chat" sessions
- Session.outputMode and inputMode fields added (migration 4.sqm)

---

### 2. NewSessionScreen
**Route:** `sessions/new`  
**File:** `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/sessions/NewSessionScreen.kt`

#### How it flows
Form to start a new session. User fills task + optional metadata (name, working dir, profile, backend, resume). On submit, calls POST /api/sessions/start with those fields. If backend != activeBackend, calls PUT /api/backends/{name} first to switch server-wide (since /start has no per-session backend param on older servers).

#### Fields
| Label | Type | StateFlow | Config | Notes |
|-------|------|-----------|--------|-------|
| Session name | OutlinedTextField | `sessionName` | N/A | Optional; defaults to empty |
| Task | OutlinedTextField | `task` | N/A | Required; 8-line multiline; "From library" dropdown seeds from /api/commands |
| Server | ExposedDropdownMenuBox | `selectedProfileId` | N/A | Populated from `profileRepository.observeAll()` filtered to enabled |
| LLM backend | ExposedDropdownMenuBox | `pickedBackend` | N/A | Hidden if /api/backends unsupported; seeded to server's active backend; shows "(active)" badge |
| Profile | ExposedDropdownMenuBox | `pickedServerProfile` | N/A | Optional; "Default (no profile)" option; populated from /api/profiles |
| Model | ExposedDropdownMenuBox | `pickedModel` | N/A | Visible only for ollama/openwebui; server-configured (read-only informational) |
| Working directory | OutlinedTextField + Browse button | `workingDir` | N/A | Optional; Browse opens FilePickerDialog (FolderOnly mode) |
| Resume previous | ExposedDropdownMenuBox | `resumeId` | N/A | Visible if recentDone.isNotEmpty(); "Start fresh" option |
| Auto git init | Switch | `autoGitInit` | N/A | Default false |
| Auto git commit | Switch | `autoGitCommit` | N/A | Default true |

#### Recent Sessions List
- **Title**: "Recent sessions"
- **Content**: Up to 20 most-recent done sessions (Completed/Killed/Error), sorted by lastActivityAt descending
- **Per-row**: Name (60-char truncated) | State · Backend | Restart button

#### Buttons
- Cancel (enabled unless submitting)
- Start (enabled unless task blank or submitting; shows spinner while submitting)

#### Error Handling
- Banner at top shows errors (task empty, start failure, backend switch failure)

#### Data Sources
- Profiles: `ServiceLocator.profileRepository.observeAll()`
- Active server: `ServiceLocator.activeServerStore.observe()`
- Backends: `transport.listBackends()` (filtered by enabled flag in config)
- Profiles: `transport.listProfiles()`
- Models: `transport.listModels(backend)` (ollama/openwebui only)
- Saved commands: `SavedCommandsViewModel.state.commands`
- Recent done sessions: `transport.listSessions()` filtered and sorted

#### Actions/Endpoints
| Action | Endpoint |
|--------|----------|
| List profiles | `GET /api/profiles` |
| List backends | `GET /api/backends` |
| Fetch config | `GET /api/config` |
| List models | `GET /api/backends/{name}/models` |
| Set active backend | `PUT /api/backends/{name}` |
| Start session | `POST /api/sessions/start` (task, workingDir, profileName, backend, resumeId, name, autoGitInit, autoGitCommit) |
| Restart previous | `POST /api/sessions/{id}/restart` |

---

### 3. SessionDetailScreen
**Route:** `sessions/{sessionId}`  
**File:** `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/sessions/SessionDetailScreen.kt`

#### How it flows
Shows full session output (terminal or chat) with reply composer. Subscribes to WS events for live updates. Terminal view renders pane_capture; chat mode renders chat_message events as bubbles. User can switch between Terminal/Chat view modes (persisted in SharedPreferences). Server-side chat-mode sessions (output_mode=="chat") lock into chat view. Buttons: Stop (active), Restart (terminal), Delete (terminal), Kill, Timeline, Rename. Dialogs for state override, schedule, timeline viewer.

#### Top App Bar
- **Title (clickable for rename)**:
  - Line 1: Session name or taskSummary or sessionId
  - Line 2: sessionId | LLM backend (uppercase) | messaging backend (secondary color) | hostname prefix
- **Navigation icon**: X (Close)
- **Actions**: Connection dot (green/red/grey) at right

#### TabRow (below top bar)
- Tabs: "tmux" | "channel"
- Selected reflects `chatMode` state (persisted in SharedPreferences)
- Tab controls whether to render TerminalView or ChatEventList (unless server-chat-mode)

#### SessionInfoBar
**Row below tabs with:**
- Backend chip (if present, lowercase)
- Mode chip (sessionMode, lowercase)
- State pill (clickable → StateOverrideDialog)
  - Labels: new, running, waiting_input, rate_limited, complete, killed, failed
  - Colors: state-specific (PWA palette)
- Action buttons:
  - If active (Running/Waiting/RateLimited): "■ Stop" (error color)
  - If terminal (Completed/Killed/Error): "↻ Restart" + "🗑 Delete" (delete error-colored)
- "⏱ Timeline" button

#### Banners
- **Error banner** (if `state.banner`): Dismissable error message
- **Connection banner** (if reachable==false): "Server unreachable. Connection issues affecting live stream."
- **Input required banner** (if state==Waiting): "Waiting on input — [latest prompt text]" with X dismiss

#### Output Surface
Three rendering paths:

**Path 1: Server-side chat mode** (session.outputMode=="chat")
- `ChatTranscriptPanel` (read-only, structured messages, v0.34.6 new)

**Path 2: User chat-view toggle** (not server-side chat)
- `ChatEventList` (renders events as bubbles with quick-reply buttons)

**Path 3: Terminal mode** (default)
- `TerminalToolbar` (with session ID display)
- `TerminalView` (xterm emulator)
  - Backend-specific column enforcement:
    - claude-code / claude: 120 cols × 40 rows
    - Others: 80 cols × 24 rows
  - Frozen (no writes) when terminal state reached
- `InlineNotices` (yellow banner for latest prompt + rate-limit)

#### Scheduled Schedules Strip
- Shows pending schedules for this session (if `sessionSchedules.supported && schedules.isNotEmpty()`)
- Rows with cancel button per schedule

#### ReplyComposer
- Text field (or voice input icon)
- Send button
- Schedule button
- Quick-reply buttons (if Waiting state)
- Auto-focus when prompt is current

#### Dialogs

**Kill Confirmation**
- "Kill session?"
- "This stops the tmux session on the server. The session cannot be resumed…"

**Delete Confirmation**
- "Delete session?"
- "This removes the session record from the server. Tracking data on disk is preserved…"

**StateOverrideDialog**
- Dropdown to pick new state (all SessionState values)
- Confirmation button

**TimelineSheet**
- ModalBottomSheet showing all events (pane_capture, prompt, rate_limit, etc.)
- Jump-to-time controls (PWA-style)

**RenameDialog**
- Single-line text field
- Save / Cancel

**ScheduleDialog**
- Task field (pre-seeded with: reply text, latest prompt, or task summary)
- Cron expression field
- Enabled toggle
- Create button

#### States/Data Bindings
| Field | StateFlow | Binding |
|-------|-----------|---------|
| Session | `vm.state.session` | Observation of SessionDto stream |
| Events | `vm.state.events` | WS event stream (live) |
| Reply text | `vm.state.replyText` | User input in composer |
| Replying | `vm.state.replying` | Spinner in Send button |
| Chat mode | SharedPreferences + local state | `modePrefs.getBoolean("chat_mode", false)` |
| Pending prompts | `vm.state.pendingPromptText` | Extracted from latest PromptDetected event |
| Messaging backend | `vm.state.messagingBackend` | From session WS updates |
| Reachability | `vm.state.reachable` | Transport ping state |

#### Actions/Endpoints
| Action | VM Function | Endpoint |
|--------|-------------|----------|
| Kill | `vm.kill()` | `DELETE /api/sessions/{fullId}?mode=kill` |
| Delete | `vm.delete(onDeleted)` | `DELETE /api/sessions/{fullId}` |
| Rename | `vm.rename(newName)` | `PUT /api/sessions/{fullId}` + {"name": …} |
| Override state | `vm.overrideState(newState)` | `PUT /api/sessions/{fullId}` + {"state": …} |
| Send reply | `vm.sendReply()` | `POST /api/sessions/{sessionId}/reply` |
| Send quick reply | `vm.sendQuickReply(text)` | `POST /api/sessions/{sessionId}/reply` |
| Dismiss banner | `vm.dismissBanner()` | (Local state only) |
| Schedule reply | `schedulesVm.create(task, cron, enabled, sessionId)` | `POST /api/schedules` |
| List schedules | `sessionSchedulesVm.refresh()` | `GET /api/sessions/{sessionId}/schedules` |
| Cancel schedule | `sessionSchedulesVm.cancel(scheduleId)` | `DELETE /api/schedules/{id}` |

#### Data Sources
- **Session**: WS subscription to session updates (SessionDto)
- **Events**: WS subscription to event stream (SessionEvent types)
- **Schedules**: `GET /api/sessions/{sessionId}/schedules` (if supported)
- **Saved commands**: `GET /api/commands` (lazy-loaded)
- **Server info**: `GET /api/info` (for daemon metadata)

#### Known Gaps vs PWA
- None identified. v0.34.6 added ChatTranscriptPanel for output_mode=="chat". Input mode selection is pending.

#### Known Recently-Fixed Items (v0.34.6)
- Delete button now visible for terminal states
- Session.fullId computed prop ensures mutations pass correct identifier
- ChatTranscriptPanel renders chat_message events (new structured output type)
- output_mode and input_mode fields propagated from server

---

### 4. AlertsScreen
**Route:** `home/alerts`  
**File:** `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/alerts/AlertsScreen.kt`

#### How it flows
Displays sessions that need user input (state==Waiting && !muted). Same filtered view drives the bottom-nav badge. Tap row → navigate to session detail. Swipe left → dismiss (mutes the session, removing it from alerts). Each row shows session name, task, and prompt context. Optional Schedule button for each alert.

#### Top App Bar
- Title: "Alerts"

#### Main Content
- **If empty**: "No sessions need input. You're caught up."
- **If has alerts**: LazyColumn of AlertRow items

#### AlertRow
- **Title**: Session name or ID
- **Body**: Task summary (with "Waiting on input" label)
- **Meta**: Hostname prefix (tertiary color)
- **Action**: "Schedule reply…" button (bottom-right)
- **Swipe left** (80dp threshold): Dismiss (calls `vm.dismiss(sessionId)`)
- **Tap row**: Navigate to session detail (calls `onOpenSession(sessionId)`)

#### Dialogs

**ScheduleDialog**
- Task field pre-seeded with lastPrompt (first 200 chars) or taskSummary or ID
- Cron expression field
- Enabled toggle
- Create button

#### States/Data Bindings
| Field | StateFlow | Binding |
|-------|-----------|---------|
| Alerts | `vm.state.alerts` | Filtered sessions (Waiting && !muted) |
| Count | `vm.state.count` | Used for bottom-nav badge |

#### Actions/Endpoints
| Action | VM Function | Endpoint |
|--------|-------------|----------|
| Dismiss alert | `vm.dismiss(sessionId)` | `PUT /api/sessions/{sessionId}` + {"muted": true} |
| Schedule reply | `schedulesVm.create(task, cron, enabled, sessionId)` | `POST /api/schedules` |

#### Data Sources
- **Alerts**: Same session stream as Sessions tab, filtered for Waiting && !muted
- **Schedules VM**: Shared across app

#### Known Gaps vs PWA
- None identified. Both implement swipe-to-dismiss. PWA may have additional tabs (Active/Resolved); Android shows only the single "waiting input" filter.

---

### 5. SettingsScreen
**Route:** `home/settings`  
**File:** `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/settings/SettingsScreen.kt`

#### How it flows
Five-tab interface (Monitor, General, Comms, LLM, About) with cards for various settings categories. Each tab targets the active server (selectable via top-right icon). Cards auto-reload when active server changes (v0.34.5).

#### Top App Bar
- **Title**: "Settings" + active profile display name
- **Actions**: Storage icon → ServerPickerSheet

#### ScrollableTabRow
- Tabs: Monitor | General | Comms | LLM | About
- Underline: PWA accent2 color
- Edge padding: 8.dp

#### Tab Content (per SettingsTab enum)

**Monitor Tab**
Cards in order:
1. `StatsScreenContent` (CPU/memory/disk stats from /api/stats)
2. `KillOrphansCard` (admin action to terminate stuck processes)
3. `MemoryCard` (LLM context memory settings)
4. `SchedulesCard` (view/create scheduled replies)
5. `DaemonLogCard` (streaming daemon logs)

**General Tab**
Cards in order:
1. `SecurityCard` (Biometric unlock toggle)
2. `ConfigFieldsPanel` (Datawatch config fields)
3. `ConfigFieldsPanel` (AutoUpdate fields)
4. `ConfigFieldsPanel` (Session fields)
5. `ConfigFieldsPanel` (RTK fields)
6. `ConfigFieldsPanel` (Pipelines fields)
7. `ConfigFieldsPanel` (Autonomous fields)
8. `ConfigFieldsPanel` (Plugins fields)
9. `ConfigFieldsPanel` (Orchestrator fields)
10. `ConfigFieldsPanel` (Whisper fields)
11. `KindProfilesCard` (kind="project", title="Project profiles")
12. `KindProfilesCard` (kind="cluster", title="Cluster profiles")
13. `NotificationsCard` (push/ntfy settings)

**Comms Tab**
Cards in order:
1. `ConfigFieldsPanel` (CommsAuth fields)
2. `ServersCard` (list of configured profiles with Edit/Delete actions)
3. `ConfigFieldsPanel` (WebServer fields)
4. `ConfigFieldsPanel` (McpServer fields)
5. `ConfigFieldsPanel` (Proxy fields)
6. `ChannelsCard` (messaging backends)
7. `FederationPeersCard` (federation configuration)
8. `CertInstallCard` (CA cert installation)

**LLM Tab**
Cards in order:
1. `LlmConfigCard` (list of backends with active badge)
2. `ConfigFieldsPanel` (Memory fields)
3. `ConfigFieldsPanel` (LlmRtk fields)
4. `DetectionFiltersCard` (prompt detection)
5. `SavedCommandsCard` (command library)
6. `FiltersCard` (output filters)

**About Tab**
Cards in order:
1. `AboutCard` (animated logo, version, daemon info)
2. `ApiLinksCard` (links to /api endpoints)
3. `UpdateDaemonCard` (auto-update toggle + manual check)
4. `RestartDaemonCard` (restart button)
5. `ConfigViewerCard` (raw config editor)

#### SecurityCard
- Label: "Biometric unlock"
- Description: "Require fingerprint or face on every app open."
- Toggle: Calls `gate.setEnabled(it)` (enabled only if device has Class-3 biometric)

#### ServersCard
- Title: "Servers"
- Add button (+ icon)
- If no servers: "No servers yet — tap + above to add one."
- Per server:
  - Name | URL
  - Badges (if applicable): "no auth", "trust-all TLS"
  - More menu: Download CA cert, Delete server

#### AboutCard
- Animated logo (live matrix rain + eye + arcs)
- App version
- Daemon version
- Daemon host
- Uptime
- Sessions stats (total / running / waiting)

#### Data Bindings

**ConfigFieldsPanel**
- Reads/writes config via `ConfigFieldRepository` (auto-save)
- Fields are state-driven dropdowns, text inputs, toggles, etc.
- Each field maps to a dot-path config key (e.g., "backends.claude-code.enabled")

**Cards**
- Most cards observe active server from `ServiceLocator.activeServerStore`
- Refresh on profile change (v0.34.5)

#### Actions/Endpoints
| Card | Action | Endpoint |
|------|--------|----------|
| Stats | (read-only) | `GET /api/stats` |
| Memory | Save config | `PUT /api/config` (via ConfigFieldsPanel) |
| Schedules | List/create/cancel | `GET/POST/DELETE /api/schedules` |
| Kill Orphans | Kill stuck procs | `POST /api/admin/kill-orphans` |
| Daemon Log | Stream logs | WS subscription |
| Config panels | Save config | `PUT /api/config` |
| Biometric | Toggle | Local SharedPreferences |
| Servers | Edit/delete | `NavController` + `profileRepository.delete()` |
| Cert | Download | `GET /api/cert` (saves to Downloads) |
| Channels | Configure | Per-backend config (via ConfigFieldsPanel) |
| LLM Config | (read-only) | `GET /api/backends` |
| Saved Commands | List/create/delete | `GET/POST/DELETE /api/commands` |

#### Data Sources
- **Active server**: `ServiceLocator.activeServerStore.observe()`
- **All profiles**: `ServiceLocator.profileRepository.observeAll()`
- **Config**: `ServiceLocator.configRepository` (per-server)
- **Stats**: Transport's `stats()` call
- **Schedules**: Transport's schedules endpoints
- **Daemon info**: Transport's `fetchInfo()` call

#### Known Gaps vs PWA
- None identified. Tabs, card order, and field coverage match PWA structure. Biometric unlock is Android-specific (no PWA equivalent).

#### Known Recently-Fixed Items
- Auto-reload on active-server change (v0.34.5)
- Config fields panel now reads/writes dot-path keys correctly

---

### 6. OnboardingScreen
**Route:** `onboarding`  
**File:** `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/onboarding/OnboardingScreen.kt`

#### How it flows
Splash → Onboarding (if no profiles) → AddServer (on "Get Started"). Simple welcome flow.

#### Content
- Welcome message
- Illustration (launcher foreground asset)
- "Get Started" button → `onGetStarted()`

---

### 7. AddServerScreen / EditServerScreen
**Routes:** `servers/add`, `servers/edit/{profileId}`  
**Files:** `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/servers/AddServerScreen.kt`, `EditServerScreen.kt`

#### How it flows
Form to add or edit a ServerProfile. Fields: displayName, baseUrl, bearer token, trust-anchor config. On save, persists to `profileRepository` and returns to Home.

#### Fields
| Label | Type | Required | Notes |
|-------|------|----------|-------|
| Display name | OutlinedTextField | Yes | User-friendly label |
| Server URL | OutlinedTextField | Yes | https://... |
| Bearer token | OutlinedTextField | No | Stored in encrypted vault (BiometricGate) |
| Trust-all TLS | Toggle | No | Dangerous; shows warning |
| Session timeout | OutlinedTextField | No | Seconds before stale session removal |
| Verify SSL | Toggle | No | Certificate verification |

#### Actions/Endpoints
| Action | Function | Endpoint |
|--------|----------|----------|
| Save profile | `profileRepository.insert()` or `.update()` | Local DB (SQLCipher) |
| Fetch info | `transport.fetchInfo()` | `GET /api/info` (connectivity check) |
| Delete profile | `profileRepository.delete()` | Local DB + token vault cleanup |

---

### 8. MatrixSplashScreen
**Route:** `splash`, `splash/replay`  
**File:** `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/splash/MatrixSplashScreen.kt`

#### How it flows
Animated splash with matrix rain + eye + arcs + tablet frame. Dwell 3.2s minimum, then if profiles loaded, auto-advance to Home or Onboarding. Replay action (v0.33.7) shows splash again.

---

## Additional Surfaces (Non-Tab)

### Dialogs & Sheets (app-wide)

**ServerPickerSheet** (3-finger swipe up or top-right icon in Settings)
- List all profiles with enable/disable status
- "All servers" option (multi-server fan-out)
- Edit buttons per profile
- Add server button

**FilePickerDialog**
- Used in NewSessionScreen (Browse working dir) and EditServerScreen
- Supports FolderOnly or FilesAndFolders modes

---

## State Management Architecture

### ViewModel Layer
Each screen has a dedicated ViewModel managing cached state:
- `SessionsViewModel`: Sessions tab state (filter, sort, profiles, reachability)
- `NewSessionViewModel`: (implicit in Compose state)
- `SessionDetailViewModel`: Session detail state (events, reply text, reachability)
- `AlertsViewModel`: Alerts list (waiting sessions, badge count)
- (SettingsScreen uses ServiceLocator directly for profile/config repos)

### Data Repositories
- `profileRepository`: Server profiles (SQLCipher DB)
- `configRepository`: Config keys (per-server, auto-sync via `/api/config`)
- `activeServerStore`: Current server selection (SharedPreferences)
- `tokenVault`: Encrypted bearer tokens (BiometricGate + KeyStore)

### Transport Client
- `ServiceLocator.transportFor(profile)`: Per-profile REST + WS client
- Auto-reconnect on session detail subscribe
- Reachability tracking (periodic pings)
- Event streaming (pane_capture, chat_message, etc.)

---

## Pending Features / Known Blockers

1. **Per-session backend override** (NewSessionScreen mentions v0.11 phase 2.7): Server-side /api/sessions/start doesn't accept per-session backend param yet. Mobile works around by calling PUT /api/backends first.

2. **Input mode selection** (SessionDetailScreen): outputMode=="chat" and inputMode=="none" support is in place for rendering decisions, but no UI control to switch modes per-session yet.

3. **ESC / Ctrl-b in quick commands** (SessionsScreen QuickCommandsSheet): Notes that WS `command` channel not subscribed on list surface; users open detail to send those.

4. **Model selection for ollama/openwebui** (NewSessionScreen): Model picker is informational only; actual model selection requires backend config change.

5. **All-servers mode** (SessionsScreen): Skeleton in place (state.allServersMode, selectAllServers()); full fan-out pending.

---

## Summary Table

| Screen | Route | Status | Key Gaps |
|--------|-------|--------|----------|
| Sessions | `home/sessions` | Complete | None |
| New Session | `sessions/new` | Complete | Per-session backend override pending server support |
| Session Detail | `sessions/{id}` | Complete | Input mode UI control pending |
| Alerts | `home/alerts` | Complete | None |
| Settings | `home/settings` | Complete | None |
| Onboarding | `onboarding` | Complete | (First-time only) |
| Add/Edit Server | `servers/add`, `servers/edit/{id}` | Complete | None |
| Splash | `splash` | Complete | None |

---

**Document generated:** 2026-04-23  
**Android version:** v0.34.6 release  
**Datawatch parent API:** v4.x (supports output_mode, input_mode, chat_message WS frames)

