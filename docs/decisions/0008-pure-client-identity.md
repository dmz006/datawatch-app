# ADR-0008 — Pure Client Identity

## Status
Accepted

## Context
The architecture needed to clarify whether the mobile app would ever act as a local server or message broker.

## Decision
No local server process, no queue, no replay. The mobile app is a pure client.

## Consequences
The app has no background daemon or persistent queue, simplifying resource usage and eliminating a class of data-consistency bugs.
