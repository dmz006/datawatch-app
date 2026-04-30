# ADR-0024 — Terminal

## Status
Accepted

## Context
The session detail terminal panel required a rendering strategy that matches the PWA's terminal output exactly.

## Decision
xterm.js in a WebView for byte-for-byte PWA parity.

## Consequences
Terminal rendering is identical to the PWA at the cost of a WebView dependency; native terminal rendering is deferred.
