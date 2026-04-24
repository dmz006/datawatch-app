# Server endpoint + config contract (2026-04-23)

Authoritative reference pulled from `/home/dmz/workspace/datawatch/internal/server/api.go` and `server.go`. Every row below was verified against the Go source and a live `curl -sk https://localhost:8443/...` where a GET was safe.

**Convention.** The server does **not** use REST-style path params for session mutations. All mutation endpoints are `POST` with a JSON body keyed on `id` (the full server-scoped id, e.g. `"ring-2db6"`). This was the root cause of the v0.34.6 P0 releases.

---

## 1. Session lifecycle

| Endpoint | Method | Body | Response | Handler | Used by |
|---|---|---|---|---|---|
| `/api/sessions` | GET | — | `[{id, full_id, name, task, state, hostname, llm_backend, output_mode, input_mode, ...}]` (bare array, not `{sessions:[]}`) | api.go:handleSessions | PWA + Android |
| `/api/sessions/start` | POST | `{task, project_dir, backend, name, resume_id, profile, auto_git_commit, auto_git_init, effort, template, project}` | `{session_id: fullId, ...}` | api.go:handleStartSession (1869) | PWA + Android |
| `/api/sessions/kill` | POST | `{"id": fullId}` | `{"status":"ok"}` | api.go:handleKillSession (1824) | PWA + Android |
| `/api/sessions/delete` | POST | `{"id": fullId, "delete_data": bool}` | `{"status":"ok"}` | api.go:handleDeleteSession (1846) | PWA + Android |
| `/api/sessions/restart` | POST | `{"id": fullId}` | updated SessionDto | api.go:handleRestartSession (1985) | PWA + Android |
| `/api/sessions/rename` | POST | `{"id": fullId, "name": "..."}` | `{"status":"ok"}` | api.go:handleRenameSession (1723) | PWA + Android |
| `/api/sessions/state` | POST | `{"id": fullId, "state": "..."}` | `{"status":"ok"}` | api.go:handleSetSessionState (1796) | PWA + Android |
| `/api/sessions/timeline?session_id=...` | GET | — | `{events: [...]}` | api.go:handleSessionTimeline | PWA + Android |
| `/api/sessions/response?session_id=...` | GET | — | `{last_response: "..."}` | api.go:handleSessionResponse | PWA only (Android reads from SessionDto) |
| `/api/sessions/prompt?session_id=...` | GET | — | prompt detail | api.go:handleSessionPrompt | PWA only |
| `/api/sessions/bind` | POST | `{id, agent_id}` | `{"status":"ok"}` | api.go:handleBindSessionAgent | neither regularly |
| `/api/sessions/reconcile` | POST | `{...}` | reconcile result | api.go:handleSessionReconcile (BL93) | neither regularly |
| `/api/sessions/import` | POST | `{...}` | import result | api.go:handleSessionImport (BL94) | neither |
| `/api/sessions/stale` | GET | `?mode=...` | stale list | api.go:handleSessionsStale (BL40) | operator |
| `/api/sessions/aggregated` | GET | — | federated list | api.go:handleAggregatedSessions | federation |

**ID form.** `full_id` = `"<hostname>-<shortId>"`, e.g. `"ring-2db6"`. SessionDto carries both `id` (short) and `full_id`. Server's `m.store.Get(fullID)` keys on the full form (manager.go:1265). Short id is for display only; never send it to mutation endpoints.

**⚠ Android dead code.** `/api/sessions/reply` is in Android's RestTransport but the server does NOT expose it (per backlog `reply-send-404` closed in v0.33.22). Android composer uses WS `send_input` instead. The dead REST route can be removed.

---

## 2. Config

| Endpoint | Method | Body | Response | Notes |
|---|---|---|---|---|
| `/api/config` | GET | — | full deeply-nested config (passwords masked as `"***"`) | app.js fetches on mount + on WS reconnect |
| `/api/config` | PUT | partial config tree with dot-paths OR flat `{"dot.path": value}` | `{"status":"ok"}` | Auto-save on field change in PWA (ConfigFieldsPanel parity on Android) |

**Top-level config keys** (from handleGetConfig):
- `hostname`
- `server.*` — enabled, host, port, public_url, token, tls, tls_auto_generate, tls_cert, tls_key, channel_port, tls_port, auto_restart_on_config, recent_session_minutes, suppress_active_toasts
- `signal.*` — enabled, account_number, group_id, config_dir, device_name
- `session.*` — max_sessions, input_idle_timeout_sec, tail_lines, alert_context_lines, default_project_dir, file_browser_root, llm_backend
- `datawatch.*` — log_level, auto_restart_on_config, default_llm_backend
- `auto_update.*` — enabled, schedule, time_of_day
- `backends.<name>.*` — each LLM backend's config (enabled + LLM_FIELDS per backend)
- `messaging.<type>.*` — each channel's config (BACKEND_FIELDS per channel type)
- `memory.*` — enabled, backend, embedder, encryption, etc.
- `notifications.*` — push/ntfy config
- `mcp.*` — addr, enabled, token
- `rtk.*`, `pipelines.*`, `autonomous.*`, `plugins.*`, `orchestrator.*`, `whisper.*`, `web_server.*`
- `profiles.projects.*`, `profiles.clusters.*`
- `federation.*`
- `proxy.*`

---

## 3. Channels + messaging

| Endpoint | Method | Body | Notes |
|---|---|---|---|
| `/api/channels` | GET | — | returns active channel instances |
| `/api/channel/send` | POST | `{channel, session_id, text}` | PWA + Android |
| `/api/channel/reply` | POST | `{...}` | reply wiring |
| `/api/channel/notify` | POST | `{...}` | notify broadcast |
| `/api/channel/ready` | GET | — | channel readiness |

**Per-channel config** lives under `messaging.<type>.*` in `/api/config`. Field schemas in PWA at `BACKEND_FIELDS` (app.js:4290-4302):
- `telegram`: token, chat_id, auto_manage_group
- `discord`: token, channel_id, auto_manage_channel
- `slack`: token, channel_id, auto_manage_channel
- `matrix`: homeserver, user_id, access_token, room_id, auto_manage_room
- `ntfy`: server_url, topic, token
- `email`: host, port, username, password, from, to
- `twilio`: account_sid, auth_token, from_number, to_number, webhook_addr
- `github_webhook`: addr, secret
- `webhook`: addr, token
- `signal`: group_id, config_dir, device_name
- `dns_channel`: mode, domain, listen, upstream, secret, ttl, max_response_size, poll_interval, rate_limit

Android's `ChannelBackendSchemas.kt` should match. Cross-check as part of G42 (already done in v0.34.4 — re-verify).

---

## 4. LLM backends

| Endpoint | Method | Response | Notes |
|---|---|---|---|
| `/api/backends` | GET | `{llm: [{name, ...}, ...], active: "..."}` OR older `{llm: ["name", ...]}` | Both shapes accepted by Android (RestTransport.kt:194-219) |
| `/api/backends/active` | POST/PUT | `{name: "..."}` | Set active backend |
| `/api/ollama/models` | GET | `[{name, size, ...}]` | For Ollama model dropdown |
| `/api/openwebui/models` | GET | `[{id, name, ...}]` | For OpenWebUI model dropdown |
| `/api/ollama/stats` | GET | ollama runtime stats | Monitor card source |

**Per-backend config schema** from PWA `LLM_FIELDS` (app.js:4262-4282). Only these backends have schemas on server:
- `claude-code`: claude_code_bin, claude_enabled, skip_permissions, channel_enabled, fallback_chain, GIT_FIELDS, console_cols, console_rows
- `aider`: binary, GIT_FIELDS, CONSOLE_SIZE_FIELDS
- `goose`: binary, GIT_FIELDS, CONSOLE_SIZE_FIELDS
- `gemini`: binary, GIT_FIELDS, CONSOLE_SIZE_FIELDS
- `ollama`: model (dropdown from /api/ollama/models), host, GIT_FIELDS, CONSOLE_SIZE_FIELDS
- `opencode`: binary, GIT_FIELDS, CONSOLE_SIZE_FIELDS
- `opencode-acp`: binary, acp_startup_timeout, acp_health_interval, acp_message_timeout, GIT_FIELDS, CONSOLE_SIZE_FIELDS
- `opencode-prompt`: binary, GIT_FIELDS, CONSOLE_SIZE_FIELDS
- `openwebui`: url, api_key, model (dropdown), GIT_FIELDS, CONSOLE_SIZE_FIELDS
- `shell`: script_path, GIT_FIELDS, CONSOLE_SIZE_FIELDS

**⚠ Android has extra schemas** (`anthropic`, `openai`, `groq`, `openrouter`, `xai`) that the server doesn't support. They're dead config; drop or mark as "future" per G45.

**Available backends list** (from `/api/info` `available_backends` array on current server): `aider, claude-code, gemini, goose, ollama, opencode, opencode-acp, opencode-prompt, openwebui, shell`.

---

## 5. Alerts

| Endpoint | Method | Body | Response |
|---|---|---|---|
| `/api/alerts` | GET | — | `{alerts: [{id, type, severity, message, session_id, created_at, read, level, title, body}], unread_count}` |
| `/api/alerts` | POST | `{id, read}` or `{all: true}` | mark-read flow |

Level values observed: `error`, `warn`, `info`. Severity optional.

Classification in PWA (app.js:5550):
- **Active** alerts = alert whose session is in set (running, waiting_input, rate_limited, new) AND session exists in `/api/sessions` response
- **Inactive** alerts = everything else, including system-level (`session_id == "__system__"`)

---

## 6. Stats + monitoring

| Endpoint | Method | Response |
|---|---|---|
| `/api/stats` | GET | flat + structured CPU / memory / disk / swap / gpu / sessions / uptime / etc. (see `StatsDto` in Android DTOs; PWA reads `cpu_load_avg_1 / cpu_cores / mem_used / mem_total / disk_used / disk_total / swap_* / gpu_*`) |
| `/api/stats/kill-orphans` | POST | kills stuck tmux sessions |
| `/api/interfaces` | GET | network interface list |
| `/api/info` | GET | `{hostname, version, llm_backend, messaging_backend, session_count, available_backends, server: {host, port}}` |
| `/api/health` | GET | `{ok, version}` |
| `/api/rtk/version`, `/api/rtk/discover`, `/api/rtk/check`, `/api/rtk/update` | GET/POST | RTK token-saving + self-update endpoints |

---

## 7. Schedules + commands + filters

| Endpoint | Method | Response |
|---|---|---|
| `/api/schedules` | GET/POST/PUT/DELETE | CRUD for scheduled commands |
| `/api/schedule` | POST | add single (legacy) |
| `/api/commands` | GET/POST/PUT/DELETE | Saved command CRUD |
| `/api/filters` | GET/POST/PUT/DELETE | Output filter CRUD |

---

## 8. Files + profiles

| Endpoint | Method | Response |
|---|---|---|
| `/api/files?path=...` | GET | `{entries: [{name, is_dir, ...}], ...}` |
| `/api/profiles` | GET | `[{name, ...}]` |
| `/api/profiles/projects`, `/api/profiles/clusters` | GET/POST/DELETE | Project/cluster profile CRUD |

---

## 9. Federation + servers

| Endpoint | Method | Response |
|---|---|---|
| `/api/servers` | GET | list of registered peer servers |
| `/api/servers/health` | GET | per-peer health |
| `/api/federation/sessions` | GET | fan-out session list |

---

## 10. Device linking (Signal QR)

| Endpoint | Method | Response | Android status |
|---|---|---|---|
| `/api/link/start` | POST | starts a linking attempt | **not implemented** (G41 / BL21) |
| `/api/link/stream` | SSE | stream of QR frames | not implemented |
| `/api/link/status` | GET | linking completion status | not implemented |

---

## 11. TLS + security

| Endpoint | Method | Response |
|---|---|---|
| `/api/cert` | GET | server CA cert bytes (for installation on clients) |
| `/api/restart` | POST | daemon restart |
| `/api/update` | POST | daemon update (RTK self-update path) |

---

## 12. WebSocket frames (`/ws`)

From `EventMapper.kt` + app.js handlers. Frame shape: `{type, data, timestamp}`.

| type | data fields | PWA handler | Android handler |
|---|---|---|---|
| `pane_capture` | `{session_id, lines: [string]}` | renderPaneCapture | buildPaneCaptureEvents → SessionEvent.PaneCapture |
| `raw_output`, `output` | `{session_id, lines: [string]}` OR `{session_id, body: string}` | append to output buffer | buildOutputEvents → SessionEvent.Output |
| `needs_input`, `prompt`, `prompt_detected` | `{session_id, prompt, prompt_kind, message}` | input banner | buildPrompt → SessionEvent.PromptDetected |
| `state_change`, `session_update` | `{id, state}` | re-render | SessionEvent.StateChange |
| `notification` | `{session_id, message}` | toast | buildNotification → system-colored Output |
| `alert` | `{session_id, message, summary}` | mark in list | buildAlert → system-colored Output |
| `error` | `{session_id, message}` | banner | SessionEvent.Error |
| `rate_limited`, `rate_limit` | `{session_id, retry_after}` | banner | SessionEvent.RateLimited |
| `completed`, `done` | `{session_id, exit_code}` | state update | SessionEvent.Completed |
| `chat_message` | `{session_id, role, content, streaming}` | appendChatBubble (app.js:647) | buildChatMessage → SessionEvent.ChatMessage (v0.34.6) |
| `channel_reply`, `channel_notify`, `response`, `ack`, `sessions`, `session_aware` | various | various | intentionally skipped |

Outbound frames from Android: `subscribe`, `unsubscribe`, `send_input`, `resize_term`.

---

## 13. Endpoints Android currently does NOT call (audit-relevant subset)

Only the parity-relevant items — there are 60+ server endpoints total (memory KG, orchestrator, observer envelopes, autonomous, etc.) that are operator/admin features not exposed on either the PWA or the mobile client.

| Endpoint | PWA uses | Android uses | Assessment |
|---|---|---|---|
| `/api/link/*` | yes | no | G41 — Signal QR device linking |
| `/api/plugins` | yes | no | G27 — Plugins card on Monitor tab |
| `/api/proxy/*` | yes | no | federation admin; skip |
| `/api/sessions/response` | yes | no | SessionDto carries last_response; fine |
| `/api/schedule` (singular) | — | — | legacy; both use `/api/schedules` |
| `/api/assist` | — | — | BL42 experimental |
| `/api/ask` | — | — | BL34 experimental |
| `/api/memory/kg/*` | — | — | KG UI not exposed on either client |
| `/api/orchestrator/*` | — | — | operator feature |
| `/api/autonomous/*` | — | — | operator feature; has config fields in General |
| `/api/observer/*` | — | — | operator feature |
| `/api/agents/*` | — | — | operator feature |

No action needed on these unless a user feature calls for them.
