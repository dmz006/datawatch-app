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
| F1 | Sprint 1 — shared foundation (transport + storage + onboarding) | [2026-04-18-sprint-1-foundation.md](2026-04-18-sprint-1-foundation.md) — Phase 1 in progress |
| F2 | Sprint 2 — WebSocket + session detail + xterm WebView + push | TBD |
| F3 | Sprint 3 — voice + MCP SSE + all-servers view | TBD |
| F4 | Sprint 4 — Wear OS (W1/W3/W4) + Android Auto dual-track | TBD |
| F5 | Sprint 5 — harden + Play submission + Driver Distraction review | TBD |
| F6 | Sprint 6 — open testing → production 1.0.0 | TBD |

## Backlog

| ID | Title | Notes |
|----|-------|-------|
| BL1 | Split consolidated `decisions/README.md` into per-ADR MADR files | Low priority; 41 ADRs |
| BL2 | Biometric unlock | Post-v1 |
| BL3 | Tablet two-pane layout | Post-v1 |
| BL4 | Wear Tile (W2) | Post-v1 |
| BL5 | iOS content phase | After Android production |
| BL6 | Home-screen widget | Post-v1 |
| BL7 | Foldable layout | Post-v1 |
| _(BL8 landed in Sprint 1 Phase 2 — see Completed Backlog)_ | | |

## Completed

_None yet._

## Completed backlog

| ID | Title | Shipped in | Notes |
|----|-------|-----------|-------|
| BL8 | SQLCipher Android driver swap | v0.1.x Sprint 1 Phase 2 | `net.zetetic:sqlcipher-android` + Keystore-derived passphrase |
