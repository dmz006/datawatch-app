# Auto: Full PRD / Story / Task Management + Voice

**Date:** 2026-09-13  
**Version at planning:** v1.3.0  
**Shipped in:** v1.4.0

## Scope

Modules: `auto/`, `auto/src/test/`

## Goal

Give Android Auto drivers complete control over automata lifecycle from the car:
read out / listen to full PRD + story + task detail; manage individual stories
(approve, read aloud); reset failed tasks; and execute lifecycle decisions by
voice (approve my plan, stop my plan, what is my plan doing).

## Phases

1. **[Done] `AutoStoryDetailScreen`** — story detail as a TTS-readable MessageTemplate
   with Approve (PRD-level) and Reset-Task (first failed task) actions.
2. **[Done] Wire story rows** in `AutoPrdStoriesScreen` to push `AutoStoryDetailScreen`.
3. **[Done] `AutoPrdDetailScreen` lifecycle gaps** — add Run (for `approved` state) and
   Decompose (for `pending`) so all lifecycle transitions are reachable.
4. **[Done] Voice commands** — add `APPROVE_PLAN`, `STOP_PLAN`, `READ_PLAN`;
   wire `APPROVE_GATE` → actual approve call; wire `LIST_AUTOMATA` → spoken summary.
5. **[Done] Unit tests** — `AutoPrdDetailBodyTest`, `AutoStoryDetailBodyTest`;
   extend `VoiceCommandTest` for new parse phrases.
6. **[Done] Docs + version bump** — CHANGELOG, README current-release line, testing-tracker row.
