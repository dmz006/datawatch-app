# Documentation index

Entry point for everything docs-related. Reading order:

1. Repository [README.md](../README.md) — project overview + status.
2. [AGENT.md](../AGENT.md) — operating rules Claude and humans follow.
3. [CHANGELOG.md](../CHANGELOG.md) — what shipped in each release.
4. [decisions/README.md](decisions/README.md) — ADRs, the "why" behind architecture.
5. Technical, UX, surface, and delivery packages below.

## Technical package

| Doc | Purpose |
|-----|---------|
| [architecture.md](architecture.md) | C4 context / container / component + module tree |
| [data-flow.md](data-flow.md) | 14 Mermaid sequence diagrams for every major interaction |
| [data-model.md](data-model.md) | ER diagram + SQLDelight schema + encryption scope |
| [api-parity.md](api-parity.md) | REST + MCP coverage matrix. Mobile → parent endpoint refs |
| [security-model.md](security-model.md) | Trust boundaries, keys, FCM payload contract |
| [threat-model.md](threat-model.md) | STRIDE analysis + residual risks |

## UX package

| Doc | Purpose |
|-----|---------|
| [ux-navigation.md](ux-navigation.md) | Bottom-nav + per-tab screens |
| [ux-session-detail.md](ux-session-detail.md) | Session-detail surface — tabs, composer, banners |
| [ux-voice.md](ux-voice.md) | Voice invocation paths + Wear fallback chain |

## Surface package

| Doc | Purpose |
|-----|---------|
| [wear-os.md](wear-os.md) | W1/W3/W4 surfaces + Wearable Data Layer auth |
| [android-auto.md](android-auto.md) | Messaging-template service + three-screen nav graph |
| [branding.md](branding.md) | Palette, typography, icon concept B |

## Delivery package

| Doc | Purpose |
|-----|---------|
| [sprint-plan.md](sprint-plan.md) | Sprint history + upcoming backlog |
| [parity-plan.md](parity-plan.md) | PWA ↔ mobile parity matrix (row-by-row) |
| [parity-status.md](parity-status.md) | Current-release parity snapshot |
| [play-store-registration.md](play-store-registration.md) | Console recreation + submission |
| [privacy-policy.md](privacy-policy.md) | Draft for `https://dmzs.com/datawatch-client/privacy` |
| [store-listing.md](store-listing.md) | Short / tagline / full descriptions |
| [data-safety-declarations.md](data-safety-declarations.md) | Binding Play Data Safety answers |

## Operational folders

- [decisions/](decisions/) — ADRs (one file per decision, MADR-ish)
- [plans/](plans/) — dated plan documents (`YYYY-MM-DD-<slug>.md`).
  Recent: [2026-04-21-pwa-audit-sprint.md](plans/2026-04-21-pwa-audit-sprint.md),
  [2026-04-21-terminal-audit.md](plans/2026-04-21-terminal-audit.md),
  [2026-04-21-auto-audit.md](plans/2026-04-21-auto-audit.md).

## Parent-project cross-references

Parent [dmz006/datawatch](https://github.com/dmz006/datawatch) tracked
issues. All eighteen mobile-filed issues are closed:

- **#1–#3** shipped (devices/register, voice/transcribe, federation).
- **#5–#13** shipped in parent v4.0.3 (sessions delete, cert, backends/
  active, channels CRUD, sessions/timeline, ollama/openwebui models,
  logs, interfaces, restart).
- **#14, #15, #17, #18** closed as mobile-side mistakes after audit found
  the endpoints already existed and openapi.yaml was stale (#16 fixed
  the staleness).
- **#16** closed — openapi doc drift fixed.
- **#4** remains open — parent's own meta-parity tracker, not a
  mobile-blocker.

Zero open upstream items block mobile today.
