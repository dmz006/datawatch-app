# iOS skeleton

Per ADR-0001 and Batch 1 Q4, the iOS app is pre-wired to the KMP shared module but the
iPhone content phase starts **after the Android production release (target 2026-07-10)**.
Until then, this directory is intentionally minimal.

## Sprint 1 Day 2 action items

- Run `xcodegen generate` in this directory (an `xcodegen` project spec is committed at
  `iosApp/project.yml` in the Sprint 1 commit) to produce the Xcode project files on-demand
  without committing them.
- Import the generated `DatawatchShared.xcframework` from the `:shared` module's iOS
  framework output.
- Verify `kotlinx-coroutines` + `kotlinx-serialization` calls from Swift compile.

## Skeleton intent

This module exists so that:

1. `./gradlew :shared:linkDebugFrameworkIosArm64` builds cleanly and produces a valid
   iOS framework.
2. The KMP shared module's `iosMain` source set has a real target to export to.
3. Future iOS work can begin without a stack migration.

## Not in scope pre-1.0 parity (Android)

- Actual Swift UI, networking bootstrap, push integration, voice capture.
- App Store Connect registration (see
  [docs/play-store-registration.md §8](../docs/play-store-registration.md) for the iOS
  track outline).
- iOS-specific security audit.
