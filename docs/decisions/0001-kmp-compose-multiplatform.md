# ADR-0001 — KMP + Compose Multiplatform

## Status
Accepted

## Context
The project needed a cross-platform architecture that maximises code sharing while supporting native Wear OS and Android Auto surfaces.

## Decision
Shared business logic via Kotlin Multiplatform; Wear OS + Android Auto implemented as native Kotlin modules; iOS app ships a skeleton pre-wired to the KMP shared module.

## Consequences
The shared module is the single source of truth for domain logic, and platform-specific UI code is kept thin and idiomatic per surface.
