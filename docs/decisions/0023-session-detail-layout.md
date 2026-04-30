# ADR-0023 — Session Detail Layout

## Status
Accepted

## Context
The session detail screen needed a layout pattern that keeps the chat primary while giving access to auxiliary panels without leaving the screen.

## Decision
Bottom-sheet pattern: chat is the spine; Terminal / Logs / Timeline / Memory slide up from bottom sheets.

## Consequences
The chat view is always visible and reachable; auxiliary panels are a single swipe away without requiring back-stack navigation.
