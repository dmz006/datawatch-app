## Summary

<!-- 1–3 bullet points on what this PR does and why -->

## Scope

- Surfaces affected: phone / wear / auto / shared-kmp / ios-skel / docs-only
- Surface parity: does this touch every surface the feature should reach? (per AGENT.md
  "Testing Requirements")

## Decisions

- [ ] New architecture/UX decision? If yes, linked ADR under `docs/decisions/`.
- [ ] Re-used an existing ADR? Reference it here.

## Testing

- [ ] Unit / integration tests updated (`./gradlew test`)
- [ ] Live device tested — describe device + OS version:
- [ ] `docs/testing-tracker.md` updated (Tested=Yes / Validated=Yes rows)

## Quality gates

- [ ] `./gradlew detekt ktlintCheck android-lintDebug` clean
- [ ] Version bumped per [AGENT.md Versioning](../AGENT.md#versioning) if a functional change
- [ ] `CHANGELOG.md` `[Unreleased]` updated

## Security

- [ ] No tokens / server URLs / user identifiers in logs or diffs
- [ ] No new permissions added (or if added, documented in security-model.md)
- [ ] No new third-party SDKs (or if added, ADR + CHANGELOG note)

## Release train

- [ ] Public AAB built
- [ ] Dev AAB built (if flavor affected)
- [ ] Mapping file preserved
