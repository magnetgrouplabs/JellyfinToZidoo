---
phase: 5
slug: advanced-playback
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-14
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 |
| **Config file** | app/build.gradle (testImplementation 'junit:junit:4.+') |
| **Quick run command** | `./gradlew test --tests "com.jellyfintozidoo.*" -x lint` |
| **Full suite command** | `./gradlew test -x lint` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.jellyfintozidoo.*" -x lint`
- **After every plan wave:** Run `./gradlew test -x lint`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 0 | ADVP-01 | unit | `./gradlew test --tests "com.jellyfintozidoo.IntroSkipperApiTest" -x lint` | ❌ W0 | ⬜ pending |
| 05-01-02 | 01 | 0 | ADVP-03/04 | unit | `./gradlew test --tests "com.jellyfintozidoo.MediaStreamParsingTest" -x lint` | ❌ W0 | ⬜ pending |
| 05-01-03 | 01 | 0 | SETT-08 | unit | `./gradlew test --tests "com.jellyfintozidoo.SettingsExportTest" -x lint` | ❌ W0 | ⬜ pending |
| 05-02-01 | 02 | 1 | ADVP-01 | unit | `./gradlew test --tests "com.jellyfintozidoo.IntroSkipperApiTest" -x lint` | ❌ W0 | ⬜ pending |
| 05-02-02 | 02 | 1 | ADVP-01 | unit | `./gradlew test --tests "com.jellyfintozidoo.IntroSkipperApiTest" -x lint` | ❌ W0 | ⬜ pending |
| 05-02-03 | 02 | 1 | ADVP-02 | unit | `./gradlew test --tests "com.jellyfintozidoo.IntroSkipperApiTest" -x lint` | ❌ W0 | ⬜ pending |
| 05-03-01 | 03 | 1 | ADVP-03/04 | unit | `./gradlew test --tests "com.jellyfintozidoo.MediaStreamParsingTest" -x lint` | ❌ W0 | ⬜ pending |
| 05-04-01 | 04 | 2 | SETT-08 | unit | `./gradlew test --tests "com.jellyfintozidoo.SettingsExportTest" -x lint` | ❌ W0 | ⬜ pending |
| 05-04-02 | 04 | 2 | SETT-08 | manual-only | Manual: import settings, verify auto-login | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/com/jellyfintozidoo/IntroSkipperApiTest.java` — stubs for ADVP-01, ADVP-02 (parse segments, disarm logic)
- [ ] `app/src/test/java/com/jellyfintozidoo/MediaStreamParsingTest.java` — stubs for ADVP-03, ADVP-04 (index mapping, URL param parsing)
- [ ] `app/src/test/java/com/jellyfintozidoo/SettingsExportTest.java` — stubs for SETT-08 (token exclusion)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Import triggers auto-authenticate | SETT-08 | Requires real Jellyfin server credentials | 1. Export settings 2. Clear app data 3. Import settings file 4. Verify auto-login succeeds |
| Intro skip on live Zidoo playback | ADVP-01 | Requires Zidoo hardware + episode with intro data | 1. Play episode with Intro Skipper data 2. Verify auto-seek past intro 3. Verify manual seek disarms |
| Credit skip triggers Up Next | ADVP-02 | Requires Zidoo hardware + episode with credit data | 1. Play episode with Credits data 2. Verify Up Next launches at credit timestamp |
| Audio/subtitle track applied on Zidoo | ADVP-03/04 | Requires Zidoo hardware + multi-track media | 1. Select non-default audio in Jellyfin 2. Play via Zidoo 3. Verify correct track active |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
