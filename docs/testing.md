# Bug test log

Required by [AGENT.md](../AGENT.md) › Testing Requirements › *Bug testing*: before a bug is
closed it gets an entry here with a description, steps, the expected result and the actual
result (PASS / FAIL). UI fixes also need device or surface validation.

Feature-level coverage lives in [testing-tracker.md](testing-tracker.md); this file is the
per-bug record.

## How to read the result line

- **PASS (unit)** — an automated JVM / common / MockWebServer test reproduces the bug's
  condition and passes under `./gradlew test`. The test is named.
- **PASS (emulator)** / **PASS (simulator)** — the steps were run on an Android emulator or
  iOS Simulator, against the sandbox test daemon where a server is needed.
- **PENDING (device)** — the fix is in the shipped build and passes code review / build, but
  no live run is recorded yet. These still need a real-device check.
- **FAIL** — the steps reproduce the bug on the named build.

"Device" below means a real phone against a real datawatch server. None of the entries
below has a recorded real-device run yet; that pass is open in
[plans/README.md](plans/README.md).

---

## v1.24.0 → v1.28.0 (2026-10-04 → 2026-10-06)

### Android voice reply fails under Do Not Disturb

- **Fixed in:** v1.27.1 (Android)
- **Description:** With Do Not Disturb or silent mode on, starting a voice reply failed
  with "Recording failed: Not allowed to change Do Not Disturb state" in the alert dock. The
  recorder muted the ringer (to hide the start beep) without a guard, and Android refused.
- **Steps:** 1. Turn on Do Not Disturb. 2. Open a session. 3. Tap the mic and speak.
  4. Stop recording.
- **Expected:** Recording starts and the transcript appears; no error in the dock. After a
  recorder failure the ringer mode is back to what it was.
- **Actual:** PASS (emulator) — run on an Android emulator with Do Not Disturb on.
- **How verified:** Emulator run recorded with the fix. No unit test (the recorder is a
  platform class). Real-device check pending.

### Full-id session links never connected

- **Fixed in:** v1.27.1 (Android)
- **Description:** `datawatch://session/<host>-<id>` links, and a session restored on
  launch, opened a detail screen keyed on the full id. It never matched the session or its
  stream, so the screen never connected.
- **Steps:** 1. Open `datawatch://session/<hostname>-<shortid>` (for example with
  `adb shell am start -d …`). 2. Watch the session screen.
- **Expected:** The session opens and the terminal connects.
- **Actual:** PASS (unit) — `ShellParityTest` › "session links with a full id open the
  short id".
- **How verified:** Unit test. Real-device check pending.

### Terminal stuck in scroll mode

- **Fixed in:** v1.25.1 (Android + iOS)
- **Description:** Entering or leaving tmux scroll (copy) mode was sent as a WebSocket
  command. If the socket was reconnecting, the command was dropped, tmux stayed in copy mode
  behind the live input bar, and the terminal looked hung until ESC was pressed by hand.
- **Steps:** 1. Open a running tmux session. 2. Tap the scroll button and page up.
  3. Tap scroll again to leave (or leave the session while scrolled back). 4. Repeat while
  the network flaps.
- **Expected:** The app sends `tmux-copy-mode <id>` / `sendkey <id>: Escape` through
  `POST /api/command`, flips the UI only after the server accepts it, falls back to the
  WebSocket if REST fails, and exits copy mode when you leave the session.
- **Actual:** PASS (unit) for the transport — `RestTransportRunCommandTest` ("runCommand
  posts text and returns the router result", "runCommand surfaces server errors as
  failure"). UI flow: PENDING (device).
- **How verified:** Unit test for the request; on-screen behaviour not yet run live.

### Session actions failed on hostnames with a hyphen

- **Fixed in:** v1.25.0 (shared, both apps)
- **Description:** The server-side session id (`fullId`) was derived by cutting the
  hostname at the first `-`. On a host like `build-box-2` the id became `build-5d47`, so
  Stop / Restart / Rename / Delete and full-id deep links all missed.
- **Steps:** 1. Connect to a server whose hostname contains `-`. 2. Stop or rename a
  session.
- **Expected:** The action reaches the right session (`build-box-2-5d47`).
- **Actual:** PASS (unit) — `MappersTest` › "hyphenated hostname keeps the whole prefix so
  fullId matches the server".
- **How verified:** Unit test. Real-device check pending.

### Automata list broke on an automaton with scope warnings

- **Fixed in:** v1.27.1 (shared, both apps)
- **Description:** `scope_warnings` was declared as a boolean, but the server sends a list
  of strings. Decoding any automaton with warnings failed and took the list down with it.
- **Steps:** 1. Have an automaton whose plan has scope warnings. 2. Open the Automata tab.
- **Expected:** The list loads and the automaton's banner lists each warning.
- **Actual:** PASS (unit) — `CouncilPersonaDtoTest` › "scope_warnings decodes as a list of
  strings".
- **How verified:** Unit test.

### LLM edits lost timeout, max in-flight and per-model nodes

- **Fixed in:** v1.27.1 (Android; shared DTO)
- **Description:** The LLM form sent `timeout`, no `max_inflight`, and model rows without
  `node`. The server replaces the whole entry on save, so these settings were silently
  dropped.
- **Steps:** 1. Settings › LLM › edit an LLM. 2. Set a timeout, max in-flight and a model on
  a specific node. 3. Save and reopen.
- **Expected:** The values are still there; the request body uses `timeout_seconds`,
  `max_inflight` and `models[].node`, and never sends `auto_created`.
- **Actual:** PASS (unit) — `RestTransportTaskSTest` ("listLlms reads timeout_seconds,
  max_inflight and models node", "LLM save body uses the server field names and never
  auto_created"); `RestTransportParityExtrasTest` (auto_created never sent).
- **How verified:** Unit tests (MockWebServer request body).

### Council personas: blank prompts, create / edit failed

- **Fixed in:** v1.26.0 (shared, both apps)
- **Description:** Persona create / update sent `prompt` / `description`. The server's
  persona needs `system_prompt` and reads `role`, so prompts loaded blank and saving failed
  with "system_prompt required". The persona and run lists also failed to load (bare
  arrays), and Stop used DELETE instead of `POST …/cancel`.
- **Steps:** 1. Settings › Council › open a persona (prompt is shown). 2. Edit it and save.
  3. Create a new persona. 4. Start a run and stop it.
- **Expected:** Prompt and role load; save and create succeed; Stop cancels the run.
- **Actual:** PASS (unit) — `CouncilPersonaDtoTest` ("persona decodes server role and
  system_prompt", "create body sends system_prompt and role"); `RestTransportCouncilLiveTest`
  ("start run decodes the async ack and lists bare arrays", "cancel posts to the cancel
  route…").
- **How verified:** Unit tests. Council runs were also exercised with real LLM replies on
  the sandbox daemon during v1.27.0 testing.

### Docs Search trust queue never loaded

- **Fixed in:** v1.26.0 (Android + iOS)
- **Description:** The pending / trusted source lists did not match the server's response
  shape, so nothing loaded and Trust / Dismiss did nothing.
- **Steps:** 1. Settings › Docs Search. 2. Open the pending queue. 3. Trust one source,
  dismiss another.
- **Expected:** Pending and trusted lists show the server's entries; Trust / Dismiss post
  the selected sources and the lists update.
- **Actual:** PASS (unit) — `RestTransportIosHTest` (docsTrustPendingEntriesParsesServerShape,
  docsTrustedEntriesUsesGrantedByAsDetail, docsTrustDecidePostsSourcesArray).
- **How verified:** Unit tests (shared transport used by both apps).

### Guardrail Library type badge always blank

- **Fixed in:** v1.26.0 (Android + iOS)
- **Description:** The badge read a field the server doesn't send; the type is in `type`.
- **Steps:** Settings › Guardrail Library — look at each guardrail's badge.
- **Expected:** Each guardrail shows its type (for example `sast`, `secrets`).
- **Actual:** PASS (unit) — `RestTransportIosHTest.guardrailLibraryReadsTypeAsKind`.
- **How verified:** Unit test.

### Schedule cron badge never shown

- **Fixed in:** v1.26.0 (Android + iOS)
- **Description:** Schedules read the cron expression from the wrong field, so recurring
  schedules showed no cron badge.
- **Steps:** 1. Create a recurring schedule. 2. Open Scheduled Events.
- **Expected:** The row reads `<session> [<schedule>]: <command>` with a cron badge.
- **Actual:** PASS (unit) — `RestTransportIosHTest.scheduleMapsCronExprAndSessionName`,
  `ScheduleRowLabelTest` (4 tests).
- **How verified:** Unit tests.

### Splash "Updated to vX" badge

- **Fixed in:** v1.25.1 (Android + iOS) — removal
- **Description:** A badge copied from the web UI in v1.25.0 that was never requested.
- **Steps:** 1. Install an update over an older build. 2. Launch the app.
- **Expected:** The splash shows (update launch) with no "Updated to vX" badge.
- **Actual:** PENDING (device). Splash gating itself is covered by `SplashGateTest`.
- **How verified:** Code removal reviewed; no live run recorded.

### Read-only Config Viewer duplicated Settings

- **Fixed in:** v1.25.1 (Android + iOS) — removal
- **Description:** Settings › General showed a read-only card per config section that
  duplicated the editable cards.
- **Steps:** Open Settings › General.
- **Expected:** No per-section Config Viewer cards; the Raw config card (view / edit) is
  still there.
- **Actual:** PENDING (device).
- **How verified:** Code removal reviewed; no live run recorded.

### Automata tab hidden after enabling Automata

- **Fixed in:** v1.27.0 (shared, both apps)
- **Description:** The tab was gated on `autonomous.enabled` from `/api/config`, which lags
  a runtime toggle, so the tab stayed hidden.
- **Steps:** 1. Disable Automata on the server, open the app (tab hidden). 2. Enable
  Automata at runtime. 3. Return to the app.
- **Expected:** The tab appears, driven by the live `/api/autonomous/config` (falling back
  to `/api/config`).
- **Actual:** PASS (unit) — `RestTransportRunCommandTest` › "autonomous gating reads the
  live autonomous config".
- **How verified:** Unit test.

### "Waiting for MCP channel…" banner never cleared

- **Fixed in:** v1.25.1 (Android + iOS)
- **Description:** The banner only cleared from the session's `channel_ready` field, not
  from the live `channel_ready` WebSocket event or the ready line in the output, as the web
  UI does.
- **Steps:** 1. Start a claude-code session with the MCP channel. 2. Open it before the
  channel connects. 3. Wait for "Listening for channel" in the output.
- **Expected:** The banner clears on the `channel_ready` event or when a ready marker
  appears in output, pane capture or chat (ANSI stripped), and stays cleared when you come
  back to the session.
- **Actual:** PASS (unit) — `ChannelReadyHubTest` (14 tests: every marker, ANSI-split
  markers, frame routing, other sessions ignored, sticky cache).
- **How verified:** Unit tests. If the banner never clears after a daemon restart, that is
  the server bridge not re-registering: dmz006/datawatch#174 (open).

### Reconnect status wrapped mid-phrase

- **Fixed in:** v1.28.0 (Android + iOS)
- **Description:** "Reconnecting to session… attempt N of 3" was one long monospace line
  that wrapped mid-phrase on phones.
- **Steps:** 1. Open a session. 2. Stop the server or drop the network.
- **Expected:** Two centred lines: "Reconnecting to session…" / "attempt N of 3", in every
  language.
- **Actual:** PENDING (device).
- **How verified:** Build + review; no live run recorded.

### iOS session card wrapped mid-word

- **Fixed in:** v1.25.0 (badge row) and v1.26.0 (state pill + buttons) — iOS
- **Description:** The badge row truncated (`⚠ zombie`, `📄 Response`), and the state pill
  and action buttons in the card header wrapped mid-word.
- **Steps:** Open Sessions on a narrow iPhone with sessions carrying several badges.
- **Expected:** The badge row wraps to a new line; the state pill and buttons keep whole
  words.
- **Actual:** PENDING (device). A DEBUG `-dwOpenSession` launch hook was added for
  simulator screenshot passes; no pass result is recorded.
- **How verified:** SwiftUI change, simulator build in CI.

### iOS alert group header squeezed

- **Fixed in:** v1.26.0 (iOS)
- **Description:** The alert group header squeezed every label onto one line.
- **Steps:** Open Alerts with a group whose session name is long.
- **Expected:** The header wraps to two lines and every label stays readable.
- **Actual:** PENDING (device).
- **How verified:** SwiftUI change, simulator build in CI.

### API keys visible in forms and raw editors

- **Fixed in:** v1.27.0 (shared, both apps)
- **Description:** Android's LLM form showed a literal `api_key_ref` in plain text; the new
  raw editors on both apps showed it too.
- **Steps:** 1. Configure an LLM with a literal API key. 2. Open it in the LLM form, the
  "</> YAML" view and the profile editor.
- **Expected:** Literal keys show as a placeholder and the stored value is restored on save;
  `${secret:…}` references stay visible; a key you replace is kept.
- **Actual:** PASS (unit) — `SecretMaskTest` (2), `ProfileEditorTest`
  (workingDocMasksLiteralSecretsOnly, buildBodyRestoresSecretsAndKeepsName,
  placeholderWithoutStoredValueIsRejected).
- **How verified:** Unit tests.

### iOS push registration not per Apple's documentation

- **Fixed in:** v1.28.0 (iOS)
- **Description:** The app registered with APNs on first launch only, stored the device
  token locally, didn't retry a failed registration, and didn't tell the server which APNs
  environment the token belongs to.
- **Steps:** 1. Install a TestFlight build on an iPhone and allow notifications.
  2. Relaunch the app. 3. Check the server's device list.
- **Expected:** The app registers on every launch, retries on the next foreground after a
  failure, keeps the token in memory only, and `POST /api/devices/register` carries
  `apns_environment: "production"` (`"development"` for debug builds).
- **Actual:** PENDING (device). APNs does not run on the Simulator. Actual delivery also
  needs the server's APNs sender (dmz006/datawatch#183, open).
- **How verified:** Code checked against Apple's "Registering your app with APNs",
  `aps-environment` and token-auth documentation; the App Store profile was confirmed to
  carry the production entitlement.

### Other fixes in this range (unit-tested)

| Fix | Version | Result | Test |
|-----|---------|--------|------|
| Schedules created without a command; orchestrator graphs dropped the project directory; pipeline Cancel did nothing on Android | v1.24.0 | PASS (unit) for graphs + pipeline cancel; schedule create PENDING (device) | `RestTransportTest` (cancelPipelinePostsIdAndActionAsQuery, createOrchestratorGraphSendsProjectDir); the create-schedule test does not assert the `command` field |
| Compute-node model list failed to load (`{models: …}` envelope) | v1.25.0 | PASS (unit) | `RestTransportAndroidParityTest.getComputeNodeModelsUnwrapsServerEnvelope` |
| Scan settings sent under names the server ignores | v1.25.0 | PASS (unit) | `RestTransportAndroidParityTest.scanConfigUsesServerKeys` |
| New profiles created with PUT (server rejects) | v1.27.0 | PASS (unit) | `RestTransportIosSettingsDepthTest.createKindProfilePostsToCollection` |
| Batch Archive set the type to "archived" | v1.25.0 | PENDING (device) | — |
| Signal device linking never produced a link | v1.25.0 | PASS (unit) for the stream flow | `RestTransportIosSettingsFormsTest` (signal link start / stream) |
