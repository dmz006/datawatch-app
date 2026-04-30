# ADR-0005 — Client Parity Scope

## Status
Accepted

## Context
The team needed a clear principle for which server capabilities to expose on mobile to avoid scope creep and under-delivery.

## Decision
1:1 client-side parity with the datawatch server API + MCP; no local LLM or code execution. Items that do not make sense on mobile are flagged for user sign-off.

## Consequences
Every server API surface has a corresponding mobile UI unless explicitly excluded via ADR-0019, keeping the mobile client functionally complete.
