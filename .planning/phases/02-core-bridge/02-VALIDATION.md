---
phase: 2
slug: core-bridge
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-13
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 (already in build.gradle) |
| **Config file** | build.gradle (testImplementation already configured) |
| **Quick run command** | `./gradlew testDebugUnitTest` |
| **Full suite command** | `./gradlew testDebugUnitTest` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew testDebugUnitTest`
- **After every plan wave:** Run `./gradlew testDebugUnitTest`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 0 | BRDG-02 | unit | `./gradlew testDebugUnitTest --tests "*JellyfinUrlParserTest*"` | ❌ W0 | ⬜ pending |
| 02-01-02 | 01 | 0 | BRDG-06 | unit | `./gradlew testDebugUnitTest --tests "*TickConversionTest*"` | ❌ W0 | ⬜ pending |
| 02-01-03 | 01 | 0 | AUTH-01 | unit | `./gradlew testDebugUnitTest --tests "*JellyfinApiTest*"` | ❌ W0 | ⬜ pending |
| 02-01-04 | 01 | 0 | BRDG-03 | unit | `./gradlew testDebugUnitTest --tests "*JellyfinApiTest*"` | ❌ W0 | ⬜ pending |
| 02-xx-xx | xx | x | BRDG-01 | manual-only | Manual: fire intent via adb | N/A | ⬜ pending |
| 02-xx-xx | xx | x | BRDG-04 | manual-only | Manual: verify on device | N/A | ⬜ pending |
| 02-xx-xx | xx | x | BRDG-05 | manual-only | Manual: verify on device | N/A | ⬜ pending |
| 02-xx-xx | xx | x | SETT-01 | manual-only | Manual: D-pad navigation test | N/A | ⬜ pending |
| 02-xx-xx | xx | x | DEBG-01-04 | manual-only | Manual: visual inspection | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/java/com/jellyfintozidoo/JellyfinUrlParserTest.java` — stubs for BRDG-02 (URL parsing)
- [ ] `app/src/test/java/com/jellyfintozidoo/TickConversionTest.java` — stubs for BRDG-06 (tick conversion)
- [ ] `app/src/test/java/com/jellyfintozidoo/JellyfinApiTest.java` — stubs for AUTH-01, BRDG-03 (API auth + JSON parsing)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Intent interception from Jellyfin client | BRDG-01 | Requires actual Android intent from external app | Fire intent via `adb shell am start` with Jellyfin-format URL |
| Path substitution end-to-end | BRDG-04 | Requires real SMB path on device | Verify substituted path resolves to playable file |
| Zidoo player launch | BRDG-05 | Requires Zidoo hardware player | Confirm Zidoo player opens with correct file |
| Settings screen D-pad navigation | SETT-01 | Requires Android TV remote input | Navigate all fields with D-pad only |
| Debug screen pipeline display | DEBG-01-04 | Visual layout verification | Check all pipeline stages shown with correct data |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
