# ADR-0012 — Push Stack

## Status
Accepted

## Context
Real-time event delivery to the mobile app requires a push notification strategy that works both when the app is backgrounded and when it is open.

## Decision
FCM primary (wake ping), ntfy fallback where configured, WebSocket realtime once the app is open.

## Consequences
Users with FCM-blocked environments can configure ntfy; active sessions upgrade to low-latency WebSocket without polling.
