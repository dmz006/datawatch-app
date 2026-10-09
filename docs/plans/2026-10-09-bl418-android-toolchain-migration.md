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
| #242 | Grouped minor/patch (30 updates; replaces #237) | **AGP 8.13.2 can't run on Gradle ≥ 9.6:** it uses `InternalProblems`, a Gradle internal API that 9.6 removed, so with Gradle 9.8 the **only** AGP upgrade is AGP 9. These need a newer AGP and wait for AGP 9: Compose Multiplatform 1.12.1, Navigation 2.10.2 and lifecycle 2.11 (AGP ≥ 9.1); androidx.core 1.16+ (via material 1.14.0) and biometric 1.4.0-alpha07 (AGP ≥ 8.6 / 8.9.1); Wear tiles 1.6.2, protolayout 1.4.2 and WorkManager 2.12.0 (AGP ≥ 8.6). kotlinx-datetime 0.8.0 has the `Clock.System` break. SQLDelight 2.4.1 and SQLCipher 4.19.1 wait for the storage step (encrypted DB must stay readable). **Merged separately (2026-10-09, tested on AGP 8.5.2):** coroutines 1.11.0 (+ test), serialization 1.11.0, guava 33.7.2, zxing 3.5.4, Wear watchface 1.3.0, detekt 1.23.8, Gradle Play Publisher 3.13.0, turbine 1.2.1, mockk 1.14.11. |

Also merged 2026-10-09: #240 upload-artifact v7, #243 security-crypto 1.1.0 (bytecode-compared with alpha06: same key names, schemes and read/write logic). Merged in the first review: #219 Gradle 9.8.0, #231 ktlint plugin 14.2.0, #230 play-services-wearable 20.0.1, #229 mockwebserver 5.5.0, #220 JUnit 6.1.3, #170 js-yaml 4.3.2. #151 (ws) was rebasing. #238 (GitHub Actions) was applied on main except gradle/actions.

## gradle/actions v6 (decided 2026-10-09: stay on v5; update tracked as BL419)

gradle/actions v6 moved its caching into `gradle-actions-caching`, a proprietary component under Gradle's separate terms of use, not MIT. CI stays on **v5.0.2** (MIT, Node 24). `.github/dependabot.yml` ignores gradle/actions majors until the operator decides one of:

- accept the terms and move to v6;
- stay on v5;
- replace its caching with `actions/cache`.

## Suggested order (to refine when scheduled)

1. **AGP 9.x + lint version.** (Newer AGP 8.x is not an option: AGP 8.6+ fails on Gradle ≥ 9.6, see #242.) Migrate `shared` to the KMP Android library plugin if AGP 9 requires it. Fix DSL removals. Keep the Gradle 10 deprecation warnings in view.
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
