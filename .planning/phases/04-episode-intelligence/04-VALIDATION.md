---
phase: 4
slug: episode-intelligence
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-14
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 (already configured) |
| **Config file** | app/build.gradle (testImplementation 'junit:junit:4.+') |
| **Quick run command** | `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.*"` |
| **Full suite command** | `./gradlew testDebugUnitTest` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.*"`
- **After every plan wave:** Run `./gradlew testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 0 | EPIS-01, EPIS-02 | unit | `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.ReverseSubstitutionTest"` | No - Wave 0 | ⬜ pending |
| 04-01-02 | 01 | 0 | SETT-04 | unit | `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.SubstitutionTest"` | No - Wave 0 | ⬜ pending |
| 04-01-03 | 01 | 0 | EPIS-03, EPIS-04 | unit | `./gradlew testDebugUnitTest --tests "com.jellyfintozidoo.JellyfinApiTest"` | Partial (extend) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/com/jellyfintozidoo/ReverseSubstitutionTest.java` — stubs for EPIS-01, EPIS-02 (reverse path substitution, credential stripping, URI decoding)
- [ ] `app/src/test/java/com/jellyfintozidoo/SubstitutionTest.java` — stubs for SETT-04 (multi-slot forward + reverse substitution)
- [ ] Extend `JellyfinApiTest.java` — stubs for EPIS-03, EPIS-04 (NextUp detail parsing, searchItemByPath response parsing)

*Existing infrastructure partially covers; Wave 0 fills gaps.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Zidoo player episode navigation triggers path change detection | EPIS-01 | Requires physical Zidoo Z9X Pro hardware | Play episode, press next in Zidoo, verify Jellyfin marks previous watched |
| UpNextActivity displays correct next episode with backdrop | EPIS-03 | UI rendering on Android TV | Verify 10s countdown, backdrop blur, episode metadata display |
| Season boundary crossover via NextUp API | EPIS-04 | Cross-season data from real Jellyfin server | Play last episode of season, verify next season first episode loads |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
