# Validation of `2026-04-22-pwa-parity-audit.md`

Every claim in the 2026-04-22 audit, re-verified 2026-04-23 against v0.34.6 Android + live PWA at `localhost:8443`. Disposition codes:

- **CONFIRMED** — gap still exists, tracked as a Gnn in `README.md` §3.
- **CLOSED** — already parity on Android (either pre-existing or shipped since 04-22).
- **REDEFINED** — claim was close to real but misdescribed; the actual gap is different and has a Gnn.
- **WONTFIX** — claim describes a feature that doesn't exist in the PWA at all (source-verified).

---

## Section 1 — Session header / TopAppBar

| 04-22 claim | 04-23 disposition |
|---|---|
| Session title truncated (ellipsis) | ✅ **PARITY** confirmed both sides. |
| Session ID after title | ✅ **PARITY**. |
| Backend / mode chip | ✅ **PARITY**. |
| Hostname prefix | ✅ **PARITY**. |
| Connection dot (green/red/grey) | ✅ **PARITY** — `SessionDetailScreen.kt:215-229` draws 10dp dot with correct palette. |
| **B34** — State pill tooltip + dropdown menu | **CLOSED**. Android has `stateMenuOpen` → `StateOverrideDialog` accessible by click on state pill (SessionDetailScreen.kt:275, 421-429). Tooltip-on-hover is a desktop concept that doesn't translate to touch; the click-to-menu gesture is the touch equivalent and is present. B34 claim superseded. (A polish refinement — anchoring the menu to the pill instead of showing an AlertDialog — is tracked as **G12** in the new inventory.) |

---

## Section 2 — Quick-Command / Command Bar

| 04-22 claim | 04-23 disposition |
|---|---|
| **B35** — Quick-command dropdown palette | **CLOSED**. Android has `QuickCommandsSheet` with System / Saved / Custom sections (SessionsScreen.kt quickCmdsOpen state) matching PWA's `.quick-cmd-select` + optgroups (app.js:1460). The earlier audit claim "Android has no dropdown" was factually wrong; the behavior is a Compose `ModalBottomSheet` instead of an HTML `<select>`. Functionally equivalent. |
| Quick-command send button | ✅ Part of QuickCommandsSheet. |
| Quick-command cancel button | ✅ Sheet dismiss. |
| **B36** — Quick-reply buttons under chat prompt | **MERGED into G7** (Alerts rebuild). The real gap the 04-22 audit was pointing at is the per-alert quick-reply dropdown on *waiting-input* alerts (app.js:5573-5580). Android's current Alerts screen has a Schedule button but no inline quick-reply. Covered by G7. |
| Quick-input row for custom command | ✅ Inside QuickCommandsSheet. |

---

## Section 3 — Settings → Comms

| 04-22 claim | 04-23 disposition |
|---|---|
| Browser token / Server bearer / MCP SSE bearer | ✅ **PARITY** (Android ConfigFieldsPanel CommsAuth). |
| Proxy Resilience toggle | ✅ **PARITY** (ConfigFieldsPanel Proxy). |
| Signal device linking / QR | **CONFIRMED as G41** — PWA uses `/api/link/start` + SSE `/api/link/stream` + QR rendering (app.js linking code). Android has no implementation. Rolled into v0.35.2 release (or deferred; user has been using the PWA flow for this). |
| Notification preferences | ✅ **PARITY** (NotificationsCard). |
| **B37** — TLS / Certificate selector | **WONTFIX**. Source-checked both sides: PWA's `Settings → Comms → Web Server` section has `TLS enabled` toggle / `TLS port` / `TLS auto-generate cert` toggle / `tls_cert` path / `tls_key` path fields (app.js renderSettingsView 'comms' branch). No "client certificate selector" exists. The `/api/cert` endpoint is a CA-cert **download** for installing on clients, which Android already has via `CertInstallCard`. Android ConfigFieldsPanel covers all the TLS config fields. **No gap.** |
| **B38** — Raw YAML config editor | Still open as backlog **BL14**, status unchanged. Neither client has it. **Not a parity gap.** |
| **B41** — Language selector | **WONTFIX**. Source-checked: PWA General tab (app.js renderSettingsView 'general' branch, plus full DOM screenshot at `/home/dmz/workspace/pwa-audit/out/05-settings-general.png`) has exactly these sections: `Datawatch / Auto-Update / Session / Project Profiles / Cluster Profiles / Notifications / RTK / Pipelines / Autonomous / Plugins / Orchestrator / Whisper`. No i18n / language selector. Parent datawatch is English only. **No gap.** |

---

## Section 4 — Settings → LLM

| 04-22 claim | 04-23 disposition |
|---|---|
| Model selector | ✅ **PARITY** since v0.34.4 schema-driven dialogs. |
| **B39** — Temperature / top-p / max-tokens sliders | **REDEFINED**. Source-checked PWA `LLM_FIELDS` (app.js:4262-4282) for all registered backends (claude-code, aider, goose, gemini, ollama, opencode, opencode-acp, opencode-prompt, openwebui, shell). **None include `temperature`, `top_p`, or `max_tokens` fields.** Parent datawatch's LLM invocation uses backend defaults or session.effort. Real gap: Android's `LlmBackendSchemas.kt` has schemas for `anthropic`, `openai`, `groq`, `openrouter`, `xai` which the server doesn't support — dead config. Tracked as **G45** (schema cleanup). |
| **B40** — Prompt templates list | **REDEFINED**. PWA has no "prompt templates" section. It has Saved Commands (Android parity via SavedCommandsCard) and Detection Filters (Android parity via DetectionFiltersCard) — both shipped. The 04-22 claim likely confused "saved commands" with "prompt templates." **No gap.** |
| **B38** (repeated) — Raw YAML editor | Same disposition — BL14 backlog, not a parity gap. |

---

## Section 5 — Settings → General / Misc

| 04-22 claim | 04-23 disposition |
|---|---|
| Project profiles | ✅ **PARITY** (KindProfilesCard). |
| Cluster profiles | ✅ **PARITY**. |
| Security / token vault | ✅ **PARITY** (SecurityCard). |
| **B41** (repeated) — Language selector | **WONTFIX** — see Section 3 above. |

---

## Section 6 — Other UI elements

| 04-22 claim | 04-23 disposition |
|---|---|
| **B42** — In-app alerts panel | **CLOSED at surface level, merged into G7 for structure.** Android has a full Alerts bottom-nav tab. The *structural* gap (Active/Inactive tabs, per-session sub-groups, per-alert quick-reply) is the real issue — tracked as **G7** in the master doc. The "in-app alerts panel" capability itself is already present. |
| Backlog pager button | ✅ **PARITY** (TerminalToolbar). |
| Session-detail timeline bottom sheet | ✅ **PARITY** (`timelineOpen` → TimelineSheet in SessionDetailScreen.kt). |
| Widget configuration | Not applicable (PWA has no widgets). |

---

## Roll-up table

| Old ID | New disposition | Replacement ID (if any) |
|---|---|---|
| B34 | CLOSED | — |
| B35 | CLOSED | — |
| B36 | MERGED | G7 |
| B37 | WONTFIX | — |
| B38 | UNCHANGED | BL14 (backlog) |
| B39 | REDEFINED | G45 (schema cleanup) |
| B40 | REDEFINED / NON-GAP | — |
| B41 | WONTFIX | — |
| B42 | CLOSED (structurally REDEFINED) | G7 |

**Net.** 4 closed, 2 wontfix, 1 merged, 1 redefined to a concrete new item, 1 unchanged backlog entry.

## Recommendation

Delete the stand-alone 04-22 audit doc (or mark it obsolete with a header pointer to this directory). The 04-22 audit's text has stale pointers and two factually incorrect items (B37, B41) that would mislead future implementers. The new `docs/plans/audit-2026-04-23/` directory is the canonical parity reference going forward.
