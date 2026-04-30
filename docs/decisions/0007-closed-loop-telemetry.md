# ADR-0007 — Closed-Loop Telemetry

## Status
Accepted

## Context
Crash and analytics reporting options needed to be decided before any SDK dependencies were added to avoid accidental third-party data sharing.

## Decision
No Crashlytics, no Sentry SaaS, no Firebase Analytics, no Google Analytics. Diagnostics stay local to the device and the user's own datawatch server.

## Consequences
No telemetry data leaves the user's infrastructure, satisfying the closed-loop privacy model at the cost of no vendor-hosted crash dashboards.
