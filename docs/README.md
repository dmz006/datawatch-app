# Documentation index

Entry point for everything docs-related. Reading order:

1. Repository [README.md](../README.md) — project overview + status.
2. [AGENT.md](../AGENT.md) — operating rules Claude and humans follow.
3. [decisions/README.md](decisions/README.md) — ADRs, the "why" behind architecture.
4. Technical, UX, surface, and delivery packages below.

## Technical package

| Doc | Purpose |
|-----|---------|
| [architecture.md](architecture.md) | C4 context/container/component + module tree |
| [data-flow.md](data-flow.md) | 14 Mermaid sequence diagrams for every major interaction |
| [data-model.md](data-model.md) | ER diagram + SQLDelight schema + encryption scope |
| [api-parity.md](api-parity.md) | Coverage matrix: REST + 37 MCP tools → mobile, upstream refs |
| [security-model.md](security-model.md) | Trust boundaries, keys, FCM payload contract |
| [threat-model.md](threat-model.md) | STRIDE analysis + residual risks |

## UX package

| Doc | Purpose |
|-----|---------|
| [ux-navigation.md](ux-navigation.md) | Server-first shell + proxy drill-down |
| [ux-session-detail.md](ux-session-detail.md) | Bottom-sheet session surface |
| [ux-voice.md](ux-voice.md) | Four voice invocation points + Wear fallback chain |

## Surface package

| Doc | Purpose |
|-----|---------|
| [wear-os.md](wear-os.md) | W1/W3/W4 surfaces + Wearable Data Layer auth |
| [android-auto.md](android-auto.md) | Public Messaging + internal passenger dual-track |
| [branding.md](branding.md) | Palette, typography, icon concept B (chosen) |

## Delivery package

| Doc | Purpose |
|-----|---------|
| [sprint-plan.md](sprint-plan.md) | 8-week MVP, +4-week production, Sprint 0–6 |
| [play-store-registration.md](play-store-registration.md) | Console recreation + submission |
| [privacy-policy.md](privacy-policy.md) | Draft for `https://dmzs.com/datawatch-client/privacy` |
| [store-listing.md](store-listing.md) | Short / tagline / full descriptions |
| [data-safety-declarations.md](data-safety-declarations.md) | Binding Play Data Safety answers |

## Operational folders

- [decisions/](decisions/) — ADRs (MADR per-file split is a Sprint 0 follow-up)
- [plans/](plans/) — dated plan documents (`YYYY-MM-DD-<slug>.md`) per AGENT.md
- [surfaces/](surfaces/) — per-surface implementation notes (grow as surfaces ship)

## Parent-project cross-references

Three upstream tickets on [dmz006/datawatch](https://github.com/dmz006/datawatch):

- #1 — `/api/devices/register` (push token registration)
- #2 — `/api/voice/transcribe` (mobile Whisper endpoint)
- #3 — `/api/federation/sessions` (all-servers fan-out aggregator)

Each has a documented workaround in [api-parity.md](api-parity.md), so mobile MVP does
not block on upstream.
