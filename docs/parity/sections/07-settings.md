# 07 — Settings page

PWA spec §8 · live `app.js` `renderSettingsView()` (A:6666) + `GENERAL/COMMS/LLM_CONFIG_FIELDS` (A:12255–12520) · Android `ui/settings/SettingsScreen.kt` (S) + `ui/configfields/ConfigFieldSchemas.kt` (CFS) + listed packages · iOS `screens/settings/*` (SV = SettingsView, SPL = ServerProfileListView, ASV/ESV = Add/EditServerView, SSV = SettingsSessionView), `security/BiometricGate.swift`.

Ref prefixes: **A** = `datawatch/internal/server/web/app.js`, **S** = `SettingsScreen.kt`, **CFS** = `ConfigFieldSchemas.kt`; other Android refs are composable names (grep-able). Audited 2026-10-04 against PWA v8.38.0, Android v1.23.120, iOS v1.23.120.

## Shell

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Settings view (bottom-nav tab, header "Settings") | ✓ A:6666 | ✓ S:SettingsScreen | ✓ SV | aligned | | |
| element | Six-tab bar General · Plugins · Comms · Compute · Automata · About (horizontal scroll) | ✓ A:6697–6703 | ✓ S:119 SettingsTab, PWA order General·Plugins·Comms·Compute·Automata·About | ✓ SV grouped list: six PWA groups as collapsible sections, cards as pushed rows | aligned | decided D31b | iOS native grouped list per D31b |
| nav | Active tab persisted (`cs_settings_tab`) with legacy-id migration (llm/agents→compute, monitor→general…) | ✓ A:6571–6577 | ✓ S:160–171 | ✓ SV collapsed-group state persisted (`settingsCollapsedGroups`) | aligned | | |
| interaction | Collapsible section cards (chevron, per-section state in localStorage) | ✓ A:6495 toggleSettingsSection | ✓ every card on the shared collapsible PwaCard (chevron, per-card state in SharedPreferences keyed by PWA section key, default expanded) | ✓ SV group sections collapse, state persisted (`settingsCollapsedGroups`) | aligned | decided D27a | Android 5a209324 |
| element | Per-card docs link (`settingsSectionHeader(id,title,docs)`) | ✓ A | ✓ PwaCard header "?" link on every card (PWA defsLink slug of the English card title) | ✓ SettingsCardScreen per-card DocsLinkButton (PWA defsLink slug) | aligned | decided D26a | Android 5a209324 |
| element | "Restart needed" after config save | ~ A:13939 inline "Restart now" link | ✓ inline "Restart required… Restart now" link after a save (banner removed) | ✓ RestartNeededRow inline "Restart now" | aligned | decided D57b | Android still shows the banner; D57b = inline link · per D57b (2026-10-04) |
| nav | Deep link into a settings tab (`navigate('plugins' / 'comms' / 'automata')`) | ✓ A:1880–1897 | ✓ SettingsNavChannel.request(tab) (AppRoot.kt:361/473) | ~ SettingsDeepLink.open(cardId:) → RootView switches to Settings, SettingsView pushes the card (used by the Automaton wizard skills hint); no URL route | aligned |  | iOS-E 2026-10-04: in-app jump exists; extend call sites as needed |
| token | Compact settings density (11–13 px labels; Android wraps tab in "settings-scale MaterialTheme") | ✓ A | ~ S custom smaller type scale | ✓ native insetGrouped + Dynamic Type | aligned | decided D32 | iOS-native density per D32 |
| element | Per-card loading / error states ("Loading…", red error line) | ✓ A | ✓ common_loading | ✓ SV/SSV ProgressView + error row | aligned | | |
| interaction | Destructive confirmations (delete server / LLM / node / secret, kill orphans) | ✓ showConfirmModal | ✓ AlertDialog | ✓ confirmationDialog on list deletes, restart, kill orphans, update, raw-config overwrite | aligned | | |
| string | Settings copy localized (PWA `t()` + locale JSON; Android 4 extra locales) | ✓ A:42 loadLocale | ✓ res/values-{de,es,fr,ja} | ✓ `L()` + Resources/{de,es,fr,ja}.lproj Localizable.strings (1,379 keys; e19306e2) | aligned | | |

## General tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Notifications card (permission status + Request Permission) | ✓ gc_notifs | ✓ NotificationsCard | ✓ SettingsNotificationsCard (UN permission + Request / Open iOS Settings) | aligned | | iOS: APNs permission UX, ties to #185 |
| element | Datawatch card: `session.log_level`, `server.auto_restart_on_config`, `session.backend_family` | ✓ 'dw' | ✓ CFS.Datawatch incl. `session.backend_family` | ✓ config card gc_dw (all 3 keys) | aligned | | Android drops `session.backend_family` |
| element | Auto-Update card: `update.enabled/schedule/time_of_day` | ✓ 'autoupdate' | ✓ CFS.AutoUpdate | ✓ config card gc_autoupdate | aligned | | |
| element | Session card (17 keys: max_sessions, reserved_interactive, capacity_wait_seconds, input_idle_timeout, tail_lines, alert_context_lines, default_project_dir, root_path, console_cols/rows, recent_session_minutes, auto_git_init/commit, kill_sessions_on_exit, mcp_max_retries, schedule_settle_ms, suppress_active_toasts) | ✓ 'sess' | ✓ CFS.Session | ✓ config card gc_sess (17 keys) | aligned | | |
| element | Summarizer card: `session.summarizer.enabled / llm_ref / model` + `POST /api/summarizer/test` | ✓ 'summarizer' | ✓ SummarizerCard | ✓ config card gc_summarizer (enabled / llm_ref / model) + Test | aligned | | |
| element | Whisper card: `whisper.enabled/backend/model/language/venv_path` + Test | ✓ 'whisper' | ✓ CFS.Whisper incl. `whisper.backend`; TestWhisperCard | ✓ config card gc_whisper (5 keys incl. backend, language read-only) + "Test transcription endpoint" → record / transcribe sheet (status line `ok (Nms, N chars)`, transcript) | aligned | | iOS-H 2026-10-05: Test added (PWA testWhisperBackend; silent-WAV health check not on Android, not added) |
| element | Docs Search card (query, results, pending + trusted sources, Export YAML) | ✓ docs_search | ✓ DocsSearchCard query + results + Pending sources (select all, Trust / Dismiss selected, per-row Trust / Dismiss) + Trusted sources (× remove, `core` locked) + "Export YAML" → modal with the server `yaml_snippet` + config.yaml paste hint; Add source removed | ✓ SettingsDocsSearchCard query + results + Pending + Trusted (swipe remove) + "Export YAML" sheet (snippet + hint) | aligned | operator 2026-10-05: Export YAML on both; Add source removed | PWA-M 2026-10-05: `docsTrustExportYaml` (GET /api/docs/trust/export); `docsTrustAdd` + `DocsTrustAddRequest` + test deleted (no callers left) |
| element | Session Templates card (list; Use / Edit / Delete) | ✓ templates | ✓ SessionTemplatesCard | ✓ list card (list / add / Edit / delete) + row ⋯ Use → NewSessionView prefilled (project dir, LLM backend, effort) | aligned |  | iOS edit via upsert POST /api/templates · iOS-E 2026-10-04: Android/PWA cards have no Use button (matrix PWA/Android cells overstate) |
| element | Device Aliases card | ✓ device_aliases | ✓ DeviceAliasesCard | ✓ list card (list / add / delete) | aligned | | |
| element | Backend Artifact Lifecycle (Tooling) card + ↻ | ✓ tooling | ✓ ToolingCard | ✓ list card tooling (gitignore / clean up actions) | aligned | | |
| element | File Service card (root path, peers, discussions) | ✓ file_service | ✓ FileServiceCard | ✓ SettingsFileServiceCard: editable root (Save) + "Leave blank…" hint, storage overview (discussions / peers counts), Upload file (Files picker + path), refresh | aligned | | iOS-H 2026-10-05 |
| element | Discussion Scopes card (scopes list, write message, New Discussion) | ✓ discussion_scopes A:23897 | ✓ DiscussionScopesCard | ✓ list card (scopes) + New Discussion (+) + Write message row | misaligned | | |
| element | Security card: biometric lock toggle | ✗ (platform n/a) | ✓ S:703 BiometricPrompt via FragmentActivity | ✓ toggle (auth required to enable) → `BiometricLockModifier` on the app root: locks on cold start + return from background; skipped without passcode | misaligned | | **iOS bug**: gate not enforced (re-checked 2026-10-04) · fixed 2026-10-04 |
| element | Secrets vault status | ✓ inside Secrets Store card (Compute) | ✓ `SecretsVaultStatusRow` inside Secrets Store card | ✓ vault status row inside Secrets Store list card (PWA placement) | misaligned | decided D33a | Android must fold it into SecretsCard per D33a · Android must fold it into SecretsCard per D33a · per D33a (2026-10-04) |
| element | Config Viewer card (read-only effective config) | ✗ | ✗ removed | ✗ removed | n/a | operator 2026-10-05 | Removed from both apps: duplicated the editable Settings cards. Raw config card kept. PWA: do not add (#172 updated) |
| element | Raw config editor card | ✗ | ✓ RawConfigCard | ✓ SettingsRawConfigCard — diffed dotted-key PUT, masked values never sent | pwa-missing | decided D79a | → #172 |
| element | Encryption status card (local DB cipher / keystore, file list) | ✗ (no local DB) | ✓ EncryptionStatusCard (About tab) | ✓ SettingsEncryptionCard: Data Protection class per file, Keychain accessibility + token count, server secure_mode | aligned | decided D90a | PWA n/a (no local store) |

## Comms tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Authentication card: browser token + Save & Reconnect, `server.token`, `mcp.token` | ✓ comms_auth A:6712 | ~ CFS.CommsAuth (`server.token`, `mcp.token`; browser token n/a) | ✓ config card comms_auth (server.token / mcp.token, secure + never displayed); browser token n/a | aligned | | |
| element | Servers card: connection dot, "This server", server info | ✓ servers A:6735 | ✓ ServersCard (multi-profile list) | ✓ Comms › Servers → SPL | misaligned | | PWA is single-server by nature; apps list profiles |
| element | Remote servers + Federated Peers cards (list / add / test / enable / delete) | ✓ remote_servers A:14523, fedpeers | ✓ FederationPeersCard | ✓ remote_servers (list / add / delete / ⋯ Test via POST /api/servers/{name}/test / enable switch via PUT round-trip; built-ins read-only) + fedpeers ✓ | aligned |  | remaining iOS gap: remote_servers test / enable · iOS-E 2026-10-04 |
| interaction | Add server profile (name, https URL, bearer token / no-token, trust) with probe before save | n/a | ✓ AddServerScreen | ✓ ASV | aligned | | PWA n/a |
| interaction | Edit / delete profile ("leave blank to keep" token, delete confirm) | n/a | ✓ EditServerScreen | ✓ ESV | aligned | | |
| interaction | Certificate pinning (TOFU: probe leaf fingerprint → confirm → `trustAnchorSha256`) | n/a (browser trust store) | ✓ Add/Edit `ServerTrustSection` + `probeServerCertificate` + `PinnedTrustManager` (REST, WS, Auto, docs WebView); hostname verification kept | ✓ ASV/ESV ServerTrustSection, CertProbe, IosTls | aligned | decided D91a | both apps pin; browser n/a |
| interaction | Trust-all certificates toggle (insecure) | n/a | ✓ selfSigned Switch | ✓ ASV/ESV | aligned | | |
| interaction | Download server CA cert (`GET /api/cert`) + install guidance | ~ websrv `_tls_install` hint | ✓ ServersCard menu "Download CA cert" + CertInstallCard | ✓ ESV Download + ShareLink | aligned | | |
| element | Profile-row security badges | n/a | ✓ no auth · trust-all TLS + 🔒 certificate pinned | ~ NO AUTH + TRUST ALL TLS; no pinned badge | aligned | | Android adds 🔒 pinned badge (d4a142ec); iOS lacks it — minor, not decided (D91c not chosen) |
| element | Web Server card: `server.enabled/host/port/tls/tls_port/tls_auto_generate/tls_cert/tls_key/channel_port` | ✓ websrv | ✓ CFS.WebServer | ✓ config card cc_websrv (interface picker) | aligned | | |
| element | MCP Server card: `mcp.enabled/sse_enabled/sse_host/sse_port/tls_*` | ✓ mcpsrv | ✓ CFS.McpServer | ✓ config card cc_mcpsrv | aligned | | |
| element | Communication Configuration: per-backend cards (Signal, Telegram, Discord, Slack, Matrix, Ntfy, Email, Twilio, GitHub webhook, Webhook, DNS) toggle + config popup | ✓ backends A:6766, A:14028 | ✓ ChannelsCard + Backend/ChannelConfigDialog (ChannelBackendSchemas) | ✓ SettingsCommBackendsCard: per-service toggle + configure (PWA BACKEND_FIELDS) | aligned | | |
| interaction | Signal device linking (Link Device → QR + instructions) | ✓ startLinking | ✓ SignalLinkingDialog | ✓ SignalDeviceSection in Communication Configuration (status + Link Device → sheet; POST /api/link/start + /api/link/stream SSE; `sgnl://` URI rendered with CoreImage CIQRCodeGenerator) | aligned | | |
| element | Proxy Resilience card (`proxy.enabled/request_timeout/health_interval/circuit_breaker_threshold/circuit_breaker_reset/offline_queue_size`) | ✓ proxy A:14230 | ✓ CFS.Proxy | ✓ config card proxy | aligned | | |
| element | Routing Rules card | ✓ routing_rules A:24370 | ✓ RoutingRulesCard | ✓ list card (list / add / delete) | aligned | | |
| element | Channel Routing card | ✓ channel_routing A:24413 | ✓ ChannelRoutingCard | ✓ list card (list / add / edit / delete) | aligned | | |
| element | Push Notifications card: registrations, Register endpoint URL, Send test, status | ✓ push_notifications A:24512 | ~ PushNotificationsCard + delivery-tier row (UnifiedPush / CommChannel / Background) | ✓ SettingsPushCard: APNs token + server registration status, re-register, send test | aligned | decided D88c | iOS APNs card per D88c; Android tier row is app-only |
| element | Federated Peers card | ✓ fedpeers | ✓ FederationPeersCard | ✓ list card fed_peers (enabled switch, URL, capability badge, Test, add / edit / delete) | aligned | | |

## Compute tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | LLMs registry (rows: name, auto badge, kind, tags, switch, ✏️/×, JSON expand, "In use…" with filter/pagination) | ✓ llms A:9477 | ✓ LlmRegistryCard, LlmDetailDialog, OllamaMarketplaceDialog | ~ list card llms (name, kind·model, tags, enable switch, delete, JSON detail via long-press, ⋯ "In use…" → paged sessions sheet) + LlmFormSheet + Ollama marketplace link; no `auto_created` badge | misaligned |  | iOS-H 2026-10-05: In use… added. Remaining: PWA `auto_created` badge on neither app — LlmRegistryEntryDto lacks the field and is reused as the PUT body (adding it risks round-tripping the flag) → needs decision |
| interaction | LLM create/edit form (§8.9 full field list, kind-dependent sections, ComputeNodes multi-select, Test row, `</> YAML` escape hatch) | ✓ buildLLMForm A:8399 | ~ LlmRegistryDialog (LlmBackendSchemas) + `</> YAML` (left of Cancel) → `LlmYamlDialog` raw editor | ~ LlmFormSheet full PWA field set … + `</> YAML` section → `LlmJsonSheet` | misaligned |  | PWA-M 2026-10-05: `</> YAML` done on both — like the PWA openFormEditPopup it edits GET /api/llms/{name} as pretty JSON (PWA "YAML" view is JSON.stringify/parse; no YAML lib), Test = save + /test, Save = PUT then the form reopens; add mode says "YAML editor available after first save". Field-by-field Android check still pending |
| element | Compute Nodes panel (rows: name, auto badge, deprecated-Kind ⚠, address, capacity, tags, switch, ✏️/📡/×, dimmed when disabled; Kind-migration banner) | ✓ A:7897 | ~ ComputeNodesCard (kind-migration status via LlmRegistryCard MigrationStatusDto) | ~ list card compute_nodes (kind·address, auto badge, tags, switch, delete, dimmed) + ComputeNodeFormSheet add/edit (kind, address, routing docker/proxy, observer peer, hardware, computed max, Test Connection) + Kind-migration section (per-node migrate, dismiss) + Ollama marketplace (catalog, pull to node, progress); no 📡 detail | misaligned | | |
| interaction | Add / Edit ComputeNode panel-modal | ✓ buildComputeNodeForm A:8119 | ✓ ComputeNodeDialog | ✓ ComputeNodeFormSheet (PWA openComputeAddPanel fields incl. routing + hardware; edit overlays GET record); no Ollama models sub-list / YAML | misaligned | | |
| element | Cost Rates card (USD / 1K tokens per model, Save) | ✓ costrates A:25262 | ✓ CostRatesCard | ✓ list card (list / add-or-replace / delete) | aligned | | |
| element | Cluster Profiles card (list, Edit / Delete, form ↔ YAML) | ✓ gc_clusterprofiles A:15770 | ✓ KindProfilesCard(cluster) list · Smoke · Edit · Delete (confirm) · + Add → `ProfileEditorDialog`: full PWA cluster form + "YAML view"/"Form view" toggle | ✓ list card (list / add / Edit / delete) → `ProfileEditorSheet`: native form + overflow-menu "YAML view"/"Form view" | aligned | | Task N 2026-10-05: both apps share `profiles/ProfileForm` (PWA fields/defaults, server-mirrored validation) + `yaml/ProfileYaml` (lossless round trip, unknown keys kept, inline parse errors); secrets masked via SecretMask. iOS cluster rows still lack the PWA Smoke button |
| element | Memory card (18 `memory.*` keys) | ✓ LLM 'memory' | ✓ CFS.Memory | ✓ config card lc_memory (PWA 17 fields) | aligned | | |
| element | RTK card (`rtk.enabled/binary/show_savings/auto_init/auto_update/update_check_interval/discover_interval`) | ✓ LLM 'rtk' | ✓ CFS.LlmRtk | ✓ config card lc_rtk | aligned | | |
| element | Web Search: config (`web_search.enabled/cache_enabled/cache_ttl_seconds`) + Providers registry (name, type, SearXNG URL, API-key secret ref, default results, enabled) | ✓ 'web_search' + websearch_providers A:12731 | ✓ CFS.WebSearch (enabled / cache_enabled / cache_ttl_seconds) + WebSearchRegistryCard | ~ config card lc_web_search (PWA keys) + providers list card (switch / test / add / edit / delete; API key write-only) | misaligned | | Android config keys drift from PWA · Android config keys drift from PWA · iOS lacks provider add/edit (2026-10-04) |
| element | Goose / OpenCode / Vision LLM config cards (`goose.*`, `opencode.default_model`, `vision.*`) | ✓ LLM goose/opencode/vision | ✓ config cards lc_goose / lc_opencode / lc_vision (PWA keys; order memory → goose → opencode → web_search → rtk → vision) | ✓ config cards lc_goose / lc_opencode / lc_vision | aligned | | Android vision added 2026-10-04 (2026-10-04) · Android-I 2026-10-05: Android aligned |
| element | Container Workers card (`agents.*` 7 keys) | ✓ gc_agents A:7766 | ✓ CFS.Agents | ✓ config card gc_agents | aligned | | |
| element | Detection Filters card | ✓ detection A:20770 | ✓ DetectionFiltersCard | ✓ config card detection (4 pattern lists + timing) | aligned | | |
| element | Alert Rules card (CRUD + firings) | ✓ alert_rules | ✓ AlertRulesCard | ✓ AlertRulesView (list / add / enable / delete) + "Recent Firings (N)" ×20 | pwa-missing | decided D70a | PWA has CRUD but no firings → #172; no client edits rules |
| element | Saved Commands card | ✓ cmds | ✓ SavedCommandsCard | ✓ SavedCommandsView re-homed to Compute | aligned | | |
| element | Output Filters card | ✓ filters | ✓ FiltersCard | ✓ FiltersView re-homed to Compute | aligned | | |
| element | Exit Hooks card | ✓ exit_hooks | ✓ `ExitHooksCard` (S:389) | ✓ SettingsExitHooksCard (list, enable switch, swipe delete, Add Exit Hook form) | aligned | | Android d4a142ec |
| element | Work Queue card | ✓ work_queue | ✓ `WorkQueueCard` (S:390) | ✓ SettingsWorkQueueCard (role / state filters, list, delete, Push Work Item) | aligned | | Android d4a142ec |
| element | Tailscale: status + config cards, Generate Auth Key, ACL Generate / Generate & Push | ✓ A:24165, A:24186 | ✓ TailscaleSettingsCard + TailscaleMeshCard | ~ config card tailscale_config + list card Mesh Status (Generate Auth Key → clipboard, never displayed; Generate ACL / Generate & Push) | aligned | | |
| element | Secrets Store card (vault status, list, add/update: name, value, tags, description, scopes) | ✓ secrets_store A:24137 | ✓ SecretsCard | ✓ list card (vault status row, list, add, delete) | aligned | | |
| element | Federated Observer quicklink (mode + peers, "Open Observer view →") | ✓ observer_quicklink | ✓ ObserverQuicklinkCard | ✓ Compute › "Federated Observer" card: "Open Observer view →" (switches to the Observer tab via `datawatch://observer`) + PWA help text | aligned | | iOS-H 2026-10-05 |

## Automata tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Identity card (role, goals, projects, values, focus, notes) + wizard | ✓ identity | ✓ IdentityCard + IdentityWizardSheet | ✓ SettingsIdentityCard form + "🤖 Open Identity Wizard" (IdentityWizardSheet) | aligned |  | iOS-E 2026-10-04 |
| element | Algorithm Mode card | ✓ algorithm | ✓ AlgorithmModeCard | ✓ Settings › Automata › Algorithm Mode (session list, 7-phase strip, output field, Advance / Edit / Abort / Reset) | aligned | | |
| element | Evals card (suites, runs, Run) | ✓ evals | ✓ EvalsCard | ✓ list card (suites + Run) + "Recent Runs" section (PASS/FAIL, name, time, score; reloads after a Run) | aligned | | iOS-H 2026-10-05 |
| element | Council panel (persona checkboxes, proposal, Quick/Debate, live SSE runs, recent 5, subsystem config, persona modal + 🤖 wizard) | ✓ council A:26111 | ✓ CouncilCard (all personas pre-checked, Quick (1 round)/Debate (3 rounds), Run → CouncilLiveRunSheet live SSE watch w/ cancel, Recent Runs ×5 → replay) + CouncilPersonaWizardSheet | ✓ list card personas (list / add / edit / delete) + Council runs (proposal, Quick/Debate, persona toggles, Run → CouncilLiveRunSheet live SSE watch w/ cancel, Recent Runs ×5 → replay) + Council settings + 🤖 Persona wizard | aligned |  | Council live runs 2026-10-05 (operator-approved): shared CouncilSseLineParser + watchCouncilRun on `/api/council/runs/{id}/events`; both apps |
| element | Project Profiles card (list, Edit / Smoke Test / Delete, form ↔ YAML) | ✓ gc_projectprofiles | ✓ KindProfilesCard(project) list · Smoke · Edit · Delete (confirm) · + Add → `ProfileEditorDialog`: full PWA project form (git, image pair, memory, spawn budgets, Agent Settings, skills) + "YAML view"/"Form view" toggle | ✓ list card (list / add / Edit / Smoke test / delete) → `ProfileEditorSheet`: native form + overflow-menu "YAML view"/"Form view" | aligned | | Task N 2026-10-05: shared `ProfileForm` + `ProfileYaml` (see Cluster Profiles row); create = POST, edit = PUT |
| element | Pipeline Manager card | ✓ pipelines A:25649 | ✓ PipelineManagerCard | ✓ PipelinesView re-homed to Automata | aligned | | |
| element | Automata Orchestrator (graphs) card | ✓ orchestrator_graphs A:24574 | ✓ OrchestratorGraphsCard | ✓ OrchestratorGraphsView re-homed to Automata | aligned | | |
| element | Guardrail Library card | ✓ automata_scan A:24770 | ✓ GuardrailLibraryCard + ScanConfigCard | ✓ list card: name, description, type badge (default `scan`); scan config lives in the Autonomous Config card (Scan defaults section) | aligned |  | iOS-H 2026-10-05: `GuardrailLibraryItemDto.kind` now reads the server `type` field (badge was always empty on both apps) |
| element | Guardrail Profiles card | ✓ A:24789 | ✓ own PwaCard `automata_guardrail_profiles` (GuardrailLibraryCard.kt card 2) | ✓ list card (list / add / edit / delete) | aligned |  | Android still folds profiles into the library card · Android-I 2026-10-05: stale note — Android already a separate card |
| element | Autonomous Config card (26 `autonomous.*` keys incl. `verification_backends` editor, quality gates, capacity, per-task/story guardrails, injection guard) | ✓ automata_autonomous | ✓ AutonomousConfigCard + CFS.Autonomous incl. planning_*, verification_model, capacity_*, max_recursion_depth, auto_approve_children, per_task/per_story_guardrails, injection_guard, block_on_injection | ✓ config card gc_autonomous (PWA 27 keys) | misaligned | | verify key set against server config schema |
| element | Pipelines config (`pipeline.max_parallel/default_backend`) | ✓ 'pipeline' | ✓ CFS.Pipelines | ✓ config card gc_pipeline | aligned | | |
| element | Orchestrator config (`orchestrator.enabled/guardrail_backend/guardrail_model/guardrail_timeout_ms/max_parallel_prds`) | ✓ 'orchestrator' | ✓ CFS.Orchestrator incl. `guardrail_model` | ✓ config card gc_orchestrator (incl. guardrail_model) | misaligned | | |
| element | Skill registries card (list, add/edit, connect, sync, browse) | ✓ automata_skills A:24852 | ✓ SkillRegistriesCard | ✓ list card (list / add / edit url+branch / Connect / delete) + ⋯ Browse → SkillBrowseSheet (available skills, tap to sync/unsync, Sync all) | aligned |  | iOS-E 2026-10-04 |
| element | Automata Type Registry card (list, create with label/id/color, delete) | ✓ automata_type_registry A:24702 | ✓ AutomataTypesCard (Settings › Automata) | ✓ Settings › Automata › Type Registry (removed from Automata tab) | aligned | decided D25a | |
| element | Automata settings panel (defaults: guided, priority, type, skills, read/write dirs) | ✓ A:24627 | ~ partially in AutonomousConfigCard / PRD settings | ✓ Autonomous Config card (automata_autonomous): poll interval, max parallel tasks, auto-fix retries + Scan defaults (/api/autonomous/scan/config toggles, fail-on severity, max findings, fix-loop retries) — matches live PWA loadAutomataSettingsPanel | aligned | | live PWA panel = autonomous config + scan config; the guided/priority/type/skills/dirs list is per-PRD (PRD settings modal) |

## Plugins tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Plugin framework config (`plugins.enabled/dir/timeout_ms`) | ✓ 'plugins' | ✓ CFS.Plugins | ✓ config card gc_plugins | aligned | | |
| element | Plugin Manager (installed list, enable/disable, test, reload, run subcommand) | ✓ plugins_list A:24307 | ✓ `InstalledPluginsCard` — Native/Subprocess, Enable/Disable, Reload (PWA panel has no test / run subcommand either) | ~ list card plugins (native + subprocess, enable/disable switch, Reload plugins); no test / run subcommand | aligned | | iOS also lacks test / run subcommand |
| element | Plugins status list (shared with Observer) | ✓ | ✓ PluginsCard (Observer) | ~ Settings › Plugins › Plugin Manager list (Observer copy: section 06) | misaligned | | see section 06 |

## About tab

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| element | Server version (from `/api/health`, linked to GitHub release) | ✓ aboutVersion A:9938 | ✓ AboutCard "Version" row: `vX.Y.Z` in accent2, tap opens its GitHub release (replaces "Connected to host · version") | ✓ SettingsAboutCard server version `vX.Y.Z` in accent2, taps open its GitHub release | aligned | | iOS-H 2026-10-05: iOS linked. Android-J 2026-10-05: Android matches (hostname dropped, as in PWA/iOS) |
| element | Client app version / build | n/a | ✓ "App version" | ✓ SettingsAboutCard App version | aligned | | |
| element | Links: Project · Mobile app · store · Docs | ✓ settings_project / settings_mobile_app | ✓ Project, Mobile app, Play Store, Docs | ~ Project, Mobile app, Docs (no App Store link yet) | misaligned | | iOS: add App Store link once listed |
| element | Sessions count ("in store"), Uptime, Daemon status | ✓ settings_sessions / settings_daemon | ✓ Sessions, Uptime, Connected to | ✓ SettingsAboutCard sessions in store + uptime | aligned | | |
| element | Orphaned tmux sessions + Kill all (N) | ✓ aboutOrphanedTmux A:9950 | ✓ KillOrphansCard | ✓ SettingsAboutCard list + Kill all (N) with confirm | aligned | | |
| element | Language override (auto + en/de/es/fr/ja) | ✓ settings_language / settings_lang_auto | ✓ LanguagePickerCard | ✓ About › Language row opens iOS per-app language (system Settings); app localized en/de/es/fr/ja | aligned | | native per-app language control is the iOS equivalent |
| element | Theme Dark / Light / System (`cs_theme`) | ✓ themePickerAbout A:16200 | ✓ ThemePickerCard (ThemeMode) | ✓ About › ThemePickerRow (`dw.theme`, Dark/Light/System, PWA light palette; 16ed1a85) | aligned | | |
| element | Branding / Splash (tagline + logo path → `session.splash_tagline` / `session.splash_logo_path`) | ✓ A:25502–25534 | — not added | ✗ | n/a | | iOS also missing · PWA removed the Branding/Splash card in v6.12.0 (`loadBrandingPanel` is dead code) |
| element | Update: Check now → Update button + progress overlay | ✓ checkForUpdate / runUpdate A:13596 | ✓ UpdateDaemonCard | ✓ SettingsAboutCard Check now → Update (confirm) | aligned | | |
| element | Restart daemon | ✓ restartDaemon A:14222 | ✓ RestartDaemonCard | ✓ SettingsAboutCard Restart (confirm) | aligned | | |
| element | Subsystem reload card | ✗ | ✓ SubsystemReloadCard | ✓ SettingsSubsystemReloadCard (config / filters / memory) | pwa-missing | decided D80a | → #172 |
| element | API links card (endpoint list) | ✓ 'api' header | ✓ ApiLinksCard | ✓ SettingsApiLinksCard (Swagger, OpenAPI, docs, MCP tools) | aligned | | |
| element | MCP channel card + MCP tools card | ✗ | ✓ McpChannelCard + McpToolsCard mounted in About | ✓ SettingsMcpChannelCard + SettingsMcpToolsCard | pwa-missing | decided D80a | → #172; Android: mount McpToolsCard in About · Android done (D80a) (2026-10-04) |

## Coverage
rows: 97 · aligned: 78 · ios-missing: 0 · android-missing: 0 · pwa-missing: 4 · misaligned: 13 · n/a: 2

Re-audited 2026-10-04 against current code (after B26–B32, 16ed1a85 iOS theme, e19306e2 iOS i18n, d4a142ec Android exit hooks / work queue / pinned badge, 5a209324 Android collapsible cards + docs links). iOS settings depth pass (2026-10-04): Exit Hooks, Work Queue and list-card CRUD landed. Remaining iOS gaps: Observer quicklink, in-app jump into a Settings group, biometric lock not enforced, and the `~` list cards (templates Use, council live SSE / wizard, remote-server test, skill browse / sync, LLM In-use/YAML, compute 📡 detail). Remaining Android gaps: Plugin Manager, D33a vault placement, D57b inline restart link, config-key drift (backend_family, whisper.backend, web_search cache_*, vision.*, orchestrator.guardrail_model, autonomous planning/capacity/guardrail keys), unmounted McpToolsCard.

## Decisions (resolved 2026-10-04)
1. iOS Settings structure → **D31b** native grouped list with the six PWA groups as sections.
2. Collapsible section cards → **D27a** both apps collapse with persisted state.
3. Restart-needed signalling → **D57b** apps adopt the PWA inline "Restart now" link.
4. Settings density → **D32** iOS-native density (sanctioned).
5. Secrets vault status placement → **D33a** Android moves it into the Secrets card.
6. Config Viewer + Raw config editor → **D79a** add to PWA + iOS (PWA via #172). **Revised by operator 2026-10-05:** Config Viewer removed everywhere (duplicates the Settings cards); only the Raw config card stays.
7. iOS encryption-status card → **D90a** show Data Protection class + Keychain state.
8. Certificate pinning → **D91a** Android adopts TOFU pinning.
9. Push card → **D88c** iOS card = APNs registration status + test.
10. Automata Type Registry placement → **D25a** Settings › Automata.
11. Subsystem reload + MCP channel/tools cards → **D80a** add to PWA (#172).
