# ADR-0006 — Voice Pipeline

## Status
Accepted

## Context
Voice input was required but on-device speech-to-text was ruled out to keep the client thin and leverage the server's existing Whisper integration.

## Decision
Record audio on device, upload to the datawatch server, and have the server transcribe via Whisper. No on-device STT in v1.

## Consequences
Transcription quality is consistent with the PWA and depends on server reachability; voice is unavailable fully offline.
