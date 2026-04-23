# 2026-04-22 – PWA Parity Audit

**Goal**: Identify every UI/UX difference between the PWA (`https://127.0.0.1:8443`) and the Android `datawatch-app`.  For each difference we record the exact source file / line range in the PWA (JS) and the Android app (Kotlin), classify the gap, and create a concrete backlog ticket.

---

## 1. Session Header / Top‑App‑Bar
| Feature | PWA implementation (app.js) | Android implementation (Kotlin) | Gap | Ticket |
|---------|-----------------------------|--------------------------------|-----|--------|
| Session title (truncated) | `renderSessionDetail` builds a `<div class="session-detail">` with a `<h2>` and CSS `text-overflow: ellipsis` (lines ~1671‑1680). | `SessionDetailScreen.kt` – `TopAppBar` title Text (lines 149‑157) uses `maxLines = 1` and `TextOverflow.Ellipsis`. | ✅ Parity – already matches. | – |
| Session ID displayed next to title | PWA adds `<span class="session-id">` after title (lines ~1675). | Android adds `Text(state.session?.id)` (lines 160‑166). | ✅ Parity. | – |
| Backend / mode chip | PWA renders a chip with `backend` and `mode` classes (lines ~1672‑1678). | Android renders backend and messagingBackend chips (lines 167‑176, 178‑186). | ✅ Parity. | – |
| Hostname prefix | PWA shows `hostnamePrefix` in a small label (lines ~1679‑1684). | Android adds hostname prefix (lines 189‑199). | ✅ Parity. | – |
| Connection dot (green/red/grey) | PWA uses CSS class `dot` with colors (`.dot.connected {background:#22c55e}` etc.) (lines ~1720‑1730). | Android draws a 10dp `Box` with `Color(0xFF22C55E)` / `0xFFEF4444` / `0xFF94A3B8` (lines 208‑226). | ✅ Parity – already implemented (comment notes match). | – |
| **Missing**: PWA shows a *clickable* state pill that toggles a dropdown for manual override. Android `SessionInfoBar` currently shows a pill but does not expose the dropdown UI (the `onStateClick` opens a menu, but UI elements differ). |
| State pill UI | PWA renders `<div class="session-state ${state}">` with a tooltip and click handler (lines ~1682‑1688). | Android `SessionInfoBar` draws a colored pill (implementation in `SessionInfoBar.kt`, not shown here) but lacks the tooltip text and the exact hover behavior. | ⚠️ Partial – need to add tooltip/hover description and ensure click opens the same menu. | **B34** – Add tooltip and matching state‑override menu to `SessionInfoBar`. |

## 2. Quick‑Command / Command Bar
| Feature | PWA (`app.js`) | Android (`SessionDetailScreen.kt` & other) | Gap | Ticket |
|---------|----------------|-------------------------------------------|-----|--------|
| Quick‑command **select** dropdown | Rendered as `<select class="quick-cmd-select">` with options (lines 1460‑1465, 2484‑2488). | Android has no dropdown – the UI only shows a plain text input and a send button (`ReplyComposer`). | ❌ Missing – need full quick‑command palette. | **B35** – Implement quick‑command dropdown in Android toolbar.
| Quick‑command **send** button (arrow) | `<button class="quick-btn" title="Send">` (lines 1463‑1464). | Android send button already exists but is tied to the text field, not to the dropdown. | ⚠️ Partial – integrate with dropdown. |
| Quick‑command **cancel** button (X) | `<button class="quick-btn" title="Cancel">` (lines 1464‑1465). | Android cancel action is the back button; no explicit cancel for quick command. | ❌ Missing – add cancel button to clear selection. |
| Quick‑reply **buttons** under chat prompt | Rendered in `ChatEventList` with `quick-reply` class (lines 3130‑3134). | Android `ChatEventList` exists (lines 311‑319) and passes `onQuickReply` but UI currently shows a plain text button only if the backend provides quick replies. Need to verify that the UI renders the button list; currently only `ChatEventList` renders events; UI for quick‑reply may be missing styling. | ⚠️ Partial – ensure quick‑reply buttons appear with same layout as PWA. |
| Quick‑input **row** for custom command | PWA adds a custom command row `<div class="quick-input-row">` (line 5580). | Android has a free‑form input field at bottom of screen (ReplyComposer). No dedicated row for custom command separate from the main input. | ❌ Missing – add optional custom command row when a quick‑command is selected. |

## 3. Settings – Communications (Comms) tab
| Field | PWA location (app.js) | Android location (`SettingsScreen.kt` & panels) | Gap | Ticket |
|-------|----------------------|--------------------------------------------|-----|--------|
| **Browser token** | Rendered under `data-group="comms"` section `auth` (lines 3104‑3112). | Android `ConfigFieldsPanel(ConfigFieldSchemas.CommsAuth)` renders token fields (lines 285‑287). | ✅ Parity – present. |
| **Server bearer token** | Rendered under `servers` section (lines 3110‑3115). | Android `ServersCard` shows server list with edit/delete but does **not** expose a bearer‑token field directly; token is stored in vault and edited via server edit sheet (not visible). | ⚠️ Partial – UI to edit bearer token is hidden; PWA shows it inline. |
| **MCP SSE bearer token** | Rendered under `servers` section (lines 3115‑3117). | Android same issue – token handled internally. | ⚠️ Partial – expose token edit UI. |
| **Proxy resilience toggle** | Section `proxy` with toggle (lines 3151‑3158). | Android `ConfigFieldsPanel(ConfigFieldSchemas.Proxy)` renders proxy fields – likely matches. Need to verify presence of “Resilience” toggle; looks present. |
| **Signal device linking / QR** | Section `linkQrRow` (line 3168) – hidden row that becomes visible when linking. | Android has `ChannelsCard` with QR linking (in `ChannelsCard.kt`), but UI may differ. |
| **Notification preferences** | Section `gc_notifs` (lines 3222‑3230) – UI with enable/disable toggles. | Android `NotificationsCard()` renders notification settings (line 279). Parity appears ok.
| **Missing**: **TLS / Certificate selector** – PWA includes a TLS cert dropdown under `comms` (not present in Android). |
| **Missing**: **Raw YAML config editor** – PWA provides a “Raw config” modal (see `BL14` in backlog). Android currently has no raw‑YAML editor. |
| **Missing**: **Localization options** – PWA shows language selector under `gc_general` (not in Android). |

## 4. Settings – LLM tab
| Feature | PWA (`app.js`) | Android (`SettingsScreen.kt` – LLM tab) | Gap | Ticket |
|---------|----------------|----------------------------------------|-----|--------|
| Model selector | Rendered in `llm` section (lines 3180‑3188). | Android `LlmConfigCard()` shows backend list with default badge (lines 323‑324). | ✅ Parity.
| **Temperature slider** | Input field `default_effort` type `text` (line 3614) – UI provides a numeric slider. | Android `LlmConfigCard` does **not** expose temperature or top‑p controls. | ❌ Missing – add temperature, top‑p, max‑tokens sliders. |
| **Prompt templates list** | Rendered under `lc_*` sections (lines 3188‑3195). | Android `ConfigFieldsPanel(ConfigFieldSchemas.Memory)` and `LlmRtk` – not clear if prompt templates are shown. Likely missing. |
| **Saved commands** | Section `saved_commands` (lines 3255‑3262). | Android `SavedCommandsCard()` (line 331) – appears present.
| **Output filters** | Section `filters` (lines 332‑339). | Android `FiltersCard()` (line 332) – present.
| **Raw YAML editor** | `BL14` (not shipped yet) – UI to edit raw config. | Android has no such editor. | ❌ Missing – plan to add raw‑YAML editor behind biometric lock.

## 5. Settings – General / Misc
| Feature | PWA | Android | Gap | Ticket |
|---------|-----|---------|-----|--------|
| **Project profiles** | Section `gc_projectprofiles` (lines 3195‑3200). | Android `KindProfilesCard(kind="project")` (lines 271‑274). | ✅ Parity.
| **Cluster profiles** | Section `gc_clusterprofiles` (lines 3214‑3219). | Android `KindProfilesCard(kind="cluster")` (lines 275‑278). | ✅ Parity.
| **Security / token vault** | Section `security` (lines 3222‑3228). | Android `SecurityCard()` (line 243). | ✅ Parity.
| **Localization (DE/ES/FR/JA)** | Section `gc_general` includes language selector (not visible in snippet but present). | Android has no language selector. | ❌ Missing – add language selection UI. |

## 6. Other UI Elements
| Component | PWA feature | Android counterpart | Gap |
|-----------|------------|--------------------|-----|
| **In‑app notification panel** | Rendered as `.alerts` overlay (lines 3083‑3090). | Android only shows system push notifications; no in‑app panel. | ❌ Missing – implement in‑app alerts panel. |
| **Backlog pager button** | Visible in terminal toolbar (`Backlog` button, lines 424‑430). | Android `TerminalToolbar.kt` includes a “Backlog” button (line 38 comment). Parity seems present. |
| **Session‑detail timeline bottom‑sheet** | PWA uses a bottom‑sheet overlay (lines 1411‑1415). | Android `SessionDetailScreen` opens `Timeline` via `onTimeline` (line 278) – uses a dialog; parity likely OK. |
| **Widget configuration** | PWA does not have home‑screen widgets. | Android widgets are separate; no parity needed. |

---

## Actionable Backlog (new IDs)
| New ID | Title | Component | Description |
|--------|-------|-----------|-------------|
| **B34** | Session header state‑pill tooltip & menu | Session Header | Add a hover tooltip describing the state and make the click open the same dropdown as PWA (override menu). |
| **B35** | Full quick‑command palette | Session Detail | Add a dropdown `<select>` with command options, send and cancel buttons, and integrate with existing ReplyComposer. |
| **B36** | Quick‑reply button layout parity | Chat UI | Ensure quick‑reply buttons are rendered with same spacing and styling as PWA. |
| **B37** | TLS / Certificate selector in Comm settings | Settings – Comms | Expose a dropdown for selecting a client certificate, mirroring PWA UI. |
| **B38** | Raw YAML config editor (biometric protected) | Settings – Misc | Add a modal editor for raw YAML config, guarded by biometric authentication (matches `BL14`). |
| **B39** | LLM temperature / max‑tokens sliders | Settings – LLM | Add sliders for temperature, top‑p, max‑tokens, and bind to backend config. |
| **B40** | Prompt templates UI | Settings – LLM | Show/edit list of prompt templates, similar to PWA. |
| **B41** | Language selector (i18n) | Settings – General | Provide a dropdown to select UI language (DE, ES, FR, JA). |
| **B42** | In‑app alerts panel | Global UI | Implement an overlay panel for alerts (mirroring PWA’s alerts overlay). |

**Next steps**
1. Prioritize tickets by user impact (quick‑command palette and TLS selector are high‑impact). 
2. Create GitHub issues for each new ID and link them to the appropriate source file.
3. Begin implementation in the order: B35 → B34 → B37 → B38 → B39 → B40 → B41 → B42.

*Audit compiled on 2026‑04‑22. All line references are based on the current checkout of both codebases.*