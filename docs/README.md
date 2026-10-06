# Documentation index

Entry point for everything docs-related. Reading order:

1. Repository [README.md](../README.md) — project overview + status.
2. [AGENT.md](../AGENT.md) — operating rules Claude and humans follow.
3. [CHANGELOG.md](../CHANGELOG.md) — what shipped in each release.
4. [decisions/README.md](decisions/README.md) — ADRs, the "why" behind architecture.
5. Technical, UX, surface, and delivery packages below.

## User guides

| Doc | Purpose |
|-----|---------|
| [installation.md](installation.md) | Install on phone, Wear and Auto; troubleshooting |
| [usage.md](usage.md) | How every screen behaves |
| [features.md](features.md) | Feature list |

## Technical package

| Doc | Purpose |
|-----|---------|
| [architecture.md](architecture.md) | C4 context / container / component + module tree |
| [data-flow.md](data-flow.md) | Mermaid sequence diagrams for every major interaction (24, incl. council live runs, planning stream, scroll mode, channel-ready, APNs, profile editor) |
| [transports.md](transports.md) | REST, WebSocket, SSE streams and MCP SSE — when each is used, fallback, limits, security |
| [ios.md](ios.md) | iOS app: TestFlight install, first-run setup, permissions, limitations, troubleshooting |
| [store-listing-ios.md](store-listing-ios.md) | Every App Store Connect / TestFlight field, privacy label, age rating, screenshot sizes |
| [config-reference.md](config-reference.md) | Every setting the apps expose, its UI path, wire key and persistence |
| [implementation.md](implementation.md) | Implementation notes and settings fields |
| [data-model.md](data-model.md) | ER diagram + SQLDelight schema + encryption scope |
| [api-parity.md](api-parity.md) | REST + MCP coverage matrix. Mobile → parent endpoint refs |
| [security-model.md](security-model.md) | Trust boundaries, keys, FCM payload contract |
| [threat-model.md](threat-model.md) | STRIDE analysis + residual risks |

## Testing package

| Doc | Purpose |
|-----|---------|
| [testing-tracker.md](testing-tracker.md) | Per-feature Tested / Validated matrix |
| [testing.md](testing.md) | Per-bug test log (description, steps, expected, actual, how verified) |
| [testing/](testing/) | QA cookbook, master plan, test-isolation guide, release test runs |

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
| [parity/README.md](parity/README.md) | Three-way parity matrix (PWA ↔ Android ↔ iOS), section files and decisions |
| [plans/README.md](plans/README.md) | Bugs, backlog and plans tracker (open items on top) |
| [operations.md](operations.md) | Release, signing and distribution runbook |
| [play-store-registration.md](play-store-registration.md) | Console recreation + submission |
| [privacy-policy.md](privacy-policy.md) | Draft for `https://dmzs.com/datawatch-client/privacy` |
| [store-listing.md](store-listing.md) | Short / tagline / full descriptions |
| [data-safety-declarations.md](data-safety-declarations.md) | Binding Play Data Safety answers |

## Operational folders

- [decisions/](decisions/) — ADRs (one file per decision, MADR-ish)
- [plans/](plans/) — dated plan documents (`YYYY-MM-DD-<slug>.md`) and the
  tracker [plans/README.md](plans/README.md). Fully shipped plans move to
  [plans/historical-plans/](plans/historical-plans/). iOS runbooks:
  [plans/ios-mac-build-host.md](plans/ios-mac-build-host.md),
  [plans/ios-testflight-setup.md](plans/ios-testflight-setup.md).

## Parent-project cross-references

Parent [dmz006/datawatch](https://github.com/dmz006/datawatch) tracked
issues.

**Open as of 2026-10-06** (tracked in [plans/README.md](plans/README.md)):

- [#183](https://github.com/dmz006/datawatch/issues/183) — APNs delivery for
  iOS devices (the app side shipped in v1.28.0).
- [#174](https://github.com/dmz006/datawatch/issues/174) — MCP channel bridges
  don't re-register after a daemon restart ("Waiting for MCP channel" never
  clears).
- [#172](https://github.com/dmz006/datawatch/issues/172),
  [#177](https://github.com/dmz006/datawatch/issues/177),
  [#181](https://github.com/dmz006/datawatch/issues/181),
  [#182](https://github.com/dmz006/datawatch/issues/182) — web UI parity
  follow-ups from the 2026-10-04 / 2026-10-05 decisions.

**Early mobile-filed issues** (all eighteen closed):

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

None of the early issues block mobile; the open items above limit iOS push and the MCP-channel banner only.
