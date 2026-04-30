# ADR-0040 — Play Publisher Name

## Status
Accepted

## Context
The Play Console publisher display name needed to be decided before account creation, with a fallback sequence if the preferred name is unavailable.

## Decision
Display "dmz" if Play Console accepts it; fallback sequence: "dmzs" → "dmzs.com" → "Datawatch". Website field points at `https://dmzs.com`.

## Consequences
The shortest available brand name is used as the publisher handle, maintaining consistency with the dmzs.com domain.
