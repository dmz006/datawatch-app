# BL33 — Android Auto: per-guardrail approval on BlockDetailsScreen

**Status:** ✅ SHIPPED v1.7.0 (2026-09-14)  
**Tracks:** [datawatch-app#150](https://github.com/dmz006/datawatch-app/issues/150)  
**Requires server:** [datawatch#153](https://github.com/dmz006/datawatch/issues/153) — `POST /api/sessions/{id}/guardrail/{name}/approve`

---

## Problem

The existing `BlockDetailsScreen` showed one "Approve Gate" action that called
`POST /api/sessions/{id}/override` — clearing **all** blocked guardrails at once.
This was all-or-nothing and gave the driver no way to approve a safe check while
leaving a high-risk block in place.

## Solution

`BlockDetailsScreen` now branches on the number of blocked verdicts:

### Single block
`MessageTemplate` layout:
- Body text: guardrail name + verdict message (TTS via `GuardrailTtsBuilder`)
- Primary action: "Approve [guardrail-name]" → calls `approveGuardrailBlock`
- ActionStrip: "Listen" (replay TTS)

### Multiple blocks
`ListTemplate` layout:
- One row per blocked verdict — title = friendly guardrail name, tap = approve that specific guardrail
- ActionStrip: "Listen" (replay combined TTS) + "Approve All" (bulk `POST …/override` fallback)

## Transport change

`TransportClient` gained:
```kotlin
suspend fun approveGuardrailBlock(sessionId: String, guardrailName: String): Result<Unit>
```
implemented in `RestTransport` as `POST /api/sessions/{sessionId}/guardrail/{guardrailName}/approve`.

`GuardrailTtsBuilder.friendlyName` was promoted from `private` to `internal` so
`BlockDetailsScreen` can use it for button label text.

## Tests added

`RestTransportTest`:
- `approveGuardrailBlockPostsToCorrectUrl` — verifies path template and HTTP method

## No server changes in this repo

The `POST /api/sessions/{id}/guardrail/{name}/approve` endpoint is on the datawatch
server side (tracked in datawatch#153). The app-side feature is wired and will return
a 404/500 until the server ships that endpoint.
