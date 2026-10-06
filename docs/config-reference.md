# Configuration reference

*Last updated 2026-10-06 for v1.28.0 (see "Added in v1.24–v1.28" near the end).*

Per [AGENT.md](../AGENT.md) Configuration Accessibility Rule, every
user-settable value has:

1. In-app Settings path.
2. Default.
3. Accepted types.
4. Persistence layer.
5. Round-trip behaviour (whether the value is server-echo'd or
   mobile-only).

Two storage tiers:

- **Mobile-only** — `EncryptedSharedPreferences` (scalar prefs) or
  SQLCipher-encrypted `SharedDB` (per-profile records). Never touches
  the server.
- **Server-round-trip** — read via `GET /api/config` (or a dedicated
  endpoint) and written via `PUT /api/config` or dedicated
  write-endpoints. Governed by ADR-0019 (structured fields only — no
  raw YAML from mobile).

---

## Servers + reachability

### profile.name

- **UI path:** Settings → Servers → Add / Edit → Name.
- **Type:** String (non-empty, ≤ 64 chars).
- **Default:** none.
- **Persisted in:** SQLCipher `server_profiles` row.
- **Server echo:** no.

### profile.baseUrl

- **UI path:** Settings → Servers → Add / Edit → URL.
- **Type:** String (URL, `http://` or `https://`).
- **Default:** none.
- **Persisted in:** SQLCipher `server_profiles` row.
- **Server echo:** no.

### profile.bearerToken

- **UI path:** Settings → Servers → Add / Edit → Token.
- **Type:** String (opaque).
- **Default:** none.
- **Persisted in:** `EncryptedSharedPreferences` (keyed by profile id).
- **Server echo:** no. Scoped to the Android Keystore master key.

### profile.trustAllTls

- **UI path:** Settings → Servers → Add / Edit → Security → **Trust all
  certificates** (insecure).
- **Type:** Boolean (stored as a sentinel in `trustAnchorSha256`).
- **Default:** `false`.
- **Persisted in:** SQLCipher `server_profiles` row.
- **Server echo:** no.
- **Scope (v1.27.0):** applies to **that profile's host only**; every other
  host (including the docs viewer) uses the system trust store. Both apps.

### profile.trustAnchorSha256 (certificate pin)

- **UI path:** Settings → Servers → Add / Edit → Security → **Fetch
  certificate** → confirm the SHA-256 fingerprint → **Pin**.
- **Type:** Hex SHA-256 of the server's leaf certificate (DER), or null.
- **Default:** null (system trust store).
- **Persisted in:** SQLCipher `server_profiles.trust_anchor_sha256`.
- **Server echo:** no.
- **Effect:** only the pinned certificate is accepted for that server, and the
  hostname must still match. Android v1.24.0; iOS v1.23.x. Probing refuses
  `http://` URLs.

### profile.enabled

- **UI path:** Toggled implicitly by the server picker.
- **Type:** Boolean.
- **Default:** `true`.
- **Persisted in:** SQLCipher `server_profiles` row.
- **Server echo:** no.

### profile.noAuth

- **UI path:** Settings → Servers → Add / Edit → **No bearer token**.
- **Type:** Boolean.
- **Default:** `false`.
- **Persisted in:** SQLCipher `server_profiles` row.
- **Server echo:** no.
- **Notes:** When true, `profile.bearerToken` is ignored and the
  `Authorization` header is dropped.

---

## Security

### biometricUnlock.enabled

- **UI path:** Settings → Security → **Biometric unlock**.
- **Type:** Boolean.
- **Default:** `false`.
- **Persisted in:** `EncryptedSharedPreferences` (key
  `security.biometric.enabled`).
- **Server echo:** no.

---

## Session preferences (server-round-trip, per-server)

These land in `config.behaviour.*` on the server. Edited via
**Settings → General → Behaviour Preferences** (v0.20.0) and repeated
under **Settings → Operations** for quick access.

### behaviour.input_mode

- **Type:** Enum `tmux` / `channel` / `none`.
- **Default:** `tmux`.
- **Wire:** `PUT /api/config` with `behaviour.input_mode`.

### behaviour.output_mode

- **Type:** Enum `tmux` / `channel` / `both` / `none`.
- **Default:** `tmux`.
- **Wire:** `PUT /api/config` with `behaviour.output_mode`.

### behaviour.recent_window_minutes

- **Type:** Int (minutes).
- **Default:** `5`.
- **Wire:** `PUT /api/config` with `behaviour.recent_window_minutes`.

### behaviour.max_concurrent

- **Type:** Int.
- **Default:** `10`.
- **Wire:** `PUT /api/config` with `behaviour.max_concurrent`.

### behaviour.scrollback_lines

- **Type:** Int.
- **Default:** `5000`.
- **Wire:** `PUT /api/config` with `behaviour.scrollback_lines`.

### sessionSort.order

- **UI path:** Sessions tab → Sort dropdown.
- **Type:** Enum `recent_activity` / `started` / `name` / `custom`.
- **Default:** `recent_activity`.
- **Persisted in:** `EncryptedSharedPreferences` (mobile-only
  preference).
- **Server echo:** `custom` order is saved via
  `POST /api/sessions/reorder` (v0.31.0).

---

## LLM backend

Edited via **Settings → LLM** through the **BackendConfigDialog**
(v0.21.0). Structured fields only per ADR-0019.

### backend.active

- **Type:** String (backend name).
- **Wire:** `POST /api/backends/active`.

### backends.<name>.model

- **Type:** String.
- **Wire:** `PUT /api/config` with `backends.<name>.model`.

### backends.<name>.base_url

- **Type:** String (URL).
- **Wire:** `PUT /api/config`.

### backends.<name>.api_key

- **Type:** String (redacted in reads; parent server masks).
- **Wire:** `PUT /api/config`.

---

## Detection filters (v0.32.0)

Edited via **DetectionFiltersCard** under Settings → LLM / Detection.
Four parallel pattern lists feeding `config.detection.*_patterns`.

### detection.prompt_patterns

- **Type:** Array of regex strings.
- **Wire:** `PUT /api/config` with `detection.prompt_patterns`.

### detection.completion_patterns

- **Type:** Array of regex strings.
- **Wire:** `PUT /api/config`.

### detection.rate_limit_patterns

- **Type:** Array of regex strings.
- **Wire:** `PUT /api/config`.

### detection.input_needed_patterns

- **Type:** Array of regex strings.
- **Wire:** `PUT /api/config`.

### detection.debounce_ms

- **Type:** Int milliseconds.
- **Default:** server-managed.
- **Wire:** `PUT /api/config`.

### detection.cooldown_ms

- **Type:** Int milliseconds.
- **Default:** server-managed.
- **Wire:** `PUT /api/config`.

---

## Profiles (v0.15 / v0.32)

Edited via **KindProfilesCard** → **ProfileEditDialog** under
Settings → Profiles.

### profile.active

- **Type:** String (profile name).
- **Wire:** `POST /api/profiles/activate` (single) +
  passed on `/api/sessions/start` as the `profile` field.

### profiles.<kind>.<name>

- **UI path (v1.27.0):** Settings → Profiles → row or ＋ → profile editor.
  Full form with every web-UI field, defaults and validation, plus a
  **YAML view / Form view** toggle on the same editor (both apps).
- **Type:** Structured object (name, description, nested blocks).
- **Kinds:** `project`, `cluster`.
- **Wire:** new profiles `POST /api/profiles/<kind>s`; edits
  `PUT /api/profiles/<kind>s/<name>`. Unknown keys round-trip unchanged.
- **Secrets:** literal secret values are shown as a placeholder and restored
  from the stored copy on save; `${secret:…}` references stay visible.
- **Errors:** invalid YAML shows a line-numbered message and keeps your text.

---

## Channels (Comms, v0.18 / v0.20)

Managed via **ChannelsCard** under Settings → Comms.

### channels.<id>.enabled

- **Type:** Boolean.
- **Wire:** `PATCH /api/channels/{id}` with `{"enabled": …}`.

### channels.<id>.config

- **Type:** Channel-specific structured object.
- **Wire:** BackendConfigDialog (v0.21.0-style) or `PUT /api/config`
  fragment.

### channels.add / .remove

- **Status:** 🚧 — parent returns 501 on `POST /api/channels`; tracked
  at [dmz006/datawatch#18](https://github.com/dmz006/datawatch/issues/18).
  Edits to existing channels work today.

---

## Schedules

### schedules

- **UI path:** Settings → General → Schedules + per-session strip
  above composer.
- **Type:** Array of `{ task, cron, enabled, sessionId? }`.
- **Wire:** `GET /api/schedules` (list), `POST /api/schedules`
  (create), `DELETE /api/schedules/{id}` (cancel).

---

## Saved commands

### commands

- **UI path:** Settings → General → Saved commands.
- **Type:** Array of `{ name, command }`.
- **Wire:** `GET /api/commands`, `POST /api/commands`,
  `DELETE /api/commands/{id}`.
- **Recall:** New Session → **From library ▾** inlines the command text.

---

## Memory (v0.17)

- **UI path:** Settings → Memory.
- **Wire:** `/api/memory/{stats,list,search,delete,remember,export}`.
- **Export** (v0.22.0) via SAF `CreateDocument` → bearer-auth GET →
  user-chosen URI.

---

## Notifications

### notifications.channel.<id>.enabled

- **UI path:** System settings → Apps → datawatch → Notifications.
- **Type:** Boolean.
- **Default:** `true`.
- **Persisted in:** Android notification-channel state.
- **Server echo:** no.

### notifications.activeSessionSuppression

- **UI path:** (implicit; v0.19.0).
- **Type:** Boolean.
- **Default:** `true`.
- **Persisted in:** code-level constant; `ForegroundSessionTracker`
  + `NotificationPoster` guard.

---

## Autonomous (v8.16.0 / v8.17.0)

Settings for the Autonomous / Automata subsystem. Persisted server-side; displayed in
Settings → Autonomous (ConfigFieldsPanel / ConfigSection `Autonomous`).

### autonomous.verifier_diff_max_bytes

- **UI path:** Settings → Autonomous → "Verifier diff max bytes (0 = 8192 default)".
- **Type:** Integer (≥ 0).
- **Default:** `0` (server interprets as 8192 bytes).
- **Server version:** v8.16.0+.
- **Wire:** `/api/config` — key `autonomous.verifier_diff_max_bytes`.
- **Effect:** Maximum byte count of the git-diff payload sent to the verifier for
  grounding. Raise this when the verifier misses context in large changesets; 0 uses
  the server's built-in default of 8192.

### autonomous.default_quality_gates.enabled

- **UI path:** Settings → Autonomous → "Quality gates enabled by default".
- **Type:** Boolean.
- **Default:** `false`.
- **Server version:** v8.17.0+.
- **Wire:** `/api/config` — key `autonomous.default_quality_gates.enabled`.
- **Effect:** When `true`, every new PRD starts with a quality-gate check enabled. Can
  be overridden per-PRD.

### autonomous.default_quality_gates.test_command

- **UI path:** Settings → Autonomous → "Quality gate test command".
- **Type:** String (shell command, e.g. `go test ./...`).
- **Default:** `""` (no test command).
- **Server version:** v8.17.0+.
- **Wire:** `/api/config` — key `autonomous.default_quality_gates.test_command`.
- **Effect:** Shell command the quality gate runs to validate work. Executed in the
  working directory of the session.

### autonomous.default_quality_gates.timeout

- **UI path:** Settings → Autonomous → "Quality gate timeout (sec, 0 = no limit)".
- **Type:** Integer (seconds, ≥ 0).
- **Default:** `0` (no limit).
- **Server version:** v8.17.0+.
- **Wire:** `/api/config` — key `autonomous.default_quality_gates.timeout`.
- **Effect:** Hard wall-clock limit for the quality-gate test command. 0 means no
  timeout.

### autonomous.default_quality_gates.block_on_regression

- **UI path:** Settings → Autonomous → "Block on test regression".
- **Type:** Boolean.
- **Default:** `false`.
- **Server version:** v8.17.0+.
- **Wire:** `/api/config` — key `autonomous.default_quality_gates.block_on_regression`.
- **Effect:** When `true`, a failing quality-gate blocks PRD progression and moves the
  session to the waiting-input state so a human can intervene.

---

## Added in v1.24–v1.28 (2026-10-04 → 2026-10-06)

Server-round-trip settings read and written by both apps unless marked
otherwise. Android keys live in `ui/configfields/ConfigFieldSchemas.kt`; iOS
in `iosApp/iosApp/ui/screens/settings/SettingsCatalog.swift`.

### Automaton concurrency (per automaton)

- **UI path:** Automata → automaton → Overview meta row and the automaton's
  settings sheet → **Concurrency**.
- **Type:** Integer ≥ 0. `0` = use the global
  `autonomous.max_concurrent_tasks` default.
- **Default:** `0`.
- **Wire:** read from `max_concurrent_tasks` on
  `GET /api/autonomous/prds/{id}`; written with
  `POST /api/autonomous/prds/{id}/set_concurrency {max_concurrent_tasks}`.
- **Since:** v1.27.1.

### autonomous.decomposition_backend / decomposition_effort / verification_effort / stale_task_seconds

- **UI path:** Settings → Automata → **Automaton decomposition** card.
  Android has had these since v0.33.11; iOS gained them in v1.27.1.
- **Types / defaults:**
  - `decomposition_backend` — String; empty = inherit.
  - `decomposition_effort` — `quick` / `normal` / `thorough`; empty = server
    default.
  - `verification_effort` — `quick` / `normal` / `thorough`; empty = server
    default.
  - `stale_task_seconds` — Integer seconds; placeholder `600`.
- **Wire:** `PUT /api/config` with `autonomous.<key>`.

### council.llm_ref (and other council settings)

- **UI path:** Settings → Council → council settings (iOS v1.25.0; Android
  `CouncilCard`).
- **Fields:**
  - `llm_ref` — String: the LLM registry entry council personas run on; empty
    = server default.
  - `max_parallel` — Int.
  - `comm_firehose` — Bool.
  - `spawn_real_sessions` — Bool.
  - `draft_retention_days` — Int.
- **Wire:** `GET` / `PUT /api/council/config`.
- **Use:** a council run (`POST /api/council/run {proposal, mode, personas,
  spawn_real_sessions}`) uses the configured `llm_ref`. If no LLM is
  reachable, the run ends with an error in the live sheet.

### LLM registry form: timeout_seconds / max_inflight / models[].node

- **UI path:** Settings → Compute → LLMs → add / edit (form or "</> YAML").
- **Fields:**
  - `timeout_seconds` — Int; `0` = adapter default. The legacy `timeout` key
    is still read.
  - `max_inflight` — Int; autonomous sessions in flight on this LLM;
    `0` = unlimited.
  - `models[].node` — String: the compute node for each enabled model row.
- **Wire:** `POST /api/llms` (create) / `PUT /api/llms/{name}` (full replace).
  `auto_created` is read-only and never sent.
- **Since:** v1.27.1. Before that, Android sent the wrong names and the
  server dropped these values.

### Terminal size — console_cols / console_rows (server-resolved)

- **UI path:** none in the apps. Set it on the server (LLM registry entry →
  per-backend config → `session.console_cols` / `console_rows`), or in the
  LLM form's session-backend section (`console_cols`, `console_rows`).
- **Type:** Int columns / rows. `0` = not reported.
- **Effect (v1.28.0):** the xterm minimum width is the session's
  `console_cols`. If that is missing, the app uses the server's per-backend
  default (claude-code 120×40, otherwise 80×24). Rows only seed the first
  `resize_term`; the server clamps.
- **Persisted in:** session cache (DB migration 9) from `GET /api/sessions`.
- **Removed:** Android's local "Terminal dimensions" override card (it was
  never mounted, and the web UI has no client-side override).

### apns_environment (iOS device registration)

- **UI path:** none — set automatically by the build. Debug builds send
  `development`; TestFlight / App Store builds send `production`.
- **Wire:** `POST /api/devices/register {device_token, kind:"apns",
  app_version, platform:"ios", profile_hint, apns_environment}` on every
  launch, for every enabled server profile. Omitted for FCM / ntfy.
- **Effect:** tells the server which APNs host to send to
  (`api.push.apple.com` or `api.sandbox.push.apple.com`). A mismatch gets
  `BadDeviceToken`. Server sender:
  [dmz006/datawatch#183](https://github.com/dmz006/datawatch/issues/183).
- **Persisted in:** not persisted. The token is kept in memory only (v1.28.0).

### Mobile-only preferences added in this range

| Preference | UI path | Persisted in | Notes |
|---|---|---|---|
| Theme (Dark / Light / System) | Settings → Theme | Device prefs | Light uses the web UI's light palette (Android v1.25.0, iOS v1.25.0) |
| Collapsed cards | Chevron on an Observer / Settings card header | Device prefs, per card id | All cards start expanded |
| Last tab + last session | Implicit | Device prefs | Reopened on launch |
| Alert dock mute (🔕) | Alert dock | In memory (app session) | |
| Splash last-shown time / version | Implicit | Device prefs | Splash shows on first launch, after an update, or after 24 h |

Reduced motion follows the system animation setting; the app has no separate
toggle.

---

## Auto (v0.33.0)

Auto is a **messaging-template** surface; no user-configurable
settings in v0.33. Host validation is build-type dependent:

- **Debug APK:** `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` (DHU-friendly).
- **Release APK:** strict allowlist from
  `auto/src/publicMain/res/values/hosts_allowlist.xml`.

---

## Wear

Wear never stores the bearer token. All server-scoped settings are
read from the paired phone via the Wearable Data Layer. No
Wear-specific configuration surfaces in v0.33.

---

## Export / import

Export bundle (Settings → General → **Session backup**) contains:

- Every `server_profiles` row (without `bearerToken` — user must
  re-enter).
- `biometricUnlock.enabled` (mobile-only pref).
- `sessionSort.order`.
- Saved commands (mobile-side mirror of the server list, de-duplicated
  by name on re-import).

The bundle is encrypted with a user-supplied passphrase (PBKDF2 +
AES-GCM). Import re-creates profiles and prompts for each bearer
token.

---

## PR hygiene

Every PR that adds, removes, or renames a field in
`shared/.../config/Settings.kt` **must** update this file in the same
commit.
