# ADR-0014 — Transport Set

## Status
Accepted

## Context
The set of wire protocols the mobile client supports needed to be defined to match the datawatch server's API surface.

## Decision
REST + WebSocket `/ws` + MCP HTTP SSE — all three simultaneously.

## Consequences
The client can consume every server-side transport without protocol negotiation, matching PWA behaviour byte-for-byte.
