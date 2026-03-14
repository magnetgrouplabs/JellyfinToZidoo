---
phase: 3
slug: playback-lifecycle
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-13
---

# Phase 3 — Validation Strategy

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
| 03-01-01 | 01 | 1 | PLAY-01 | unit | `./gradlew test --tests "com.jellyfintozidoo.PlaybackReportingTest.startReport*" -x lint` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | PLAY-02 | unit | `./gradlew test --tests "com.jellyfintozidoo.PlaybackReportingTest.progressReport*" -x lint` | ❌ W0 | ⬜ pending |
| 03-01-03 | 01 | 1 | PLAY-03 | unit | `./gradlew test --tests "com.jellyfintozidoo.PlaybackReportingTest.stopReport*" -x lint` | ❌ W0 | ⬜ pending |
| 03-01-04 | 01 | 1 | PLAY-04 | unit | `./gradlew test --tests "com.jellyfintozidoo.PlaybackReportingTest.stopReport*" -x lint` | ❌ W0 | ⬜ pending |
| 03-01-05 | 01 | 1 | PLAY-05 | unit | `./gradlew test --tests "com.jellyfintozidoo.PlaybackReportingTest.watchedThreshold*" -x lint` | ❌ W0 | ⬜ pending |
| 03-01-06 | 01 | 1 | PLAY-05 | unit | `./gradlew test --tests "com.jellyfintozidoo.JellyfinApiTest.parseItemResponse*Duration*" -x lint` | ❌ W0 | ⬜ pending |
| 03-02-01 | 02 | 2 | PLAY-06 | manual | N/A — requires real device | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/com/jellyfintozidoo/PlaybackReportingTest.java` — stubs for PLAY-01 through PLAY-05
- [ ] Add msToTicks tests to existing `TickConversionTest.java`
- [ ] Add RunTimeTicks parsing tests to existing `JellyfinApiTest.java`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Client relaunch after playback | PLAY-06 | Requires real Zidoo device with Jellyfin client installed | 1. Launch playback from Jellyfin client 2. Let movie finish or stop 3. Verify Jellyfin client relaunches |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
