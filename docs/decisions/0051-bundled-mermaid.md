# ADR-0051 — Mermaid is bundled in both apps and kept current

## Status
Accepted (2026-10-06) — operator decision.

## Context

Automaton specs can contain ```mermaid diagrams. Both apps rendered them in a
web view that loaded Mermaid from a public CDN (jsDelivr): Android floated on
`mermaid@10`, iOS pinned 10.9.6. That meant a third-party network fetch from
the app (against the closed-loop invariant in AGENT.md › Project Identity) and
no diagrams offline.

## Decision

Bundle Mermaid in the apps, the same way the JetBrains Mono terminal font is
bundled and tracked:

- One copy at `composeApp/src/androidMain/assets/mermaid/` (`mermaid.min.js`,
  `mermaid-LICENSE.txt` — MIT, `mermaid.VERSION`), shared with iOS as a folder
  reference in `iosApp/project.yml` (like `assets/xterm`).
- Android loads it with `file:///android_asset/mermaid/` as the page base and
  `blockNetworkLoads = true` on the diagram web view; iOS injects it as a
  `WKUserScript`. Both render with `securityLevel: 'strict'`.
- `scripts/update-mermaid.sh` picks the newest stable npm release that has been
  out for at least 72 hours (AGENT.md dependency rule) and checks it still
  exposes the `mermaid` global. `.github/workflows/mermaid-update.yml` runs it
  monthly and opens a PR (or an issue linking the branch when Actions can't
  open PRs).
- Starting version: 12.1.0.

## Consequences

- No CDN fetch; diagrams render offline. The app bundle grows by about 5.5 MB
  (uncompressed JS).
- Updates arrive as reviewed PRs; before merging, open an Automaton with a
  mermaid block on Android and iOS and check it renders.
- The web UI still loads Mermaid 10.9.6 from the CDN; diagram rendering may
  differ slightly between web and apps until it moves too.
