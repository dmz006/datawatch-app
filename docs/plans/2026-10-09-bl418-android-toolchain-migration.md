# BL418: Android / KMP toolchain migration (AGP 9, compileSdk 37, ktor 3, library catch-up)

**Status:** backlog, unscheduled. Opened 2026-10-09 during the Dependabot PR review.
**Why it's one item:** every Dependabot PR that failed traces back to the same chain. They can't be merged one at a time.

## Where we are (main, 2026-10-09)

| Piece | Version | Note |
|---|---|---|
| Gradle wrapper | **9.8.0** | Merged #219 (works with AGP 8.5.2; Gradle 10 deprecation warnings only) |
| AGP | 8.5.2 | `android.experimental.lint.version=9.3.1` in gradle.properties |
| Kotlin | 2.4.20 | |
| compileSdk | shared 35, auto 35, wear 36, composeApp 36 | |
| Compose Multiplatform | 1.6.11 | |
| ktor | 2.3.13 | okhttp 4 engine |
| kotlinx-datetime | 0.6.1 | |
| SQLDelight | 2.0.2 | |
| Gradle Play Publisher | 3.10.0 | |
| gradle/actions (CI) | v5.0.2 | v6 held: license change, see below |

## Closed Dependabot PRs and their real blockers (validated)

| PR | Update | Blocker (from CI + local runs with Gradle 9.8) |
|---|---|---|
| #222 | AGP 8.5.2 → 9.4.1 | AGP 9 migration: `android.experimental.lint.version` must be ≥ 9.4.1. Expect more AGP 9 breaking changes (built-in Kotlin, DSL removals, the KMP `com.android.kotlin.multiplatform.library` plugin for `shared`). Needs Gradle ≥ 9.6, which is now met. |
| #232 | Gradle Play Publisher 3.10.0 → 4.1.1 | Needs **AGP ≥ 9.0** (tested locally). The release pipeline's Play upload/promote uses it, so verify with a dry-run release. |
| #228 | ktor 2.3.13 → 3.6.0 | Pulls **okhttp 5.5.0**, which needs **compileSdk ≥ 37** (and an AGP tested with 37). API break: `AndroidWsHttpClient.kt:50` `pingInterval` unresolved. Check the darwin and CIO engines for ktor 3 changes as well. |
| #237 | Grouped minor/patch (27 updates) | **Wear tiles 1.6.2 / protolayout-material3 1.4.2 need AGP ≥ 8.6.** **kotlinx-datetime 0.8.0** moved `Clock.System` to `kotlin.time.Clock` (`ServerProfileRepository.kt:19`, `RestTransport.kt:508`). The group also carries Compose Multiplatform 1.6.11 → 1.12.1, SQLDelight 2.0.2 → 2.4.0, coroutines 1.11, serialization 1.11, material 1.14, sqlcipher 4.19.1, guava 33.7.2, mockk 1.14, turbine 1.2.1 and detekt 1.23.8. Each needs checking against the target AGP and compileSdk. |

Merged in the same review: #219 Gradle 9.8.0, #231 ktlint plugin 14.2.0, #230 play-services-wearable 20.0.1, #229 mockwebserver 5.5.0, #220 JUnit 6.1.3, #170 js-yaml 4.3.2. #151 (ws) was rebasing. #238 (GitHub Actions) was applied on main except gradle/actions.

## gradle/actions v6 (decided 2026-10-09: stay on v5; update tracked as BL419)

gradle/actions v6 moved its caching into `gradle-actions-caching`, a proprietary component under Gradle's separate terms of use, not MIT. CI stays on **v5.0.2** (MIT, Node 24). `.github/dependabot.yml` ignores gradle/actions majors until the operator decides one of:

- accept the terms and move to v6;
- stay on v5;
- replace its caching with `actions/cache`.

## Suggested order (to refine when scheduled)

1. **AGP 9.x + lint version.** Migrate `shared` to the KMP Android library plugin if AGP 9 requires it. Fix DSL removals. Keep the Gradle 10 deprecation warnings in view.
2. **compileSdk 37** on all modules (targetSdk stays a separate, deliberate change, because it brings behaviour changes).
3. **Gradle Play Publisher 4.x.** Verify upload/promote with a dry-run release before a real tag.
4. **kotlinx-datetime 0.8:** `Clock.System` → `kotlin.time.Clock`.
5. **Library catch-up:** let Dependabot regroup (comment `@dependabot recreate`), then fix Compose MP 1.12, SQLDelight 2.4, material 1.14 and Wear tiles/protolayout.
6. **ktor 3 + okhttp 5:** fix `pingInterval` and re-test WebSocket keepalive on a device. Re-test iOS (darwin engine) on the Mac build host.
7. **Full verification:**
   - phone, Wear and Auto on emulators;
   - iOS simulator;
   - a real-device pass for push and WebSocket;
   - CI and a release dry run.

   Then release, with parity on all channels.

## Risks

- **Release pipeline:** Play publishing and the TestFlight jobs only run on tags. Validate on a branch first.
- **Android Auto (Samsung gearhead):** AGP/compileSdk changes can affect the Car App library behaviour. Re-run the Auto checks.
- **Storage:** SQLCipher and SQLDelight upgrades must keep existing encrypted databases readable (signing-cert-scoped key; see AGENT.md).
