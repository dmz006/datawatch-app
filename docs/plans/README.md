# Plans, bugs, and backlog tracker

Single source of truth per AGENT.md "Project Tracking". IDs are permanent — never
reuse a bug (B#), backlog (BL#), or feature (F#) number.

Plans: individual dated documents live as `YYYY-MM-DD-<slug>.md` in this directory
when work warrants formal planning (3+ files or non-trivial architecture).

## Active bugs

_None yet._

## Planned / In Progress

| ID | Title | Plan |
|----|-------|------|
| F1 | Sprint 1 — shared foundation (transport + storage + onboarding) | [2026-04-18-sprint-1-foundation.md](2026-04-18-sprint-1-foundation.md) — v0.2.x shipped |
| F2 | Sprint 2 — WebSocket + session detail + xterm WebView + push + multi-server picker + BL9 | TBD |
| F3 | Sprint 3 — voice + MCP SSE + all-servers + BL6 home widget | TBD |
| F4 | Sprint 4 — Wear OS (W1/W3/W4) + BL4 Wear Tile + Android Auto dual-track + BL10 Auto Tile | TBD |
| F5 | Sprint 5 — harden + BL2 biometric unlock + Play submission | TBD |
| F6 | Sprint 6 — open testing → production 1.0.0 | TBD |

## Backlog

| ID | Title | Notes |
|----|-------|-------|
| BL1 | Split consolidated `decisions/README.md` into per-ADR MADR files | Low priority; 41 ADRs |
| BL3 | Tablet two-pane layout | Post-v1 |
| BL5 | iOS content phase | After Android production |
| BL7 | Foldable layout | Post-v1 |
| BL11 | Full schedule editor CRUD | Post-v1 |
| BL12 | KG Add / Timeline / Research deeper views | Post-v1 |
| BL13 | Adjustable terminal dimensions | Post-v1 |
| BL14 | Raw YAML config editor (gated behind biometric + confirm) | Post-v1; revisits ADR-0019 |
| BL15 | Localization (DE, ES, FR, JA) | Post-v1 |

## Promoted from backlog → v1.0.0 scope (ADR-0042)

| ID | Title | Lands in sprint |
|----|-------|-----------------|
| BL2 | Biometric unlock | 5 |
| BL4 | Wear Tile (W2) | 4 |
| BL6 | Home-screen widget | 3 |
| BL9 | 3-finger swipe-up server picker | 2 |
| BL10 | Android Auto Tile (parked-state dashboard — dev flavor only) | 4 |

## Completed

_None yet._

## Completed backlog

| ID | Title | Shipped in | Notes |
|----|-------|-----------|-------|
| BL8 | SQLCipher Android driver swap | v0.1.x Sprint 1 Phase 2 | `net.zetetic:sqlcipher-android` + Keystore-derived passphrase |
