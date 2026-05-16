# datawatch-app COOKBOOK TEMPLATE — MASTER

**Template Version**: 1.0  
**Purpose**: Baseline for all release-specific live-test-status cookbooks (copy to `docs/testing/vX.Y.Z/cookbook.md` for each release)

---

## Before Using This Template

1. **Copy this file** to `docs/testing/vX.Y.Z/cookbook.md` (matching your plan.md version)
2. **Update header** with your release version and test date range
3. **Use during test run**: Update Status column for each story as you complete them
4. **After test run**: Commit final cookbook to git as the persistent record of what was tested

---

# datawatch-app {{VERSION}} — Cookbook (Live Test Status)

**Last updated**: {{RUN_DATE}}  
**Version tested**: {{APP_VERSION}}  
**Test environment**: Secondary instance (https://10.0.2.2:18443) + emulator dw_test_phone  
**Emulator**: Android 14 / API 34, Pixel 6

---

## How to Use This Cookbook

1. **Before test run**: Print this file; note the start time
2. **During test run**: For each story:
   - Execute steps from the plan.md
   - Record: ✅ Pass, ❌ Fail, ⏭ Skip, ⏳ Blocked
   - Update Status column in the tables below
   - Save evidence screenshots/logs to `evidence/TS-NNN/`
3. **After each T-sprint**: Commit your progress (`git add docs/testing/{{VERSION}}/cookbook.md; git commit -m "..."`
4. **After test run**: Final cookbook + evidence forms the release test record

---

## Sprint Status Summary

| T-Sprint | Name | Stories | Passed | Failed | Skipped | Blocked | Status |
|----------|------|---------|--------|--------|---------|---------|--------|
| T1 | {{SPRINT_NAME}} | {{ TOTAL }} | — | — | — | — | 📋 |
| T2 | {{SPRINT_NAME}} | {{ TOTAL }} | — | — | — | — | 📋 |
| ... | | | | | | | |
| T20 | Howto validation | 9 | — | — | — | — | 📋 |
| T21 | End-to-end journeys | 3+ | — | — | — | — | 📋 |
| **TOTALS** | | **{{ SUM }}** | **0** | **0** | **0** | **0** | **📋 PLANNED** |

**Legend**: ✅ = Pass · ❌ = Fail · ⏭ = Skip · ⏳ = Blocked

---

## Story Status Table Template

### T1 — {{SPRINT_NAME}} (TS-001–TS-0NN)

| Story | Title | Status | Notes |
|-------|-------|--------|-------|
| TS-001 | {{Story title}} | 📋 | {{Add result after running}} |
| TS-002 | {{Story title}} | 📋 | {{Add result after running}} |
| ... | | | |

---

## Blocking Issues Log

**Issues preventing test execution** (filled in during run):

| Issue | Title | Blocks | Workaround | Status |
|-------|-------|--------|-----------|--------|
| {{ISSUE}} | {{Title}} | TS-XXX–TS-YYY | {{Workaround or none}} | ⏳ |

---

## Test Debt Tracker

**Unit tests deferred to next sprint** (if applicable):

| Sprint | Test Class | Status | Rationale |
|--------|-----------|--------|-----------|
| {{SPRINT}} | {{TestClassName}} | 📋 | {{Why deferred}} |

---

## Bug Triage

**Bugs found during testing** (if any):

| Bug | Severity | Affects | Status | Fix Sprint |
|-----|----------|---------|--------|------------|
| {{BID}} | P{{0-3}} | TS-NNN | 🐛 Found | Sprint {{X}} |

---

## Release Checklist

Use this before shipping:

- [ ] All regression T-sprints (non-skip stories): ✅ Pass
- [ ] All new T-sprints: ✅ Pass OR ⏳ Blocked (with blocker issue)
- [ ] No ❌ failures in cookbook (all bugs either fixed or marked P2+deferred)
- [ ] Evidence directory populated (screenshots, logcat, JSON responses)
- [ ] Version parity verified: gradle.properties ↔ Version.kt ↔ Play Console
- [ ] CHANGELOG.md updated with release notes
- [ ] Cookbook committed to git
- [ ] Test run time logged (e.g., "Ran 2026-05-15 10:00 to 15:00, 5 hours")

---

## Test Run Summary

**Test runner**: {{WHO}}  
**Run date**: {{START_DATE}} to {{END_DATE}}  
**Environment**: {{EMULATOR_VERSION}}, {{DATAWATCH_VERSION}}  
**Total stories**: {{N}}  
**Total passed**: {{N}} ({{PCT}}%)  
**Total failed**: {{N}}  
**Total skipped**: {{N}} (due to: {{REASONS}})  
**Total blocked**: {{N}} (by: {{ISSUES}})  

**Notes**: {{Add any observations about the test run — environment issues, surprises, recommendations for next release}}

---

## How to Complete This After a Test Run

1. Replace all `{{PLACEHOLDERS}}` with actual values
2. Update the Status column for each story (✅/❌/⏭/⏳)
3. Fill in Blocking Issues Log if any blockers found
4. Complete Release Checklist
5. Commit to git with message: `test(release): {{VERSION}} cookbook final — {{N}}/{{TOTAL}} pass`
6. Reference this commit in your release tag

---

**For future releases**: Copy this file to `docs/testing/vX.Y.Z/cookbook.md`, update header with your version, and fill in during your test run.
