# ADR-0004 — Transport Priority

## Status
Accepted

## Context
The app must reach the datawatch server across a variety of network conditions, including when a direct connection is unavailable.

## Decision
Primary: direct API (REST / WS / MCP). Fallback: Android Intent to on-device Signal / SMS / etc. apps targeting the number or ID the datawatch server already watches.

## Consequences
Users retain the ability to send commands even when the datawatch server is unreachable directly, by routing through messaging apps already configured on the device.
