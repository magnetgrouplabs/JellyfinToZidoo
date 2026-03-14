---
phase: 05-advanced-playback
plan: 01
subsystem: api
tags: [intro-skipper, mediastreams, gson, tdd, settings-export]

# Dependency graph
requires:
  - phase: 02-core-bridge
    provides: JellyfinApi static parsing pattern and Gson JsonParser usage
provides:
  - IntroSkipperResult class and parseIntroSkipperResponse for intro/credit timestamps
  - jellyfinToZidooAudioIndex and jellyfinToZidooSubtitleIndex for stream index mapping
  - parseUrlParam for extracting AudioStreamIndex/SubtitleStreamIndex from streaming URLs
  - findDefaultStreamIndex for IsDefault/IsForced fallback stream selection
  - buildExportJson with token exclusion for safe settings export
affects: [05-02-PLAN, 05-03-PLAN]

# Tech tracking
tech-stack:
  added: []
  patterns: [TDD for pure parsing logic, extracted testable static methods from Android activities]

key-files:
  created:
    - app/src/test/java/com/jellyfintozidoo/IntroSkipperApiTest.java
    - app/src/test/java/com/jellyfintozidoo/MediaStreamParsingTest.java
    - app/src/test/java/com/jellyfintozidoo/SettingsExportTest.java
  modified:
    - app/src/main/java/com/jellyfintozidoo/JellyfinApi.java
    - app/src/main/java/com/jellyfintozidoo/SettingsActivity.java

key-decisions:
  - "Used simple string parsing for parseUrlParam instead of android.net.Uri to avoid Android dependency in unit tests"
  - "findDefaultStreamIndex checks both IsDefault and IsForced flags in a single pass for subtitle fallback"
  - "Extracted buildExportJson as static method from SettingsActivity for unit testability"

patterns-established:
  - "TDD RED-GREEN for pure parsing: write failing tests first, then implement minimal code"
  - "Static method extraction from Android Activities for unit testing without Android context"

requirements-completed: [ADVP-01, ADVP-02, ADVP-03, ADVP-04, SETT-08]

# Metrics
duration: 6min
completed: 2026-03-14
---

# Phase 5 Plan 01: TDD Parsing Logic Summary

**IntroSkipper response parsing, Jellyfin-to-Zidoo stream index mapping, URL param extraction, and settings export token exclusion -- all TDD with 25 passing tests**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-14T19:03:18Z
- **Completed:** 2026-03-14T19:09:00Z
- **Tasks:** 3 features (6 TDD commits: 3 RED + 3 GREEN)
- **Files modified:** 5

## Accomplishments
- IntroSkipperResult inner class with sec-to-ms conversion, parsing both Introduction and Credits segments with Valid flag checks and -1 sentinels
- 4 stream mapping methods: jellyfinToZidooAudioIndex (0-based), jellyfinToZidooSubtitleIndex (1-based), parseUrlParam, findDefaultStreamIndex
- Extracted buildExportJson from SettingsActivity.exportSettings() with jellyfin_access_token and jellyfin_user_id exclusion
- All 25 new tests pass green alongside existing test suite (no regressions)

## Task Commits

Each feature was committed atomically with TDD RED then GREEN commits:

1. **Feature 1 RED: IntroSkipper parsing tests** - `2baff92` (test)
2. **Feature 1 GREEN: IntroSkipperResult + parseIntroSkipperResponse** - `948b088` (feat)
3. **Feature 2 RED: MediaStream mapping tests** - `1fafac8` (test)
4. **Feature 2 GREEN: 4 static mapping methods** - `6a8806e` (feat)
5. **Feature 3 RED: Settings export token exclusion tests** - `852b94d` (test)
6. **Feature 3 GREEN: buildExportJson extraction** - `f057cb4` (feat)

## Files Created/Modified
- `app/src/test/java/com/jellyfintozidoo/IntroSkipperApiTest.java` - 8 tests for IntroSkipper segment parsing (139 lines)
- `app/src/test/java/com/jellyfintozidoo/MediaStreamParsingTest.java` - 13 tests for stream index mapping and URL params (186 lines)
- `app/src/test/java/com/jellyfintozidoo/SettingsExportTest.java` - 4 tests for export token exclusion (74 lines)
- `app/src/main/java/com/jellyfintozidoo/JellyfinApi.java` - Added IntroSkipperResult, parseIntroSkipperResponse, and 4 stream mapping methods
- `app/src/main/java/com/jellyfintozidoo/SettingsActivity.java` - Extracted buildExportJson static method from exportSettings()

## Decisions Made
- Used simple string parsing for parseUrlParam instead of android.net.Uri to keep unit tests free of Android dependencies
- findDefaultStreamIndex checks both IsDefault and IsForced in one pass (subtitles may only have IsForced=true)
- Extracted buildExportJson as a static method to make token exclusion unit-testable without Android Context

## Deviations from Plan

None - plan executed exactly as written.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- All pure parsing/mapping logic is tested and ready for integration in Plan 02 (poller intro skip, track setting)
- buildExportJson extraction prepares SettingsActivity for Plan 03's full export/import fixes
- No blockers

---
*Phase: 05-advanced-playback*
*Completed: 2026-03-14*
