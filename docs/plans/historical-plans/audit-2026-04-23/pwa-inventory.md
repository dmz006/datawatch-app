# PWA Screen Inventory – Comprehensive Audit (2026-04-23)

**Date**: 2026-04-23  
**Source**: `/home/dmz/workspace/datawatch/internal/server/web/app.js` (6665 lines)  
**Templates**: `/home/dmz/workspace/datawatch/templates/`  
**Live Instance**: https://localhost:8443 (self-signed cert)  
**Screenshots**: `/home/dmz/workspace/pwa-audit/out/*.png`

---

## Overview: PWA Design & Architecture

### Overall Dimensions & Viewport
- **Layout**: Mobile-first single-column PWA (~480px on mobile, centered max-width on desktop)
- **Navigation**: Bottom tab bar (4 buttons) sticky at viewport bottom
  - Sessions / New / Alerts / Settings (always visible, color-coded)
- **Header**: Top app bar with connection indicator (green dot = WS connected; red = disconnected)
- **Color Palette**:
  - Purple accent: `#8B5CF6` (buttons, active tabs, primary actions)
  - Green dot (active/success): `#22C55E`
  - Error red: `#EF4444`
  - Warning orange: `#F59E0B`
  - Dark background: CSS var `--bg` (appears ~#0F172A)
  - Secondary bg: CSS var `--bg2` (~#1E293B)
  - Text primary: `--text` (~#F1F5F9)
  - Text secondary: `--text2` (~#94A3B8)
  - Border: `--border` (~#334155)
- **Typography**:
  - Monospace for code/terminal: `font-family: monospace`
  - Headlines: Regular weight with letter-spacing for emphasis
  - Body: System font stack, font-size: 12–13px for density

### State Management (key global variables, line 28–67)
```javascript
state = {
  connected: false,              // WebSocket connected
  sessions: [],                  // All sessions from /api/sessions
  activeView: 'sessions',        // Current top-level view
  activeSession: null,           // Full ID of session being viewed
  activeOutputTab: 'tmux',       // 'tmux' or 'channel' (session detail)
  outputBuffer: {},              // sessionId -> terminal lines
  channelReplies: {},            // sessionId -> [{text, direction, ts}]
  chatMessages: {},              // sessionId -> [{role, content, ts}] (chat-mode sessions)
  sessionOrder: [],              // Manual drag-reorder persistence
  sessionFilter: '',             // Text filter for session list
  showHistory: false,            // Show completed/killed sessions
  selectMode: false,             // Batch-select mode for deletion
  selectedSessions: new Set(),   // Full IDs of selected inactive sessions
  sessionFilter: '',             // Dynamic filter string
}
```

### Navigation Surface (lines 969–1024)
```javascript
navigate(view, sessionId, fromPopstate)
// Views: 'sessions' | 'new' | 'settings' | 'session-detail' | 'alerts'
// Routes via History API for Android back button support
switchSettingsTab(tab)
// Tabs: 'monitor' | 'general' | 'comms' | 'llm' | 'about'
```

---

## Screen 1: Sessions List

**Route**: `navigate('sessions')`  
**Render function**: `renderSessionsView()` (lines 1088–1190)  
**Header title**: "Datawatch"  
**Bottom nav**: Sessions button active (purple)

### Layout Structure
```
┌─────────────────────────────────────────┐
│ Datawatch                     ● (green)  │  ← Header with WS indicator
├─────────────────────────────────────────┤
│ Filter sessions...      [claude-code ×] │  ← Filter toolbar
│                         [oc ×]          │
│ Show history (2)                        │  ← Toggle history button + count
├─────────────────────────────────────────┤
│                                         │
│ ┌────────────────────────────────────┐ │
│ │ i7db | RUNNING | claude-code | 47m │ │
│ │ datawatch-app                    ⚙ │  ← Session card (draggable)
│ │ ───────────────────────────────── │ │
│ │ Waiting for input...             ↗ │  ← Quick-command button
│ └────────────────────────────────────┘ │
│                                         │
└─────────────────────────────────────────┘
              Bottom nav bar ↓
```

### Fields Rendered

#### Filter Row (lines 1126–1138)
| Field | Type | Config Key | Read Endpoint | Write Endpoint | Line | Behavior |
|-------|------|-----------|---------------|----------------|------|----------|
| **Session filter input** | text | session_filter (local state) | — | — | 1128–1130 | Real-time re-render on input; focus restoration on re-render |
| **Backend filter badges** | chips | — | /api/sessions (cached) | — | 1120–1125 | Computed from `state.sessions.llm_backend` (unique set); toggleable |
| **Clear filter button** | button (icon ✕) | — | — | — | 1131 | Only visible when filter is non-empty; onclick clears `state.sessionFilter` |
| **Server indicator** | badge (globe) | — | — | — | 1134 | Shows if `state.activeServer !== 'local'`; click to return to local |
| **Scheduled events badge** | button (clock) | — | /api/schedules?state=pending | — | 1135–1215 | Popup dropdown showing pending scheduled commands; auto-load (line 1169) |
| **Show history toggle** | button | showHistory (local state) | — | — | 1136–1138 | Shows count of completed sessions; clicking toggles & exits select mode |
| **Select mode toggle** | button (checkbox icon) | selectMode (local state) | — | — | 1140–1141 | Only visible when history is shown and count > 0 |

#### Session Cards (lines 1158–1432)

Each card is generated by `sessionCard(sess, idx, total)` (lines 1361–1432):

| Element | Visual | Config Key | Endpoint | Line | Behavior |
|---------|--------|-----------|----------|------|----------|
| **Card container** | `.session-card {state}` | — | — | 1410 | Draggable unless in select mode; click navigates to detail; drop reorders via `sessionDrop()` (line 1414) |
| **Session ID (short)** | `{sess.id}` | — | — | 1418 | Extracted from `full_id` |
| **State badge** | `.state {state}` colored | — | — | 1419 | Text: `sess.state` (running/waiting_input/complete/failed/killed/rate_limited) |
| **Backend badge** | small label | — | — | 1420 | Text: `sess.llm_backend` (claude-code, ollama, aider, etc.); omitted if empty |
| **Server badge** | small label (if remote) | — | — | 1421 | Text: `sess.server`; only shown if not 'local' |
| **Time badge** | `{ago}` | — | — | 1422 | `timeAgo(sess.updated_at)` computed live |
| **Action buttons** | conditional | — | — | 1375–1385 | **Active session**: `Stop` button → `killSession(fullId)` (line 1378). **Done session**: `Restart` → `restartSession(fullId)` (line 1383), `Delete` → `deleteSession(fullId)` (line 1384) |
| **Drag handle** | ⋯⋯ icon | — | — | 1424 | Vertical drag to reorder; `ondragstart=sessionDragStart` (line 1412) |
| **Checkbox** (in select mode) | `<input type="checkbox">` | — | — | 1417 | Only shown when `state.selectMode === true` and session is done; toggles `state.selectedSessions` |
| **Task text** | truncated display name | — | — | 1365–1366 | `sess.name || sess.task || '(no task)'`; max 80 chars with ellipsis |
| **Last response icon** | 📄 button | — | — | 1428 | Only visible if `sess.last_response`; onclick `showResponseViewer(fullId)` |
| **Waiting input row** | collapsible text + quick-cmd | — | /api/commands | 1388–1403 | Shows last 4 lines of `prompt_context` (or `last_prompt`); quick-command popup hidden by default |

#### Quick-Command Popup (lines 1434–1468)

Triggered by clicking the ➤ icon in a waiting_input card, or via `showCardCmds(fullId)`:

| Element | Type | Endpoint | Line | Action |
|---------|------|----------|------|--------|
| **System commands select** | `<select>` | — | 1447–1453 | yes/no/continue/skip/ESC/tmux-prefix/exit |
| **Saved commands** | optgroup | /api/commands | 1454–1457 | User-created commands from Settings → LLM → Saved Commands |
| **Custom input field** | text + send + cancel | — | 1461–1464 | `cardCustom-{shortId}` hidden div; shows when "Custom..." selected; enter or click send → `cardSendCustom()` (line 1484) |

#### Select Bar (when select mode active, lines 1172–1189)

Fixed bottom element above nav bar:
```
[✓ All (9)] [🗑 Delete (3)] [Cancel]
```

| Button | Onclick | Line | Behavior |
|--------|---------|------|----------|
| **All/None** | `selectAllInactive()` | 1183 | Toggles all completed sessions; deselect if all are selected |
| **Delete** | `deleteSelectedSessions()` | 1184 | POST /api/sessions/delete for each `state.selectedSessions` with `{id, delete_data: true}` |
| **Cancel** | `toggleSelectMode()` | 1185 | Exit select mode; clear selections |

### Buttons & Actions

| Button | Label | Line | Onclick | Endpoint | Body |
|--------|-------|------|---------|----------|------|
| **Filter badge** | Backend name + count | 1124 | `setBackendFilter('backend-name')` | — | Toggle filter text to backend name (or clear if already selected) |
| **Clear filter** | ✕ | 1131 | `state.sessionFilter=''` | — | Direct state mutation |
| **Server return badge** | Globe icon | 1134 | `selectServer(null)` | — | Sets `state.activeServer = null` and re-renders |
| **Schedule badge** | Clock + count | 1201 | `toggleGlobalScheduleDropdown()` | — | Show/hide dropdown of pending schedules |
| **Schedule cancel** | ✕ in dropdown | 1211 | `cancelSchedule(sc.id, '')` | POST /api/schedules/cancel | Deletes pending schedule |
| **Show history toggle** | Show/Hide history | 1136 | `toggleHistory()` | — | Toggle `state.showHistory`, clear select if hiding |
| **Select mode toggle** | Checkbox icon | 1140 | `toggleSelectMode()` | — | Toggle `state.selectMode` |
| **Stop (active session)** | Stop (red) | 1378 | `killSession(fullId)` | POST /api/sessions/kill | `{session_id: fullId}` |
| **Quick-command (waiting)** | ➤ arrow | 1380 | `showCardCmds(fullId)` | /api/commands | Fetches and shows popup |
| **Restart (done session)** | Restart icon | 1383 | `restartSession(fullId)` | POST /api/sessions/restart | `{session_id: fullId}` |
| **Delete (done session)** | Delete icon | 1384 | `deleteSession(fullId)` | POST /api/sessions/delete | `{id: fullId, delete_data: false}` |
| **Response button** | 📄 | 1428 | `showResponseViewer(fullId)` | — | Shows last_response in a modal |

### Dialogs / Modals

#### Quick-Command Popup (lines 1434–1468)
- **Name**: Card quick-command selector
- **How triggered**: Click ➤ on a waiting_input card
- **Fields**:
  - System commands: yes, no, continue, skip, Escape, tmux Ctrl-b, /exit
  - Saved commands: from /api/commands
  - Custom: text input with send/cancel
- **Action**: `cardSendCmd(fullId, cmd)` (lines 1498–1521) or `cardSendCustom()` (lines 1484–1490)

#### Confirm Delete Modal (showConfirmModal, lines 1274–1287)
- **Triggered**: Click Delete in select bar
- **Body**: "Delete N session(s) and their data?"
- **Actions**: POST /api/sessions/delete for each selected session (parallel Promise.all)

### Data Source

| Feed | Type | Endpoint | Update Mechanism | Line |
|------|------|----------|------------------|------|
| **Session list** | REST (cached in state) | GET /api/sessions | WS message `update_sessions` (triggers `renderSessionsView`)  | 1012, 916, 1088 |
| **Commands** | REST (on-demand) | GET /api/commands | Fetched when quick-command popup opens | 1442 |
| **Pending schedules** | REST (on-demand) | GET /api/schedules?state=pending | Auto-load at render (line 1169) | 1195–1215 |
| **Server list** | REST (cached in state) | GET /api/servers | WS subscription (state.servers updated) | 54, onMessage |

### Notable Behaviors

- **Drag-drop reorder**: Session cards are draggable; drop target reorders via `sessionDragStart/Over/Drop/End` (lines 1412–1415). Order persisted to `localStorage('cs_session_order')`.
- **Real-time filter**: Input changes re-render the list immediately; cursor position restored.
- **Keyboard**: Enter in filter input focuses the filter input again (line 1166).
- **Select mode**: Only available when history is shown and there are inactive sessions.
- **Time display**: Each card updates every poll (via WS); `timeAgo()` recalculated fresh each render.
- **Server switching**: Purple server indicator at top-right shows selected remote server; click returns to local.
- **Empty state**: When no active/recent sessions, shows watermark + "No active sessions" message with prompt to tap New.

---

## Screen 2: Session Detail

**Route**: `navigate('session-detail', sessionId)`  
**Render function**: `renderSessionDetail(sessionId)` (lines 1524–1826)  
**Header title**: Session name + short ID (e.g., "datawatch-app #i7db")  
**Header edit**: Clickable name → `startHeaderRename(sessionId)` inline edit  
**Bottom nav**: Buttons inactive (greyed)

### Layout Structure
```
┌────────────────────────────────────────────┐
│ ◄ │ datawatch-app #i7db                  ✎ │  ← Rename on click
├────────────────────────────────────────────┤
│ claude-code | channel | running | ⏹ Stop  │  ← Info bar (state clickable)
├────────────────────────────────────────────┤
│ ⏳ Waiting for MCP channel…                │  ← Connection banner (if not ready)
├────────────────────────────────────────────┤
│ ⚠️ Input Required                          │  ← Needs-input banner (if waiting)
│    Prompt context lines here               │     Dismissible (X button)
│    Tip: press 1 then Enter                 │
├────────────────────────────────────────────┤
│ [Tmux] [Channel] [?]               [A+/-] │  ← Output tabs + font controls
│ [xterm.js terminal rendering...]           │
│ [─ lots of output lines ─]                 │
│                                            │
├────────────────────────────────────────────┤
│ 💾 Response                                │  ← Quick panel (if active session)
├────────────────────────────────────────────┤
│ Input Required                             │  ← Input label (if waiting)
│ [Type your response...        ]  [➤ Send] │  ← Input bar (active session only)
│                             [Schedule ⏰]  │
└────────────────────────────────────────────┘
              (no bottom nav)
```

### Fields & Components

#### Session Info Bar (lines 1672–1678)
| Element | Content | Line | Behavior |
|---------|---------|------|----------|
| **Backend badge** | `sess.llm_backend` | 1674 | e.g. "claude-code" |
| **Mode badge** | `getSessionMode()` result: 'tmux' \| 'channel' \| 'acp' | 1675 | Computed from backend type |
| **State badge** | `sess.state` (clickable) | 1676 | **Click**: `showStateOverride(sessionId, element)` (line 2206) → Popup menu to manually set state |
| **Action buttons** | Conditional | 1677 | **Active**: "Stop" button. **Done**: "Restart" + "Delete" buttons |
| **Timeline button** | "Timeline" label | 1678 | onclick `toggleSessionTimeline(sessionId)` → shows bottom sheet with event timeline |

#### Connection Status Banner (lines 1591–1623)
- **Shown when**: Active session AND (channel or acp mode) AND connection not yet ready
- **Content**: Spinner + "Waiting for {MCP channel | ACP server}…" + dismiss button
- **Logic**:
  - Check `state.channelReady[sessionId]` (cached)
  - If not cached, scan `outputBuffer` for "Listening for channel" (channel) or "[opencode-acp] server ready" (acp)
  - If waiting_input state, disable connection requirement (allow input for consent prompts)
- **Dismiss**: `dismissConnBanner(sessionId)` sets internal flag (no API call)

#### Input Required Banner (lines 1570–1589)
- **Shown when**: `waiting_input` state AND (`prompt_context` OR `last_prompt` exists) AND not dismissed
- **Content**: Yellow "Input Required" badge + last 6 lines of prompt_context + tip if contains "trust" keywords
- **Dismiss**: Click X → `dismissNeedsInputBanner(sessionId)` (sticky until session leaves waiting_input)

#### Output Area (lines 1632–1805)

**Dual-tab mode** (if channel connected): Tmux tab + Channel tab  
**Single mode** (otherwise): Tmux only (or Chat if `output_mode=chat`)

##### Tmux Tab (terminal mode)
- **Rendering**: xterm.js via `initXterm(sessionId, rawLines, sessCols, sessRows)` (line 1804)
- **Font controls** (lines 1635–1643):
  - A- button: `changeTermFontSize(-1)` (decrease)
  - Font size display (e.g., "9px")
  - A+ button: `changeTermFontSize(1)` (increase)
  - Fit button: `termFitToWidth()` (fit to screen width)
  - Scroll mode button: `toggleScrollMode()` (enter tmux scroll mode with `Ctrl-b [`)

##### Channel Tab (if connected)
- **Content**: `state.channelReplies[sessionId]` rendered as:
  - Incoming (→): `channel-reply-line` class
  - Outgoing (←): `channel-send-line` class
  - Notify (⚡): `channel-notify-line` class
- **Auto-scroll**: `chatArea.scrollTop = chatArea.scrollHeight` on render

##### Chat Tab (if `output_mode=chat`)
- **Message bubbles**: `chat-bubble chat-{role}` (user/assistant/system)
- **Avatar + role label + timestamp** per message
- **Markdown rendering** for assistant messages: `renderChatMarkdown(m.content)` (line 1745)
- **Collapsible thread**: If > 6 messages, show first N messages in a `<details>` collapsible (lines 1737–1751)
- **Quick command buttons** at bottom (lines 1769–1774):
  - "💾 memories"
  - "🔍 recall:"
  - "🧠 kg query"
  - "🔬 research:"

#### Saved Commands Quick Panel (lines 1685–1685)
- **ID**: `savedCmdsQuick`
- **Shown when**: Active session AND input not disabled
- **Content**: Button "📄 Response" → `showResponseViewer(sessionId)`
- **Load**: `loadSavedCmdsQuick(sessionId)` (on render if active, line 1809)

#### Input Bar (lines 1686–1701)
- **Shown when**: Active session AND `input_mode !== 'none'`
- **Elements**:
  - **Input label** (if waiting): "Input Required" (visible only if `waiting_input`)
  - **Input field**: `id="sessionInput"`, placeholder varies by mode
  - **Send button**: Conditional
    - **Channel active + not waiting**: "➤ ch" button → `sendChannelMessage()`
    - **Otherwise**: "➤ tmux" button → `sendSessionInputDirect()`
    - **For inactive sessions**: "➤" button → `sendSessionInput()`
  - **Schedule button** (if active): "⏰" → `showScheduleInputPopup(sessionId)` (line 1667)

### Buttons & Actions

| Button | Label | Line | Onclick | Endpoint | Body |
|--------|-------|------|---------|----------|------|
| **Back** | ◄ (header) | — | `navigate('sessions')` | — | Pops history, returns to sessions list |
| **Header name edit** | Name or ✎ icon | 1047 | `startHeaderRename(sessionId)` | — | Inline input mode; Enter to confirm, Esc to cancel |
| **State pill** | State text (clickable) | 1676 | `showStateOverride(sessionId, element)` | — | Popup menu: running/waiting_input/rate_limited/complete/failed/killed (set via API) |
| **Stop (active)** | ⏹ Stop | 1626 | `killSession(sessionId)` | POST /api/sessions/kill | `{session_id: sessionId}` |
| **Restart (done)** | Restart icon | 1628 | `restartSession(sessionId)` | POST /api/sessions/restart | `{session_id: sessionId}` |
| **Delete (done)** | Delete icon | 1629 | `deleteSession(sessionId)` | POST /api/sessions/delete | `{id: sessionId, delete_data: false}` |
| **Timeline** | Timeline label | 1678 | `toggleSessionTimeline(sessionId)` | — | Show/hide bottom sheet with event timeline |
| **Tmux tab** | Tmux | 1647 | `switchOutputTab('tmux')` | — | Switch active output tab |
| **Channel tab** | Channel | 1648 | `switchOutputTab('channel')` | — | Switch active output tab |
| **Channel help** | ? icon | 1649 | `showChannelHelp()` | — | Show channel commands help |
| **Font decrease** | A- | 1636 | `changeTermFontSize(-1)` | — | Decrease terminal font; saved to localStorage |
| **Font increase** | A+ | 1638 | `changeTermFontSize(1)` | — | Increase terminal font; saved to localStorage |
| **Fit to width** | Fit | 1640 | `termFitToWidth()` | — | Call xterm FitAddon.fit() |
| **Scroll mode** | Scroll | 1642 | `toggleScrollMode()` | — | Enter tmux scroll mode (Ctrl-b [) |
| **Dismiss needs-input** | X | 1587 | `dismissNeedsInputBanner(sessionId)` | — | Set `state.needsInputDismissed[sessionId] = true` |
| **Dismiss connection** | X | 1619 | `dismissConnBanner(sessionId)` | — | Internal flag (no API call) |
| **Response viewer** | 📄 Response | 1685 | `showResponseViewer(sessionId)` | — | Modal showing `sess.last_response` formatted |
| **Send button** | ➤ (tmux/ch) | 1664–1665 | `sendSessionInput()` / `sendChannelMessage()` | POST /api/send_input or /api/channel/send | `{session_id, text: input.value}` |
| **Schedule button** | ⏰ | 1667 | `showScheduleInputPopup(sessionId)` | — | Popup to defer input to later time |

### Dialogs & Modals

#### State Override Menu (lines 2206–2220)
- **Trigger**: Click state badge on info bar
- **Content**: Dropdown with radio options: running/waiting_input/rate_limited/complete/failed/killed
- **Action**: POST /api/sessions/state with `{session_id, state: selected_state}`

#### Response Viewer Modal (lines 2255–2290)
- **Trigger**: Click 📄 Response button (on card or in detail)
- **Content**: Modal showing `sess.last_response` (full response text from LLM)
- **Format**: Monospace, read-only
- **Action**: Click close (X) to dismiss

#### Schedule Input Popup (lines 2143–2175)
- **Trigger**: Click ⏰ button in input bar
- **Fields**:
  - **Run at** (datetime picker)
  - **Text to send** (pre-filled from input box)
- **Actions**: "Schedule" → POST /api/sessions/schedule, "Cancel" → close popup

#### Timeline Sheet (expandable, lines 1678, toggleSessionTimeline)
- **Trigger**: Click "Timeline" button
- **Content**: Bottom sheet showing session event log
  - Events: state changes, input prompts, responses, connections, etc.
- **Format**: Timeline with timestamps and event descriptions

### Data Source

| Feed | Type | Endpoint | Update Mechanism | Line |
|------|------|----------|------------------|------|
| **Session data** | REST (cached) | GET /api/sessions (already in state) | WS `update_sessions` | 1530 |
| **Output buffer** | WS binary | /ws (subscribe message, line 1539) | WS `pane_output` messages | 1542 |
| **Channel replies** | WS | /ws (subscribe) | WS `channel_message` messages | 1543 |
| **Chat messages** | WS | /ws (subscribe) | WS `chat_message` messages | 1541 |
| **Session schedules** | REST | GET /api/sessions/{id}/schedules | Manual load (line 1811) | 1811 |

### Notable Behaviors

- **Terminal rendering**: xterm.js initialized only on first render to active session; preserved across view navigation (line 1708–1712)
- **Scroll mode**: Pressing Ctrl-b [ enters tmux scroll mode; exit with Ctrl-b ] or `exitScrollMode()` (line 1515)
- **Connection ready cache**: `state.channelReady[sessionId]` survives view navigation; cleared only on session restart
- **Waiting input prompt banner**: Sticky dismiss until session leaves `waiting_input` state; re-shows on next waiting_input episode
- **Auto-scroll chat**: Chat area scrolls to bottom after render
- **Font persistence**: Terminal font size saved to `localStorage('cs_term_font_size')`
- **Keyboard**: Enter in input field sends input (unless Shift held for multiline)
- **Output mode**: Session can be 'terminal' (default), 'chat' (OpenWebUI, etc.), or 'log' (ACP headless)

---

## Screen 3: New Session

**Route**: `navigate('new')`  
**Render function**: `renderNewSessionView()` (lines 2577–2695)  
**Header title**: "New Session"  
**Bottom nav**: New button active (purple)

### Layout Structure
```
┌────────────────────────────────────────────┐
│ New Session                               │
├────────────────────────────────────────────┤
│ Describe the coding task for the AI…      │
│                                            │
│ SESSION NAME                              │
│ [e.g. Auth refactor           ]           │
│                                            │
│ + Task description (optional)              │  ← Collapsible
│                                            │
│ LLM BACKEND                               │
│ [claude-code ▼]                           │
│ [Default (no profile) ▼]                  │
│ ⚠️ Backend not installed…                 │  ← Warning (conditional)
│                                            │
│ PROJECT DIRECTORY                         │
│ [~/  📁]                                  │
│                                            │
│ RESUME PREVIOUS SESSION (optional)        │
│ [Start fresh ▼]                           │
│                                            │
│ ☑ Auto git init  ☑ Auto git commit        │
│                                            │
│ [   Start Session   ]                     │
│                                            │
│ Restart a previous session                │
│ ┌──────────────────────────────┐          │
│ │ Opencode (killed) 52m        │ Restart  │
│ │ [51 failed in store…]        │          │
│ └──────────────────────────────┘          │
└────────────────────────────────────────────┘
```

### Fields Rendered

| Field | Label | Type | Config Key | Endpoint | Line | Required | Behavior |
|-------|-------|------|-----------|----------|------|----------|----------|
| **Session name** | "Session name" | text | session.name | — | 2587–2593 | No | e.g. "Auth refactor"; focused by default (touch-friendly check, line 2660) |
| **Task description** | "+ Task description (optional)" | textarea | session.task | — | 2595–2605 | Conditional* | Collapsible details; rows=5; Enter+Cmd submits form (line 2672) |
| **LLM backend** | "LLM backend" | select | session.llm_backend | GET /api/backends | 2607–2610 | Yes | Options from /api/backends; onChange updates prompt requirement & resume visibility (line 2852) |
| **Profile** | (subfield) | select | session.profile | GET /api/profiles | 2611–2613 | No | Optional profile override; loaded by `populateProfileDropdown()` (line 2697) |
| **Backend warning** | (dynamic) | html | — | — | 2614–2617 | N/A | Shows if backend not available; displays setup hint (line 2810–2824) |
| **Project directory** | "Project directory" | dir picker | session.project_dir | GET /api/dir-browse | 2620–2624 | No | Click to open directory browser; stores in `newSessionState.selectedDir` |
| **Directory browser** | (modal popup) | file tree | — | — | 2625–2627 | N/A | Modal showing directory structure; click to select; nested browsable |
| **Resume previous** | "Resume previous session (optional)" | select | — | — | 2628–2636 | No | Loads from completed sessions (last 30); onChange `handleResumeSelect()` (line 2738) auto-fills form |
| **Resume custom** | (hidden input) | text | — | — | 2633–2635 | No | Shows if "Custom session ID…" selected; allows manual ID entry |
| **Auto git init** | "Auto git init" | checkbox | session.auto_git_init | — | 2639–2640 | No | Toggleable; default from /api/config (line 2691) |
| **Auto git commit** | "Auto git commit" | checkbox | session.auto_git_commit | — | 2642–2643 | No | Checked by default; from /api/config (line 2691) |

\* Task is required if backend has `prompt_required=true`; otherwise optional (line 2862)

### Buttons & Actions

| Button | Label | Line | Onclick | Endpoint | Body |
|--------|-------|------|---------|----------|------|
| **Task details toggle** | "+ Task description (optional)" | 2596 | Click to expand/collapse | — | Toggles `open` attribute on `<details>` |
| **Backend selector** | Backend dropdown | 2852–2871 | onChange | — | Updates prompt requirement, resume visibility, loads warning hint |
| **Profile selector** | Profile dropdown | 2611–2613 | onChange | — | Direct form field |
| **Directory picker** | Project directory input | 2622 | onclick `openDirBrowser()` | GET /api/dir-browse | Async tree fetch + modal render |
| **Resume selector** | Dropdown | 2630 | onchange `handleResumeSelect()` | — | Auto-fills form fields from selected session (lines 2749–2775) |
| **Custom resume input** | Text field | 2634 | (shown on select "Custom…") | — | Manual session ID entry |
| **Resume cancel** | X button | 2635 | onclick | — | Hides custom input, clears selection |
| **Start Session** | [Start Session] | 2646 | onclick `submitNewSession()` | POST /api/sessions/create | `{name, task, llm_backend, project_dir, resume_id, auto_git_init, auto_git_commit}` |

### Dialogs

#### Directory Browser Modal (lines 2625–2627, openDirBrowser)
- **Trigger**: Click project directory input
- **Content**: Tree of directories from server
- **Endpoint**: GET /api/dir-browse (with optional path param)
- **Action**: Click directory → set `newSessionState.selectedDir` + update display

#### Backend Setup Hint (conditional warning, lines 2614–2617)
- **Shown when**: Backend not available and not already configured
- **Content**: Setup instruction text (from `backendSetupHint()` line 2810)
- **Example**: "Install Claude Code: npm install -g @anthropic-ai/claude-code…"

### Session Backlog / Restart Cards (lines 2648–2807)

#### Backlog List (lines 2650–2654)
- **Header**: "Restart a previous session"
- **Content**: Cards for last 20 completed sessions (sorted by updated_at desc)
- **Load**: `renderSessionBacklog()` (line 2680)

#### Backlog Card (lines 2789–2806)
| Element | Content | Line | Onclick |
|---------|---------|------|---------|
| **Task snippet** | Truncated name/task (60 chars) | 2797 | — |
| **State badge** | `sess.state` colored | 2798 | — |
| **Backend badge** | `sess.llm_backend` | 2801 | — |
| **Time ago** | `timeAgo(updated_at)` | 2802 | — |
| **Restart button** | [Restart] | 2804 | `restartSession(fullId)` → POST /api/sessions/restart |

### Data Source

| Feed | Type | Endpoint | Load Trigger | Line |
|------|------|----------|--------------|------|
| **Backends** | REST | GET /api/backends | On render (fetchBackends) | 2679, 2827 |
| **Profiles** | REST | GET /api/profiles | On render (populateProfileDropdown) | 2682, 2700 |
| **Session backlog** | Cached (state.sessions) | — | On render (renderSessionBacklog) | 2780–2807 |
| **Resume dropdown** | Cached (state.sessions filtered) | — | On render (populateResumeDropdown) | 2681, 2717–2735 |
| **Directory structure** | REST (tree) | GET /api/dir-browse | On demand (openDirBrowser) | — |
| **Git config defaults** | REST | GET /api/config | On render (line 2685) | 2684–2694 |

### Notable Behaviors

- **Dynamic requirement**: Task field becomes required if selected backend has `prompt_required: true`
- **Resume auto-fill**: Selecting a previous session auto-fills Name, Task, Directory, Backend from that session's data
- **Profile override**: Profiles are optional; selected profile prepends to the backend config
- **Directory browser**: Modal tree; click any directory to select (stores in `newSessionState.selectedDir`)
- **Enter key behavior**: In name field, Enter submits. In task field, Cmd+Enter submits (line 2672)
- **Touch-friendly focus**: Name input only auto-focuses on non-touch devices (line 2660–2661)
- **Collapsible task details**: Expands if backend requires prompt (line 2861)

---

## Screen 4: Alerts

**Route**: `navigate('alerts')`  
**Render function**: `renderAlertsView()` (lines 5516–5694)  
**Header title**: "Alerts"  
**Bottom nav**: Alerts button active (purple)

### Layout Structure
```
┌────────────────────────────────────────────┐
│ Alerts                                    │
├────────────────────────────────────────────┤
│ [Active (1)] [Inactive (9)]  [Refresh]    │  ← Tab buttons
├────────────────────────────────────────────┤
│ [datawatch-app] (sub-tab for active sess) │
│                                            │
│ datawatch-app | running | 107 alerts      │  ← Session header
│ ┌──────────────────────────────────────┐ │
│ │ INFO                            47m ago│ │
│ │ ring: datawatch-app [i7db]…          │ │
│ │ Prompt: go ahead and remove if it is │ │
│ │ not required -- grid | dropdown only │ │
│ │                                      │ │
│ │ [Quick reply dropdown ▼]             │ │
│ └──────────────────────────────────────┘ │
│ ┌──────────────────────────────────────┐ │
│ │ INFO                            47m ago│ │
│ │ ring: datawatch-app [i7db]…          │ │
│ │ …                                    │ │
│ └──────────────────────────────────────┘ │
│                                            │
│ (Inactive section, collapsed)              │
│ ▶ system | 3 alerts                       │
│ ▶ old-session | killed | 2 alerts         │
│                                            │
└────────────────────────────────────────────┘
```

### Fields Rendered

#### Top Tab Bar (lines 5676–5689)
| Button | Onclick | Line | Behavior |
|--------|---------|------|----------|
| **Active (N)** | `switchAlertTab('active')` | 5678 | Shows alerts for active sessions; count in label |
| **Inactive (N)** | `switchAlertTab('inactive')` | 5681 | Shows alerts for completed sessions + system; count in label |
| **Refresh** | `renderAlertsView()` | 5685 | Reload all alerts from /api/alerts |

#### Active Session Tabs (lines 5634–5651)
- **Sub-tabs** per active session (only one visible at a time)
- **Tab button**: Session name or ID
- **Onclick**: `switchAlertSessionTab(idx, total)` (line 5696)

#### Alert Card (lines 5568–5592)
| Element | Content | Line | Type | Behavior |
|---------|---------|------|------|----------|
| **Level badge** | Level (INFO/WARN/ERROR) | 5585 | text | Color-coded: error=red, warn=orange, info=grey |
| **Time stamp** | `timeAgo(a.created_at)` | 5586 | text | Relative time (e.g., "47m ago") |
| **Title** | `a.title` | 5588 | text | Bold, monospace |
| **Body** | `a.body` | 5589 | text | Multi-line (ANSI stripped) |
| **Quick reply** | Select dropdown | 5580 | conditional | Only for first alert in waiting_input session; options from /api/commands |
| **Quick reply options** | Command list | 5576–5580 | options | Pre-populated saved commands; onChange calls `alertSendCmd()` (line 5724) |

#### Session Group Header (collapsed, lines 5594–5623)
| Element | Content | Line |
|---------|---------|------|
| **Session link** | Name or ID (clickable) | 5599 | Navigate to session detail |
| **State badge** | Session state | 5600 | Color-coded (waiting=warning, running=success) |
| **Alert count** | "N alert(s)" | 5601 | Descriptive text |
| **Toggle icon** | ▶ / ▼ chevron | 5606 | Click to expand/collapse section |

#### System Alert Group (lines 5662–5672)
- **Header**: "System" (no session)
- **Content**: Alerts with no session_id
- **Collapsed by default** (like inactive sessions)

### Buttons & Actions

| Button | Label | Line | Onclick | Endpoint | Body |
|--------|-------|------|---------|----------|------|
| **Active tab** | "Active (N)" | 5678 | `switchAlertTab('active')` | — | Show active panel, hide inactive |
| **Inactive tab** | "Inactive (N)" | 5681 | `switchAlertTab('inactive')` | — | Show inactive panel, hide active |
| **Refresh** | "Refresh" | 5685 | `renderAlertsView()` | GET /api/alerts | Re-fetch and re-render all alerts |
| **Session tab** | Session name (in active tab) | 5640 | `switchAlertSessionTab(idx, total)` | — | Switch between active session alert panels |
| **Session link** | Name/ID (in group header) | 5599 | `navigate('session-detail', sessID)` | — | Navigate to session detail view |
| **Toggle section** | Chevron (group header) | 5606 | Click div → toggle display:none | — | Show/hide collapsed section |
| **Quick reply** | Command dropdown | 5580 | onchange `alertSendCmd(sessID, cmd)` | POST /api/command | `{text: 'send {shortId}: {cmd}'}` |

### Dialogs / Modals

None for this screen. Alerts are inline cards.

### Data Source

| Feed | Type | Endpoint | Load Trigger | Line |
|------|------|----------|--------------|------|
| **Alerts** | REST | GET /api/alerts | On render (line 5522) | 5516–5694 |
| **Commands** | REST | GET /api/commands | On render (line 5523) | 5576–5580 |
| **Sessions (fresh)** | REST | GET /api/sessions | On render (line 5524) | 5525–5529 |

### Notable Behaviors

- **Auto-mark-read**: Immediately after load, POST /api/alerts with `{all: true}` to mark all as read (line 5539)
- **Active/inactive grouping**: Sessions in terminal states (complete/failed/killed) are "inactive"
- **System alerts**: Alerts with no session_id grouped separately at bottom of inactive section
- **Sub-tabs for active sessions**: Only active sessions show as separate tabs; each tab shows its alerts
- **Quick reply shortcut**: Only first alert in a waiting_input session gets the quick-reply dropdown
- **Badge counting**: Badge at bottom nav shows `state.alertUnread` count; hidden if 0

---

## Screen 5: Settings → Monitor

**Route**: `navigate('settings')` then `switchSettingsTab('monitor')`  
**Render function**: Part of `renderSettingsView()` (lines 3238–3305 define Monitor section)  
**Rendering sections**: `loadStatsPanel()` (line 5737), `renderStatsData()` (lines 5804–6100)  
**Header title**: "Settings"  
**Tab active**: Monitor

### Layout Structure
```
┌────────────────────────────────────────────┐
│ Settings                                  │
├────────────────────────────────────────────┤
│ [Monitor] [General] [Comms] [LLM] [About] │
├────────────────────────────────────────────┤
│ System Statistics                          │
│ ┌──────────────────────────────────────┐ │
│ │ CPU Load          2.1 / 4 cores   ▮▮ │
│ │ Memory            1.2 / 16 GB      ▮ │
│ │ Disk              50 / 256 GB     ▮▮ │
│ │ GPU RTX 3080      78% 62°C         ▮ │
│ │ Network (system)                   │ │
│ │  ↓ Download  125 MB                │ │
│ │  ↑ Upload    45 MB                 │ │
│ │ Daemon                             │ │
│ │  Memory    256 MB RSS              │ │
│ │  Goroutines 345                    │ │
│ │ Infrastructure                     │ │
│ │  HTTP  http://0.0.0.0:8080         │ │
│ │  HTTPS https://0.0.0.0:8443 🔒    │ │
│ │  Tmux  5 sessions (1 orphan)       │ │
│ └──────────────────────────────────────┘ │
│                                            │
│ eBPF (per-process net)                    │
│ ● live — per-process net wired            │
│                                            │
│ Installed plugins                         │
│ ● my-plugin v1.0 · enabled · hook_x      │ │
│                                            │
│ Session Statistics                        │
│ [active: 1 / 10 max]  Donut chart        │
│                                            │
│ Orphaned Tmux Sessions                    │
│ [tmux_session_1] [Kill All Orphaned]     │
│                                            │
│ Episodic Memory                          │
│ Status       enabled                      │
│ Backend      sqlite                       │
│ Encryption   plaintext                    │
│ Total        42 memories                  │
│                                            │
│ Ollama Server                             │
│ Status       online                       │
│ Models       3                            │
│ Running      mistral, neural-chat        │
│ VRAM Used    16 GB                        │
│                                            │
│ Sessions                                 │
│ ▼ daemon (running)    256 MB · 4h 32m    │
│   Backend: daemon                         │
│   Memory: 256 MB · CPU: 12%               │
│ ▶ i7db (running)      180 MB · 47m       │
│                                            │
│ Communication Channels (if any)           │
│ ▼ signal-device1 (linked)    3 msgs      │
│ ▶ telegram-bot (connected)   12 msgs     │
│                                            │
│ Memory Browser                            │
│ [Search memories...] [Manual] [7 days]   │
│ [Search] [List] [Export]                 │
│ [Memory results list…]                   │
│                                            │
│ Scheduled Events                          │
│ [List of pending schedules]              │
│                                            │
│ Daemon Log                               │
│ [Monospace log output, scrollable]       │
│ [Newest] [Older] (showing N of M)        │
│                                            │
└────────────────────────────────────────────┘
```

### Stat Cards (lines 5826–5926)

#### CPU Load (line 5828)
- **Label**: "CPU Load"
- **Value**: `data.cpu_load_avg_1.toFixed(2)` / `data.cpu_cores` cores
- **Bar**: Progress bar, color based on percentage (green <50%, orange 50–80%, red >80%)

#### Memory (line 5831)
- **Label**: "Memory"
- **Value**: `fmt(data.mem_used)` / `fmt(data.mem_total)`
- **Color logic**: Red if >85%, else purple

#### Disk (line 5833)
- **Label**: "Disk"
- **Value**: `fmt(data.disk_used)` / `fmt(data.disk_total)`
- **Color logic**: Red if >90%, else purple

#### Swap (line 5835, conditional)
- **Label**: "Swap"
- **Shown**: Only if `data.swap_total > 0`
- **Color**: Orange (warning)

#### GPU (lines 5837–5839, conditional)
- **Label**: "GPU {name}"
- **Value**: `data.gpu_util_pct`% + `data.gpu_temp`°C
- **Color**: Red if >80%, else green
- **VRAM bar** (if available)

#### Network (lines 5841–5847)
- **Label**: "Network (datawatch)" or "Network (system)" depending on eBPF status
- **Stats**:
  - ↓ Download: `fmt(data.net_rx_bytes)`
  - ↑ Upload: `fmt(data.net_tx_bytes)`

#### Daemon (lines 5848–5857)
- **Stats**:
  - Memory: `fmt(data.daemon_rss_bytes)` RSS
  - Goroutines: `data.goroutines`
  - File descriptors: `data.open_fds`
  - Uptime: Formatted hours/minutes/seconds

#### Infrastructure (lines 5858–5870)
- **Stats**:
  - HTTP: `http://{host}:{port}`
  - HTTPS: `https://{host}:{port}` (with lock icon if TLS enabled)
  - MCP SSE: (if configured)
  - Tmux: `{count}` sessions (+ orphan count if any)

#### RTK Token Savings (lines 5871–5884, conditional)
- **Label**: "RTK Token Savings"
- **Stats** (if `data.rtk_installed`):
  - Version: With update indicator if available
  - Hooks: active/inactive
  - Tokens saved: Total count
  - Avg savings: Percentage
  - Commands: Count of RTK-optimized commands

#### Episodic Memory (lines 5886–5905)
- **Label**: "Episodic Memory"
- **Stats**:
  - Status: enabled/disabled
  - Backend: sqlite/postgres
  - Embedder: ollama/openai/—
  - Encryption: encrypted/plaintext
  - Total count, manual count, sessions, learnings, DB size

#### Ollama Server (lines 5906–5925, conditional)
- **Shown**: If `data.ollama_stats` present
- **Stats**:
  - Host, Status, Model count, Disk used, Running models
  - Per-model VRAM line items

### Session Statistics Card (lines 5928–6001)

#### Active Sessions Donut (lines 5930–5941)
- **Donut chart**: Conic gradient (active color vs border color)
- **Center text**: Active count
- **Label**: "{active} of {max} max"

#### Orphaned Tmux (lines 5942–5953, conditional)
- **Shown**: If `data.orphaned_tmux.length > 0`
- **Content**: List of session names
- **Button**: "Kill All Orphaned" → `killOrphanedTmux()`
- **Copy hint**: "tmux attach -t {name}"

#### eBPF Status (lines 5954–5962)
- **Notice**: Degraded/active status with message
- **Color**: Warning if degraded, success if active

#### Expandable Sessions List (lines 5964–6000)
| Element | Content | Line | Behavior |
|---------|---------|------|----------|
| **Session row** | Name, state badge, memory, uptime | 5974–5986 | Click to expand/collapse details |
| **Chevron** | ▶/▼ | 5981 | Toggles `_expandedSessions.has(sid)` |
| **Details row** | Backend, PID, Memory, CPU%, Network | 5987–5993 | Visible only if expanded; shows eBPF network stats if available |

#### Sessions in Store Link (lines 5996–5999)
- **Text**: "{total} sessions in store"
- **Link**: Click → navigate to sessions view with history shown
- **Color**: Purple accent

### eBPF Status Block (lines 3242–3246, 5753–5775)
- **Label**: "eBPF (per-process net)"
- **Loading text**: "Loading…" initially
- **Loaded content**: 
  - Dot + status: "live — per-process net wired" (green) or "configured + capability granted" (purple) or "configured but capability missing" (orange) or "off" (grey)
  - Message line (if available)
- **Endpoint**: GET /api/stats?v=2
- **Load function**: `loadEBPFStatus()` (line 5753)

### Plugins Status Block (lines 3247–3251, 5777–5799)
- **Label**: "Installed plugins"
- **Loading text**: "Loading…"
- **Content**: Per-plugin row with enabled/disabled dot, name, version, hooks, invoke count, last error
- **Endpoint**: GET /api/plugins
- **Load function**: `loadPluginsStatus()` (line 5779)
- **Fallback**: If plugins disabled, shows "none installed · plugin docs" link

### Memory Browser (lines 3263–3286)
| Element | Type | Line | Endpoint | Behavior |
|---------|------|------|----------|----------|
| **Search input** | text | 3266 | — | Text search in memory content |
| **Role filter** | select | 3267 | — | All/Manual/Session/Learning/Chunks |
| **Time filter** | select | 3274 | — | All time / 7 days / 30 days / 90 days |
| **Search button** | button | 3280 | GET /api/memories/search | Query with filters |
| **List button** | button | 3281 | GET /api/memories | Fetch all memories |
| **Export button** | button | 3282 | GET /api/memories (JSON download) | Download backup |
| **Results area** | scrollable list | 3284 | — | Display search/list results |

### Schedules List (lines 3288–3293)
- **Label**: "Scheduled Events"
- **Load function**: `loadSchedulesList()` (line 3420)
- **Content**: Pending scheduled commands, with cancel buttons per schedule

### Daemon Log (lines 3295–3305)
| Element | Type | Line | Behavior |
|---------|------|------|----------|
| **Log panel** | monospace text | 3298 | Displays last 50 lines; max-height with scroll |
| **Newest button** | button | 3300 | onclick `loadDaemonLog(0)` → Fetch latest 50 lines |
| **Older button** | button | 3301 | onclick `loadDaemonLog((offset)+50)` → Fetch earlier lines |
| **Info text** | text | 3302 | "Showing N of M lines (offset X)" |

### Buttons & Actions

| Button | Label | Line | Onclick | Endpoint | Behavior |
|--------|-------|------|---------|----------|----------|
| **Refresh (manual)** | N/A (auto-updates) | — | — | — | Stats loaded on render; auto-refresh every 10s (line 5714–5718) when on monitor tab |
| **Kill Orphaned** | "Kill All Orphaned" | 5951 | `killOrphanedTmux()` | POST /api/tmux/kill-orphaned | Removes orphaned tmux sessions |
| **Session expand** | Chevron (▶/▼) | 5980 | onclick | — | Toggle `_expandedSessions` Set |
| **Sessions link** | "{count} sessions in store" | 5998 | navigate + setHistory | — | Go to Sessions view with history shown |
| **Search memories** | "Search" | 3280 | `searchMemories()` | GET /api/memories/search?query=...&role=...&since=... | Filter memories by text + role + date |
| **List memories** | "List" | 3281 | `listMemories()` | GET /api/memories?limit=100 | Fetch all memories with pagination |
| **Export memories** | "Export" | 3282 | `exportMemories()` | GET /api/memories (download) | Download as JSON file |
| **Daemon log newest** | "Newest" | 3300 | `loadDaemonLog(0)` | GET /api/logs?lines=50&offset=0 | Fetch latest log lines |
| **Daemon log older** | "Older" | 3301 | `loadDaemonLog((offset)+50)` | GET /api/logs?lines=50&offset=X | Fetch earlier log lines |

### Data Source

| Feed | Type | Endpoint | Load Trigger | Auto-Refresh | Line |
|------|------|----------|--------------|------------------|------|
| **System stats** | REST | GET /api/stats | On render | Every 10s (when monitor tab active) | 5737–5747, 5714–5718 |
| **eBPF status** | REST | GET /api/stats?v=2 | On render (line 5756) | — | 5753–5775 |
| **Plugins** | REST | GET /api/plugins | On render (line 5782) | — | 5777–5799 |
| **Memories** | REST | GET /api/memories* | On demand (search/list buttons) | — | 3280–3284 |
| **Schedules** | REST | GET /api/schedules | On render (line 3420) | — | 3288–3293 |
| **Daemon log** | REST | GET /api/logs | On demand (newest/older buttons) | — | 3298–3305 |

### Notable Behaviors

- **Auto-refresh stats**: Every 10s when monitor tab visible (line 5714–5718)
- **Stat card grid**: Responsive grid layout (auto-fit columns, min 180px)
- **Color coding**: CPU/memory/disk bars change color based on utilization thresholds
- **Scroll preservation**: Stats panel preserves scroll position during updates (line 5807–5809)
- **Session expand/collapse**: Toggled via `_expandedSessions` Set (line 5977)
- **Daemon log pagination**: Offset-based; shows current range
- **eBPF + plugins**: Lazy-loaded; show "Loading…" initially

---

## Screen 6: Settings → General

**Route**: `navigate('settings')` then `switchSettingsTab('general')`  
**Render function**: Part of `renderSettingsView()` (lines 3195–3234 define General sections)  
**Config load**: `loadGeneralConfig()` (line 3427)  
**Header title**: "Settings"  
**Tab active**: General

### Layout Structure
```
┌────────────────────────────────────────────┐
│ Settings                                  │
├────────────────────────────────────────────┤
│ [Monitor] [General] [Comms] [LLM] [About] │
├────────────────────────────────────────────┤
│ Datawatch                                 │
│ ▶ Log level              [debug ▼]        │
│ ▶ Auto-restart on config save  [toggle]   │
│ ▶ Default LLM backend         [select ▼]  │
│                                            │
│ Auto-Update                               │
│ ▶ Enabled                      [toggle]   │
│ ▶ Schedule                  [hourly ▼]    │
│ ▶ Time of day (HH:MM)           [text]    │
│                                            │
│ Session                                  │
│ ▶ Max concurrent sessions      [number]   │
│ ▶ Input idle timeout (sec)     [number]   │
│ ▶ Tail lines                   [number]   │
│ ▶ Alert context lines          [number]   │
│ ▶ Default project dir      [dir picker]   │
│ ▶ File browser root path   [dir picker]   │
│ ▶ Default console width    [number cols]  │
│ ▶ Default console height   [number rows]  │
│ ▶ Recent session visibility    [number]   │
│ ▶ Claude skip permissions      [toggle]   │
│ ▶ Claude channel mode          [toggle]   │
│ ▶ Auto git init                [toggle]   │
│ ▶ Auto git commit              [toggle]   │
│ ▶ Kill sessions on exit        [toggle]   │
│ ▶ MCP auto-retry limit         [number]   │
│ ▶ Scheduled command settle (ms) [number]  │
│ ▶ Default effort         [text: quick/...] │
│ ▶ Suppress toasts for active session [toggle] │
│                                            │
│ [More collapsible sections below: RTK,   │
│  Pipelines, Autonomous, Plugins,         │
│  Orchestrator, Whisper, Project Profiles, │
│  Cluster Profiles, Notifications]        │
│                                            │
└────────────────────────────────────────────┘
```

### Collapsible Sections (lines 3195–3201, config built from GENERAL_CONFIG_FIELDS)

| Section | id | Fields in Section | Read Endpoint | Write Endpoint | Collapse Behavior |
|---------|----|--------------------|---------------|-----------------|------------------|
| **Datawatch** | gc_dw | log_level, auto_restart_on_config, llm_backend | GET /api/config | PUT /api/config | Collapsible (stored in `settingsCollapsed`) |
| **Auto-Update** | gc_autoupdate | enabled, schedule, time_of_day | GET /api/config | PUT /api/config | Collapsible |
| **Session** | gc_sess | (14 fields) max_sessions, idle_timeout, tail_lines, etc. | GET /api/config | PUT /api/config | Collapsible |
| **RTK** | gc_rtk | enabled, binary, show_savings, auto_init, discover_interval | GET /api/config | PUT /api/config | Collapsible |
| **Pipelines** | gc_pipeline | max_parallel, default_backend | GET /api/config | PUT /api/config | Collapsible |
| **Autonomous** | gc_autonomous | enabled, poll_interval, max_parallel_tasks, backends, retries, security_scan | GET /api/config | PUT /api/config | Collapsible |
| **Plugin Framework** | gc_plugins | enabled, dir, timeout_ms | GET /api/config | PUT /api/config | Collapsible |
| **Orchestrator** | gc_orchestrator | enabled, guardrail_backend, timeout, max_parallel_prds | GET /api/config | PUT /api/config | Collapsible |
| **Voice Input (Whisper)** | gc_whisper | enabled, model, language, venv_path | GET /api/config | PUT /api/config | Collapsible |
| **Project Profiles** | gc_projectprofiles | (dynamic list) | GET /api/profiles | — | Collapsible; renders via loadProjectProfiles() (line 3429) |
| **Cluster Profiles** | gc_clusterprofiles | (dynamic list) | GET /api/profiles | — | Collapsible; renders via loadClusterProfiles() (line 3430) |
| **Notifications** | gc_notifs | Status + Request Permission button | Notification.permission (browser API) | — | Collapsible |

### Field Types & Rendering (from GENERAL_CONFIG_FIELDS, lines 3586–3658)

| Type | HTML Element | Example | Save Mechanism |
|------|--------------|---------|-----------------|
| **toggle** | `<label class="toggle-switch"><input type="checkbox"></label>` | Auto-restart, Channel mode | onchange → `saveGeneralField(key, checked)` |
| **select** | `<select class="form-select"><option>...` | Log level (info/debug/warn/error) | onchange → `saveGeneralField(key, value)` |
| **number** | `<input type="number">` | Max sessions (10), timeout (300) | onchange → `saveGeneralField(key, value)` |
| **text** | `<input type="text">` | Time of day (HH:MM), effort (quick/normal/thorough) | onchange → `saveGeneralField(key, value)` |
| **dir_browse** | `<input type="text">` + 📁 icon | Default project dir, root path | Click → directory browser modal; stores selected path |
| **llm_select** | `<select>` | Session default LLM backend | onchange → `saveGeneralField(key, value)` |

### Buttons & Actions

| Button | Label | Section | Line | Onclick | Endpoint | Body |
|--------|-------|---------|------|---------|----------|------|
| **Section toggle** | Chevron (▶/▼) | All sections | — | Click header | — | Toggle `settingsCollapsed[id]` (line 3086) |
| **All field changers** | onchange | All sections | — | `saveGeneralField(key, value)` | PUT /api/config | `{key: value, ...}` (single field) |
| **Directory picker** | 📁 (dir_browse type) | Session, Project, etc. | — | `openDirBrowser(key)` | GET /api/dir-browse | Modal tree selector |
| **Request notification permission** | "Request Permission" | Notifications | 3230 | `requestNotificationPermission()` | — | Browser Notification.requestPermission() |

### Data Source

| Feed | Type | Endpoint | Load Trigger | Line |
|------|------|----------|--------------|------|
| **General config** | REST | GET /api/config | On render (line 3427) | 3195–3301 |
| **Project profiles** | REST | GET /api/profiles | On render (line 3429) | 3204–3210 |
| **Cluster profiles** | REST | GET /api/profiles | On render (line 3430) | 3213–3219 |
| **Browser notification permission** | Browser API | N/A | On render | 3080–3084 |

### Notable Behaviors

- **Collapse state persistence**: `settingsCollapsed` object maintains expand/collapse state across renders
- **Auto-save**: Each field onChange calls `saveGeneralField()` immediately (no manual Save button per section)
- **Field validation**: Number fields validated by HTML5 `<input type="number">`
- **Directory picker**: Modal overlay; click to select directory; stores in `newSessionState.selectedDir` (reused from New Session)
- **Notification permission**: Shows browser permission status + button to request if not granted yet
- **Profile lists**: Dynamic; fetched from /api/profiles; rendered as drag-droppable cards in Project/Cluster sections

---

## Screen 7: Settings → Comms

**Route**: `navigate('settings')` then `switchSettingsTab('comms')`  
**Render function**: Part of `renderSettingsView()` (lines 3100–3177 define Comms sections)  
**Config load**: `loadCommsConfig()` (line 3408, lines 3720–3800)  
**Header title**: "Settings"  
**Tab active**: Comms

### Layout Structure
```
┌────────────────────────────────────────────┐
│ Settings                                  │
├────────────────────────────────────────────┤
│ [Monitor] [General] [Comms] [LLM] [About] │
├────────────────────────────────────────────┤
│ Authentication                            │
│ ▼ Browser token                           │
│  [••••••••••]          [Save & Reconnect] │
│  Server bearer token     [••••••••••]     │
│  MCP SSE bearer token    [••••••••••]     │
│                                            │
│ Servers                                  │
│ ▼ Status                 ● Connected      │
│  This server             localhost:8443   │
│  [Pending (refreshing…)]                  │
│                                            │
│ Web Server                                │
│ ▼ Enabled                      [toggle]   │
│  Bind interface         [0.0.0.0 ▼]       │
│  Port                       [8080]        │
│  TLS enabled                [toggle]      │
│  TLS port                   [8443]        │
│  TLS auto-generate cert     [toggle]      │
│  TLS cert path              [/path]       │
│  TLS key path               [/path]       │
│  Install cert on phone      [⬇️ Links]   │
│   ├ Download CA Certificate (.crt)       │
│   ├ PEM format                            │
│   └ [Details: Android/iPhone steps]       │
│  Channel port (0=random)    [0]           │
│                                            │
│ MCP Server                                │
│ ▼ Enabled (stdio)           [toggle]      │
│  SSE enabled (HTTP)         [toggle]      │
│  SSE bind interface     [0.0.0.0 ▼]       │
│  SSE port                  [8081]        │
│  TLS enabled                [toggle]      │
│  TLS auto-generate cert     [toggle]      │
│  TLS cert path              [/path]       │
│  TLS key path               [/path]       │
│                                            │
│ Proxy Resilience                          │
│ ▼ [Proxy resilience settings]             │
│                                            │
│ Communication Configuration               │
│ ▼ Signal Device                           │
│  [Checking…]  [Link Device] / [QR Code]  │
│  [QR code area (hidden when no linking)]  │
│                                            │
│ [More: channels/backends configured]     │
│                                            │
└────────────────────────────────────────────┘
```

### Collapsible Sections (lines 3100–3177, from COMMS_CONFIG_FIELDS)

| Section | id | Fields | Collapse Behavior |
|---------|----|---------|--------------------|
| **Authentication** | comms_auth | Browser token, server token, MCP token | Collapsible |
| **Servers** | servers | Status, this server address, remote server list | Collapsible |
| **Web Server** | cc_websrv | enabled, host, port, TLS, cert paths, channel port | Collapsible |
| **MCP Server** | cc_mcpsrv | enabled (stdio), SSE enabled, SSE config, TLS | Collapsible |
| **Proxy Resilience** | proxy | (configurable) retry count, timeout, etc. | Collapsible |
| **Communication Configuration** | backends | Signal device linking + QR code, Telegram/Discord/Slack/Matrix backends | Collapsible |

### Fields & Configuration Sections

#### Authentication (lines 3101–3120)
| Field | Type | Config Key | Line | Behavior |
|-------|------|-----------|------|----------|
| **Browser token** | password input + button | session token (localStorage) | 3106–3107 | Shows current token; "Save & Reconnect" button calls `saveToken()` (line 3107) to update localStorage + reconnect WS |
| **Server bearer token** | password input | server.token | 3112–3113 | Auto-save onchange via `saveGeneralField()` |
| **MCP SSE bearer token** | password input | mcp.token | 3117–3118 | Auto-save onchange via `saveGeneralField()` |

#### Servers (lines 3123–3139)
| Field | Content | Line | Load Function |
|-------|---------|------|----------------|
| **Connection status** | Dot (green/red) + "Connected"/"Disconnected" | 3128–3130 | `loadLinkStatus()` (refreshes on render) |
| **This server** | Read-only address | 3135 | `location.host` |
| **Remote servers list** | Dynamic div | 3137 | `loadServers()` (line 3407) — fetches /api/servers |

#### Web Server (lines 3543–3573, from COMMS_CONFIG_FIELDS)
| Field | Type | Config Key | Line | Auto-save |
|-------|------|-----------|------|-----------|
| **Enabled** | toggle | server.enabled | 3544 | ✓ |
| **Bind interface** | interface_select dropdown | server.host | 3545 | ✓ (fetches /api/interfaces) |
| **Port** | number | server.port | 3546 | ✓ |
| **TLS enabled** | toggle | server.tls | 3547 | ✓ |
| **TLS port** | number | server.tls_port | 3548 | ✓ |
| **TLS auto-generate cert** | toggle | server.tls_auto_generate | 3549 | ✓ |
| **TLS cert path** | text | server.tls_cert | 3550 | ✓ |
| **TLS key path** | text | server.tls_key | 3551 | ✓ |
| **Install cert on phone** | html (links + details) | _tls_install | 3552–3571 | N/A (download links: /api/cert?format=der, /api/cert) |
| **Channel port** | number (0=random) | server.channel_port | 3572 | ✓ |

#### MCP Server (lines 3574–3583)
| Field | Type | Config Key | Auto-save |
|-------|------|-----------|-----------|
| **Enabled (stdio)** | toggle | mcp.enabled | ✓ |
| **SSE enabled (HTTP)** | toggle | mcp.sse_enabled | ✓ |
| **SSE bind interface** | interface_select | mcp.sse_host | ✓ |
| **SSE port** | number | mcp.sse_port | ✓ |
| **TLS enabled** | toggle | mcp.tls_enabled | ✓ |
| **TLS auto-generate cert** | toggle | mcp.tls_auto_generate | ✓ |
| **TLS cert path** | text | mcp.tls_cert | ✓ |
| **TLS key path** | text | mcp.tls_key | ✓ |

#### Proxy Resilience (lines 3150–3155)
- **ID**: `settings-sec-proxy`
- **Content**: Dynamically loaded by `loadProxySettings()` (line 3409)
- **Fields**: Likely retry counts, timeouts, fallback behavior

#### Communication Configuration / Signal Linking (lines 3157–3177)
| Element | Type | Line | Behavior |
|---------|------|------|----------|
| **Signal Device status** | Text + buttons | 3164 | Shows "Checking…" / "Linked" / "Not linked"; loaded by `loadLinkStatus()` |
| **Link Device button** | button | 3165 | onclick `startLinking()` (line 3165) → QR code appears |
| **QR code area** | canvas (hidden by default) | 3168–3175 | Shows when linking; populated by `startLinking()` |
| **QR instructions** | Text | 3171–3173 | Explains: "Open Signal on your phone → Settings → Linked Devices → Link New Device" |

### Backend Configuration Popups

For each communication backend (Telegram, Discord, Slack, Matrix, Email, Twilio, GitHub Webhook, Webhook, Signal, DNS Channel, Ntfy, etc.), there's a modal dialog opened via `openBackendSetup()` (line 4304) or embedded in Comms tab.

**Backend fields** (from BACKEND_FIELDS, lines 4290–4302):
- Telegram: token, chat_id, auto_manage_group
- Discord: token, channel_id, auto_manage_channel
- Slack: oauth_token, channel_id, auto_manage_channel
- Matrix: homeserver URL, user_id, access_token, room_id, auto_manage
- Email: host, port, username, password, from, to
- Twilio: account_sid, auth_token, from_number, to_number, webhook_addr
- GitHub Webhook: listen address, webhook secret
- Webhook: listen address, token
- Signal: group_id (base64), config_dir, device_name
- DNS Channel: mode, domain, listen, upstream, secret, TTL, response size, poll interval, rate limit
- Ntfy: server_url, topic, token

### Configure Dialog (lines 4317–4417, showBackendConfigPopup)

**Trigger**: Click "Configure" button next to backend name  
**Modal structure**:
```
┌────────────────────────────────────┐
│ Configure {backend-name}        ✕  │
├────────────────────────────────────┤
│ [Field 1: Label] [Input or select] │
│ [Field 2: Label] [Toggle switch]   │
│ [Field 3: Label] [Password input]  │
│ ...                                │
├────────────────────────────────────┤
│ [Test & Load Models] [Save & Enable] [Cancel] │
└────────────────────────────────────┘
```

**Field types in popup**:
- text / password / number / checkbox / select / ollama_model_select / openwebui_model_select

**Buttons**:
- **Test & Load Models** (if model fields present): `testBackendConnection(service)` (line 4419)
- **Save & Enable**: `saveBackendConfig(service)` (line 4473)
- **Cancel**: `closeBackendConfigPopup()` (line 4468)

**Model loading**: Async fetch from /api/ollama/models or /api/openwebui/models (line 4396)

### Buttons & Actions

| Button | Label | Line | Onclick | Endpoint | Body |
|--------|-------|------|---------|----------|------|
| **Save & Reconnect** | (auth section) | 3107 | `saveToken()` | — | Update localStorage + reconnect WebSocket |
| **Download CA Cert (.crt)** | Link (⬇️) | 3554 | Download | GET /api/cert?format=der | Self-signed CA certificate (DER format) |
| **Download CA Cert (PEM)** | Link | 3555 | Download | GET /api/cert | Self-signed CA certificate (PEM format) |
| **Install instructions** | `<details>` | 3556–3570 | Click to expand | — | Step-by-step for Android/iOS cert installation |
| **Link Device** | Button | 3165 | `startLinking()` | POST /api/comms/signal/link | Initiate QR code linking process |
| **Configure {backend}** | Button (per backend) | — | `openBackendSetup(name)` | GET /api/config | Load config for popup |
| **Test & Load Models** | (in popup) | 4372 | `testBackendConnection()` | GET /api/ollama/models or /api/openwebui/models | Validate connection + fetch model list |
| **Save & Enable** | (in popup) | 4373 | `saveBackendConfig()` | PUT /api/config | Save all field values + set enabled:true |
| **Cancel** | (in popup) | 4374 | `closeBackendConfigPopup()` | — | Close modal; discard changes |

### Data Source

| Feed | Type | Endpoint | Load Trigger | Line |
|------|------|----------|--------------|------|
| **Config** | REST | GET /api/config | On render (line 3408) | 3720–3800 |
| **Interfaces** | REST | GET /api/interfaces | On render (line 3723) | 3737–3738 |
| **Link status** | REST | GET /api/comms/signal/status | On render (line 3405) | loadLinkStatus() |
| **Servers list** | REST | GET /api/servers | On render (line 3407) | loadServers() |
| **Proxy settings** | REST | — (todo) | On render (line 3409) | loadProxySettings() |

### Notable Behaviors

- **Auto-collapse on save**: Some config changes may auto-restart daemon (if `state.autoRestartOnConfig` set)
- **Token masking**: Password fields show previous value as masked; placeholder "(configured — enter to change)" if value is "***"
- **Interface selector**: Dynamically populated from /api/interfaces (e.g., eth0, wlan0, docker0)
- **QR code linking**: Appears/disappears based on linking state
- **Backend popup fields**: Dynamically rendered based on BACKEND_FIELDS or LLM_FIELDS mapping
- **Model auto-load**: If model_select field detected, auto-fetches models on popup open (if host configured)

---

## Screen 8: Settings → LLM

**Route**: `navigate('settings')` then `switchSettingsTab('llm')`  
**Render function**: Part of `renderSettingsView()` (lines 3179–3342 define LLM sections)  
**Config load**: `loadLLMConfig()` (line 3425), `loadLLMTabConfig()` (line 3426), `loadDetectionFilters()` (line 3422)  
**Header title**: "Settings"  
**Tab active**: LLM

### Layout Structure
```
┌────────────────────────────────────────────┐
│ Settings                                  │
├────────────────────────────────────────────┤
│ [Monitor] [General] [Comms] [LLM] [About] │
├────────────────────────────────────────────┤
│ LLM Configuration                         │
│ ▼ claude-code v4.0.5      (default)       │
│   [✎ Configure] [Toggle: enabled]         │
│  ollama                    not configured  │
│   [Configure] [Toggle: disabled]          │
│  aider v0.21               enabled        │
│   [✎ Configure] [Toggle: enabled]         │
│  …                                        │
│  Note: Toggle enables/disables. Default… │
│                                            │
│ Episodic Memory                           │
│ ▼ Enabled                      [toggle]   │
│  Backend              [sqlite/postgres]   │
│  Embedder         [ollama/openai dropdown] │
│  Embedder model        [nomic-embed-text] │
│  Embedder host     [localhost:11434]      │
│  Search results (top-K)     [10]          │
│  Auto-save summaries        [toggle]      │
│  …(14 total fields)…                      │
│                                            │
│ RTK (Token Savings)                       │
│ ▼ Enable RTK integration       [toggle]   │
│  RTK binary path               [rtk]      │
│  Show savings in stats         [toggle]   │
│  …                                        │
│                                            │
│ Detection Filters                         │
│ ▼ [Filter list with pattern + action]    │
│  Add pattern: [regex] [action dropdown]   │
│                                            │
│ Saved Commands                            │
│ ▼ [Command list: name + text]             │
│  Add: [name] [command text] [Save]        │
│                                            │
│ Output Filters                            │
│ ▼ [Filter list: pattern + action]         │
│  Add: [pattern] [action] [value]          │
│                                            │
└────────────────────────────────────────────┘
```

### LLM Configuration List (lines 3179–3184, 3444–3492)

**Loaded by**: `loadLLMConfig()` (line 3444)

For each backend in /api/backends response:

| Element | Type | Line | Behavior |
|---------|------|------|----------|
| **Backend name** | Text | 3476–3478 | Bold; shows version (if available) |
| **Default indicator** | Label | 3478 | Shows "(default)" if this backend is active |
| **Configure button** | Icon ✎ (pencil) | 3480 | onclick `openLLMSetup(name)` → Popup with LLM_FIELDS for that backend |
| **Enable/disable toggle** | Toggle switch | 3481–3484 | onchange `toggleLLM(cfgKey, enabled, name)` → PUT /api/config |
| **Not configured state** | Text + button | 3467–3471 | If backend available but not enabled, show "not configured" + "Configure" button |
| **Help text** | Small text | 3486–3488 | "Toggle enables/disables backends. The (default) backend is used for new sessions…" |

### LLM Backend Configuration Popup (lines 4262–4417)

**Trigger**: Click "Configure" button next to backend name  
**Load function**: `openLLMSetup(name)` (line 3510)

**Fields** (from LLM_FIELDS, lines 4263–4282):

#### Claude Code Backend
- claude_enabled (checkbox)
- skip_permissions (checkbox)
- channel_enabled (checkbox)
- fallback_chain (text)
- console_cols / console_rows (number)
- ...GIT_FIELDS (git config like commit_message, author, push_url)

#### Ollama Backend
- model (ollama_model_select — dynamic dropdown)
- host (text, e.g. http://localhost:11434)
- ...GIT_FIELDS
- ...CONSOLE_SIZE_FIELDS

#### OpenWebUI Backend
- url (text, e.g. http://localhost:3000)
- api_key (password)
- model (openwebui_model_select — dynamic dropdown)
- ...other fields

#### Generic Backends (aider, goose, gemini, opencode*, shell)
- binary (text, path to executable)
- ...GIT_FIELDS
- ...CONSOLE_SIZE_FIELDS

**Popup buttons**:
- **Test & Load Models** (if model_select field): `testBackendConnection()` (line 4419)
- **Save & Enable**: `saveBackendConfig()` (line 4473)
- **Cancel**: `closeBackendConfigPopup()` (line 4468)

### Episodic Memory Settings (lines 3662–3680, from LLM_CONFIG_FIELDS)

| Field | Type | Config Key | Line | Behavior |
|-------|------|-----------|------|----------|
| **Enable memory system** | toggle | memory.enabled | 3663 | Auto-save onchange |
| **Storage backend** | select | memory.backend | 3664 | sqlite / postgres |
| **Embedding provider** | select | memory.embedder | 3665 | ollama / openai |
| **Embedding model** | text | memory.embedder_model | 3666 | e.g. "nomic-embed-text" |
| **Embedder host** | text | memory.embedder_host | 3667 | e.g. "localhost:11434" |
| **Search results (top-K)** | number | memory.top_k | 3668 | Number of results to return |
| **Auto-save summaries** | toggle | memory.auto_save | 3669 | Auto-save after session completion |
| **Extract learnings** | toggle | memory.learnings_enabled | 3670 | Extract key learnings from sessions |
| **Storage mode** | select | memory.storage_mode | 3671 | summary / verbatim |
| **Auto entity detection** | toggle | memory.entity_detection | 3672 | Detect entities and relationships |
| **Session awareness** | toggle | memory.session_awareness | 3673 | Inject memory into session prompts |
| **Broadcast summaries** | toggle | memory.session_broadcast | 3674 | Send summaries to active sessions |
| **Auto-install hooks** | toggle | memory.auto_hooks | 3675 | Per-session memory hooks |
| **Hook save interval** | number | memory.hook_save_interval | 3676 | Save memory every N messages |
| **Retention days** | number | memory.retention_days | 3677 | 0 = forever |
| **SQLite path** | text | memory.db_path | 3678 | e.g. "~/.datawatch/memory.db" |
| **PostgreSQL URL** | text | memory.postgres_url | 3679 | Enterprise backend connection string |

### RTK (Token Savings) Settings (lines 3681–3689)

| Field | Type | Config Key | Behavior |
|-------|------|-----------|----------|
| **Enable RTK integration** | toggle | rtk.enabled | Auto-save |
| **RTK binary path** | text | rtk.binary | Path to rtk executable |
| **Show savings in stats** | toggle | rtk.show_savings | Display token savings in Monitor tab |
| **Auto-init hooks** | toggle | rtk.auto_init | Automatically initialize hooks |
| **Auto-update RTK** | toggle | rtk.auto_update | Auto-fetch latest RTK version |
| **Update check interval** | number | rtk.update_check_interval | Seconds between update checks |
| **Discover interval** | number | rtk.discover_interval | Seconds between cache discovery |

### Detection Filters (lines 3255–3260, loadDetectionFilters)

**Label**: "Detection Filters"  
**Load function**: `loadDetectionFilters()` (line 3422)  
**Content**: Dynamic list of regex patterns that trigger session state changes

| Element | Type | Line | Behavior |
|---------|------|------|----------|
| **Filter list** | div | 3258 | Rendered by `loadDetectionFilters()` from /api/detection-filters |
| **Per-filter row** | Text + delete button | — | Shows pattern, action, value; delete button removes |

### Saved Commands (lines 3307–3320, loadSavedCommands)

**Label**: "Saved Commands"  
**Load function**: `loadSavedCommands()` (line 3419)  
**Content**: User-defined quick commands accessible from session quick-command dropdown

| Element | Type | Line | Behavior |
|---------|------|------|----------|
| **Command list** | div | 3310 | Dynamic list: name + command text |
| **Add form** | details collapsible | 3311–3319 | Expand to add new command |
| **Name input** | text | 3314 | Command name (e.g., "approve") |
| **Command text input** | text | 3315 | Command to send (e.g., "y") |
| **Save button** | button | 3316 | onclick `createSavedCmd()` → POST /api/commands |

### Output Filters (lines 3322–3341, loadFilters)

**Label**: "Output Filters"  
**Load function**: `loadFilters()` (line 3423)  
**Purpose**: Patterns that trigger automatic actions (send input, alert, schedule, detect prompt)

| Element | Type | Line | Behavior |
|---------|------|------|----------|
| **Filter list** | div | 3325 | Dynamic list: pattern + action + value |
| **Add form** | details collapsible | 3326–3340 | Expand to add new filter |
| **Pattern input** | text (regex) | 3329 | Regex pattern (e.g., "DATAWATCH_RATE_LIMITED") |
| **Action select** | dropdown | 3330–3335 | send_input / alert / schedule / detect_prompt |
| **Value input** | text | 3336 | Optional value (e.g., "y" for send_input) |
| **Save button** | button | 3337 | onclick `createFilter()` → POST /api/filters |

### Buttons & Actions

| Button | Label | Line | Onclick | Endpoint | Body |
|--------|-------|------|---------|----------|------|
| **Configure (backend)** | ✎ pencil | 3480 | `openLLMSetup(name)` | GET /api/config | Fetch config for backend |
| **Toggle (backend)** | Toggle switch | 3481–3484 | `toggleLLM(cfgKey, enabled, name)` | PUT /api/config | `{session.{backend}_enabled: bool}` |
| **Test & Load Models** | (popup button) | 4372 | `testBackendConnection(service)` | GET /api/ollama/models or /api/openwebui/models | Validate connection |
| **Save & Enable** | (popup button) | 4373 | `saveBackendConfig(service)` | PUT /api/config | Save all field values |
| **Cancel** | (popup button) | 4374 | `closeBackendConfigPopup()` | — | Close modal |
| **Add Saved Command** | "Save Command" (in details) | 3316 | `createSavedCmd()` | POST /api/commands | `{name, command}` |
| **Add Filter** | "Save Filter" (in details) | 3337 | `createFilter()` | POST /api/filters | `{pattern, action, value}` |

### Data Source

| Feed | Type | Endpoint | Load Trigger | Line |
|------|------|----------|--------------|------|
| **LLM backends** | REST | GET /api/backends | On render (line 3425) | 3444–3492 |
| **Detection filters** | REST | GET /api/detection-filters | On render (line 3422) | loadDetectionFilters() |
| **Saved commands** | REST | GET /api/commands | On render (line 3419) | loadSavedCommands() |
| **Output filters** | REST | GET /api/filters | On render (line 3423) | loadFilters() |
| **Config (for fields)** | REST | GET /api/config | On popup open | openLLMSetup() (line 3513) |

### Notable Behaviors

- **Model dropdown auto-fetch**: When popup opens, if host configured, auto-fetches model list from /api/ollama/models or /api/openwebui/models
- **Backend version display**: Shows version if available in /api/backends response
- **Default badge**: Highlights the default backend used for new sessions
- **Auto-save on field change**: Config fields auto-save (no manual Save button per section)
- **Popup field types**: Supports text, password, number, checkbox, select, ollama_model_select, openwebui_model_select
- **Collapsible sections**: Each LLM section can expand/collapse

---

## Screen 9: Settings → About

**Route**: `navigate('settings')` then `switchSettingsTab('about')`  
**Render function**: Part of `renderSettingsView()` (lines 3343–3401 define About sections)  
**Config load**: `loadVersionInfo()` (line 3424)  
**Header title**: "Settings"  
**Tab active**: About

### Layout Structure
```
┌────────────────────────────────────────────┐
│ Settings                                  │
├────────────────────────────────────────────┤
│ [Monitor] [General] [Comms] [LLM] [About] │
├────────────────────────────────────────────┤
│             [logo]                        │
│           datawatch                       │
│    AI Session Monitor & Bridge            │
│                                            │
│ Version              v4.0.5                │
│ Update               [Check now]           │
│ Daemon               [Restart]             │
│ Sessions             {N} in store (link)   │
│ Project              github.com/…          │
│                                            │
│ API                                       │
│ ▼ Swagger UI                   /api/docs  │
│  OpenAPI Spec          /api/openapi.yaml  │
│  Architecture diagrams  /diagrams.html    │
│  MCP Tools         /api/mcp/docs (HTML)   │
│                      or (JSON)             │
│                                            │
└────────────────────────────────────────────┘
```

### About Section (lines 3369–3400)

| Element | Type | Content | Line | Behavior |
|---------|------|---------|------|----------|
| **Logo** | img | `/favicon.svg` | 3372 | Centered 64x64 |
| **App name** | heading | "datawatch" | 3373 | Bold, 18px font, letter-spacing |
| **Tagline** | text | "AI Session Monitor & Bridge" | 3374 | Secondary color, 11px |
| **Version row** | label + value | "v{version}" | 3376–3379 | Loaded by `loadVersionInfo()` from /api/health |
| **Update row** | label + button | "Check now" | 3381–3385 | onclick `checkForUpdate()` (likely checks GitHub releases) |
| **Daemon row** | label + button | "Restart" | 3387–3389 | onclick `restartDaemon()` → POST /api/restart |
| **Sessions row** | label + link | "{N} in store" | 3391–3395 | Clickable link → navigate to sessions with history shown |
| **Project row** | label + link | github.com URL | 3397–3399 | External link to GitHub repo |

### API Section (lines 3343–3366)

| Field | Type | Content | Line | Link/Behavior |
|-------|------|---------|------|---------------|
| **Swagger UI** | link | "/api/docs" | 3347–3348 | Opens interactive API documentation |
| **OpenAPI Spec** | link | "/api/openapi.yaml" | 3350–3352 | Raw OpenAPI 3.0 specification |
| **Architecture diagrams** | link | "/diagrams.html" | 3354–3357 | Fullscreen viewer with zoom + pan |
| **MCP Tools (HTML)** | link | "/api/mcp/docs" | 3359–3364 | HTML documentation for MCP server tools |
| **MCP Tools (JSON)** | link | "/api/mcp/docs" (inline fetch) | 3363 | onclick → fetch as JSON, open in new window |

### Buttons & Actions

| Button | Label | Line | Onclick | Endpoint | Behavior |
|--------|-------|------|---------|----------|----------|
| **Check for Update** | "Check now" | 3383 | `checkForUpdate()` | GET /api/health (or /api/check-update) | Check GitHub releases; show notification if update available |
| **Restart Daemon** | "Restart" | 3388 | `restartDaemon()` | POST /api/restart | Restart datawatch daemon process |
| **Sessions link** | "{N} in store" | 3393 | `navigate('sessions'); state.showHistory=true` | — | Jump to sessions view, show all history |
| **Project link** | "github.com/…" | 3398 | `<a href="…" target="_blank">` | — | External link to GitHub repo |

### Data Source

| Feed | Type | Endpoint | Load Trigger | Line |
|------|------|----------|--------------|------|
| **Version info** | REST | GET /api/health | On render (line 3424) | 3433–3441 |

### Notable Behaviors

- **Version display**: "v" prefix added client-side
- **Update check**: Likely queries GitHub API or a custom endpoint
- **External links**: Open in new tab/window (target="_blank", rel="noopener")
- **Daemon restart**: Shows toast notification "Daemon restarting… reconnecting in a moment."
- **Sessions count**: Dynamically shown from state.sessions.length

---

## Cross-Screen Patterns & Shared Utilities

### Global Utilities Used Across All Screens

| Function | Purpose | Line | Usage |
|----------|---------|------|-------|
| `navigate(view, sessionId)` | Route to top-level view | 969 | From buttons in header, nav bar, or inline |
| `switchSettingsTab(tab)` | Switch settings sub-view | 3064 | From tab buttons in settings |
| `tokenHeader()` | Build auth header | ~150 | Used in all fetch() calls |
| `apiFetch(url, options)` | Wrapper for authenticated fetch | ~200 | Shorthand for fetch + tokenHeader() |
| `escHtml(str)` | XSS prevention | ~100 | Used in all HTML interpolation |
| `stripAnsi(str)` | Remove ANSI color codes | ~900 | Used for terminal output display |
| `timeAgo(date)` | Relative time string | ~800 | Used on all timestamps |
| `showToast(msg, type, duration)` | Notification overlay | ~700 | User feedback after actions |
| `showConfirmModal(msg, onConfirm)` | Confirmation dialog | ~600 | Delete confirmations |

### WebSocket Message Types Handled

| Message Type | Lines | Effect |
|--------------|-------|--------|
| `update_sessions` | ~450 | Refresh session list; re-render if on sessions view |
| `pane_output` | ~500 | Append terminal output to buffer; render if on detail view |
| `channel_message` | ~510 | Append to channelReplies; re-render channel tab |
| `chat_message` | ~515 | Append to chatMessages; re-render chat area |
| `stats` | ~313 | Render stats card on monitor tab |
| `connected` | ~150 | Set connected indicator (green dot) |

### CSS Classes & Styling

| Class | Purpose |
|-------|---------|
| `.session-card {state}` | Session card styling based on state (running/waiting/complete/etc.) |
| `.state-badge-{state}` | State badge color coding |
| `.output-tab.active` | Active output tab (Tmux/Channel/Chat) |
| `.needs-input-banner` | Yellow warning for input-required state |
| `.toggle-switch` | Custom checkbox toggle control |
| `.form-input`, `.form-select`, `.form-textarea` | Form field styling |
| `.stat-card` | Monitor tab stat card grid item |
| `.chat-bubble.chat-{role}` | Chat message styling (user/assistant/system) |
| `.alert-card` | Alert card styling |
| `.backend-config-popup` | Configuration modal overlay |

### localStorage Keys Used

| Key | Content | Scope |
|-----|---------|-------|
| `cs_token` | Browser auth token | Session/persistent |
| `cs_active_view` | Last visited view | Restore on refresh |
| `cs_active_session` | Last active session ID | Restore detail view |
| `cs_settings_tab` | Last settings sub-tab | Restore settings view |
| `cs_session_order` | Drag-drop order array (JSON) | Persist reordering |
| `cs_term_font_size` | Terminal font size (px) | Persist user preference |

---

## Summary of Endpoints Used

### REST Endpoints

| Endpoint | Method | Body | Response | Line |
|----------|--------|------|----------|------|
| `/api/sessions` | GET | — | `[{full_id, id, name, task, state, …}]` | 1524, 2524 |
| `/api/sessions/create` | POST | `{name, task, llm_backend, project_dir, …}` | `{id, full_id}` | 2922 (submitNewSession) |
| `/api/sessions/kill` | POST | `{session_id: fullId}` | `{}` | 1978 (killSession) |
| `/api/sessions/restart` | POST | `{session_id: fullId}` | `{}` | 2007 (restartSession) |
| `/api/sessions/delete` | POST | `{id: fullId, delete_data: bool}` | `{}` | 1279, 1629 |
| `/api/sessions/state` | POST | `{session_id, state: newState}` | `{}` | 2206 (showStateOverride) |
| `/api/sessions/schedule` | POST | `{session_id, run_at, text}` | `{}` | 2159 (scheduleInput) |
| `/api/sessions/{id}/schedules` | GET | — | `[{id, session_id, run_at, type, …}]` | 1811 (loadSessionSchedules) |
| `/api/send_input` | POST | `{session_id, text}` | `{}` | 2047 (sendSessionInput) |
| `/api/channel/send` | POST | `{session_id, text}` | `{}` | 2065 (sendChannelMessage) |
| `/api/schedules` | GET | `?state=pending` | `[{id, run_at, type, …}]` | 1195 (loadGlobalScheduleBadge) |
| `/api/schedules/cancel` | POST | `{id}` | `{}` | 1211 (cancelSchedule) |
| `/api/alerts` | GET | — | `{alerts: [{level, title, body, session_id, …}]}` | 5522 |
| `/api/alerts` | POST | `{all: true}` | `{}` | 5539 (mark all as read) |
| `/api/commands` | GET | — | `[{name, command}]` | 1442, 2523, 5523 |
| `/api/command` | POST | `{text: 'send {sessId}: {cmd}'}` | `{}` | 2055 (sendCommand via WS) |
| `/api/config` | GET | — | `{session: {…}, server: {…}, …}` | 2685, 3408 |
| `/api/config` | PUT | `{key: value, …}` | `{…}` | 3767 (saveGeneralField), 4488 (saveBackendConfig) |
| `/api/backends` | GET | — | `{llm: [{name, available, version, enabled, …}], active: '…'}` | 2830, 3447 |
| `/api/profiles` | GET | — | `{profile-name: {backend: '…'}, …}` | 2700, 3409 |
| `/api/dir-browse` | GET | `?path=…` | `[{name, isDir}]` | Directory tree fetch (openDirBrowser) |
| `/api/interfaces` | GET | — | `[eth0, wlan0, …]` | 3723 (loadCommsConfig) |
| `/api/servers` | GET | — | `[{name, url, …}]` | loadServers() |
| `/api/stats` | GET | — | `{cpu_load, mem_used, disk_used, …}` | 5740 |
| `/api/stats?v=2` | GET | — | `{…, host: {ebpf: {…}}}` | 5756 (loadEBPFStatus) |
| `/api/plugins` | GET | — | `{plugins: [{name, enabled, version, hooks, …}]}` | 5782 |
| `/api/memories/search` | GET | `?query=…&role=…&since=…` | `[{id, content, role, …}]` | searchMemories() |
| `/api/memories` | GET | — | `[{…}]` | listMemories() |
| `/api/memories` | GET | (with download header) | JSON file | exportMemories() |
| `/api/logs` | GET | `?lines=50&offset=…` | `{lines: […], total: N}` | 3696 (loadDaemonLog) |
| `/api/health` | GET | — | `{version: 'v4.0.5', …}` | 3434 (loadVersionInfo) |
| `/api/restart` | POST | — | `{}` | 4511 (restartDaemon) |
| `/api/cert` | GET | — | PEM cert | 3554 (CA cert download) |
| `/api/cert?format=der` | GET | — | DER cert | 3555 (CA cert .crt download) |
| `/api/ollama/models` | GET | — | `['mistral', 'neural-chat', …]` | 4396 (testBackendConnection) |
| `/api/openwebui/models` | GET | — | `['gpt-3.5', …]` | 4396 (testBackendConnection) |
| `/api/comms/signal/status` | GET | — | `{linked: bool, devices: […]}` | loadLinkStatus() |
| `/api/comms/signal/link` | POST | — | `{qr_code: 'data:image/…'}` | startLinking() |
| `/api/detection-filters` | GET | — | `[{pattern, action, value}]` | loadDetectionFilters() |
| `/api/filters` | GET | — | `[{pattern, action, value}]` | loadFilters() |

### WebSocket Endpoints

| Path | Query | Message | Purpose |
|------|-------|---------|---------|
| `/ws` | `?token=…` | (binary stream) | Real-time session output, state updates, stats |
| `/ws` | (subscribe msg) | `{type: 'subscribe', session_id: fullId}` | Subscribe to session output |
| `/ws` | (send_input msg) | `{type: 'send_input', session_id, text}` | Send input to session |

---

## Notes on Missing/Ambiguous Behaviors

1. **Directory browser modal**: Rendering logic not visible in app.js excerpt; likely async tree fetched via GET /api/dir-browse with recursive path expansion.

2. **Drag-drop session reordering**: Implementation in `sessionDragStart/Over/Drop/End` not fully detailed; order persists to localStorage via `sortSessionsByOrder()`.

3. **Xterm.js integration**: `initXterm()` call on line 1804 but implementation not shown; likely initializes terminal with ANSI color support + FitAddon.

4. **Quick-command modal rendering**: showCardCmds() fetches /api/commands but HTML structure renders inline in card; toggle visibility via display style.

5. **Response viewer modal**: `showResponseViewer()` not fully detailed; likely modal with monospace rendering of `sess.last_response`.

6. **LLM configuration popup model loading**: async fetch from /api/ollama/models or /api/openwebui/models; populates select dropdown with results.

7. **Chat markdown rendering**: `renderChatMarkdown()` called on assistant messages; likely converts markdown to HTML (implementation not shown).

8. **Auto-restart daemon config**: `state.autoRestartOnConfig` flag; if set, auto-restarts daemon after config save (line 3503–3505).

9. **Broadcast session summaries**: Memory system feature; if enabled, shares summaries to active sessions (memory.session_broadcast config).

10. **Scroll mode in terminal**: Entering scroll mode shows a modal bar at bottom with navigation buttons; implementation via `toggleScrollMode()`, `scrollPage()`, `exitScrollMode()` (lines 1515–1520).

